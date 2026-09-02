package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility;

/**
 * The single authoritative expression of Mandatory Accelerated eligibility.
 *
 * <p><b>This class is the reason the epic holds together.</b> Every list, every count, every
 * status check and the in-transaction re-check all compile against the same fragments. If the
 * rules change, they change here once. Copying a WHERE clause out of here into a repository
 * method is the failure mode the design exists to prevent — counts drift from lists, silently.
 *
 * <h2>Why fragments rather than one string</h2>
 *
 * The previous revision held one finished predicate for the RO case and the group query
 * re-stated it by hand. The hand-written copy had drifted: it omitted the grade-criteria clause
 * entirely, so the group screen would list cases that the RO screens exclude, and reconciliation
 * check 2 (union of RO lists equals the group list) would fail against real data. Composing both
 * predicates from the same fragments makes that class of drift structurally impossible rather
 * than a thing code review has to catch.
 *
 * <p><b>Dialect.</b> Written for Oracle, matching the rest of ENTITY. {@code SUBSTR} with a
 * negative offset and {@code OFFSET .. FETCH NEXT} are 12c+. Postgres needs {@code RIGHT(x, 1)}
 * and {@code LIMIT/OFFSET}.
 *
 * <p><b>Table and column names are placeholders.</b> See the mapping table in the README and
 * rename before first compile.
 */
public final class EligibilitySql {

    private EligibilitySql() {}

    // ---------------------------------------------------------------------------------------
    // Fragments. Each one is a single rule. Nothing outside this class assembles a predicate.
    // ---------------------------------------------------------------------------------------

    /**
     * Case-level clauses. True or false for the case on its own, with no reference to any RO.
     *
     * <p>All five accelerated alpha values are in scope, not just 99a. The status list is what
     * makes {@code listed} and {@code groupSummaryPriority99} equal today; open question 4
     * (K-Skipped) and the GM Hold File question both land here.
     */
    public static final String CASE_CLAUSES = """
            ci.priority_alpha IN ('99a', '99b', '99c', '99d', '99e')
            AND ci.program_type = :programType
            AND ci.selection_status IN ('QUEUED', 'SELECTED', 'PENDING')
            """;

    /**
     * Defers to the pre-existing Case Assignment eligibility rules rather than duplicating them.
     * If that logic lives in a service rather than a view in your codebase, this is the clause to
     * replace, and it is the only one.
     */
    public static final String EXISTING_RULES = """
            AND EXISTS (
                SELECT 1
                  FROM v_case_assignment_eligible vcae
                 WHERE vcae.tin = ci.tin
                   AND vcae.tin_file_source = ci.tin_file_source
            )
            """;

    /**
     * Alignment and grade for one named RO. Binds {@code :roAssignmentNumber}.
     *
     * <p>ZIP alignment decides <i>whose count the case falls into</i>. It does not decide who the
     * case may be assigned to — see {@code AcceleratedAssignmentService}. Keeping those two
     * concerns in separate code is rule 6.
     *
     * <p>Grade comparison is implemented as at-or-below-and-at-or-above, i.e. a band. Open
     * question 4 in the backlog: exact match, at-or-below, or lookup table. Not stated in any
     * source. Confirm before UAT.
     */
    public static final String RO_ALIGNMENT_AND_GRADE = """
            AND EXISTS (
                SELECT 1
                  FROM ro_zip_alignment rza
                 WHERE rza.ro_assignment_number = :roAssignmentNumber
                   AND rza.zip_code = ci.zip_code
                   AND rza.active_flag = 'Y'
            )
            AND EXISTS (
                SELECT 1
                  FROM ro_grade_criteria rgc
                 WHERE rgc.ro_assignment_number = :roAssignmentNumber
                   AND ci.case_grade BETWEEN rgc.min_case_grade AND rgc.max_case_grade
            )
            """;

    /**
     * Alignment and grade for <i>any</i> RO in a group. Binds {@code :groupId}.
     *
     * <p>The ZIP join and the grade join are inside one EXISTS on purpose, so both must be
     * satisfied by the <b>same</b> RO. Splitting them into two EXISTS clauses would admit a case
     * that one RO can take by ZIP and a different RO can take by grade, which no RO can actually
     * take — and the group list would then be a superset of the union of the RO lists.
     */
    public static final String GROUP_ALIGNMENT_AND_GRADE = """
            AND EXISTS (
                SELECT 1
                  FROM ro_zip_alignment rza
                  JOIN revenue_officer ro
                    ON ro.ro_assignment_number = rza.ro_assignment_number
                  JOIN ro_grade_criteria rgc
                    ON rgc.ro_assignment_number = rza.ro_assignment_number
                 WHERE ro.group_id = :groupId
                   AND ro.program_type = :programType
                   AND rza.zip_code = ci.zip_code
                   AND rza.active_flag = 'Y'
                   AND ci.case_grade BETWEEN rgc.min_case_grade AND rgc.max_case_grade
            )
            """;

    // ---------------------------------------------------------------------------------------
    // Compositions. These are what callers use.
    // ---------------------------------------------------------------------------------------

    /** Eligibility for one Revenue Officer. Binds {@code :roAssignmentNumber, :programType}. */
    public static final String ELIGIBILITY_PREDICATE =
            CASE_CLAUSES + RO_ALIGNMENT_AND_GRADE + EXISTING_RULES;

    /**
     * Eligibility anywhere in a group. Binds {@code :groupId, :programType}.
     *
     * <p>By construction this is exactly the union of {@link #ELIGIBILITY_PREDICATE} over the
     * group's ROs, which is what makes the group list one row per case with no DISTINCT and an
     * exact header count.
     */
    public static final String GROUP_ELIGIBILITY_PREDICATE =
            CASE_CLAUSES + GROUP_ALIGNMENT_AND_GRADE + EXISTING_RULES;

    // ---------------------------------------------------------------------------------------
    // Ordering
    // ---------------------------------------------------------------------------------------

    /**
     * QIND rank, derived rather than read from a column.
     *
     * <p>The previous revision ordered on {@code ci.qind_status_rank}, a column that appears in no
     * screenshot, no migration and no mapping table. Deriving it from {@code selection_status}
     * removes an invented dependency and keeps the rank in one place next to the enum it mirrors
     * ({@code SelectionStatus#displayRank}).
     *
     * <p>Selected and Pending pin above Queued. Ordering is applied here, in the database, so it
     * survives pagination — sorting after the fetch re-sequences each page on its own, which is
     * the defect reported on 08/26/2026 and must not be reintroduced.
     */
    public static final String QIND_STATUS_RANK = """
            CASE ci.selection_status
                 WHEN 'SELECTED' THEN 0
                 WHEN 'PENDING'  THEN 1
                 WHEN 'HOLD'     THEN 2
                 WHEN 'SKIPPED'  THEN 3
                 ELSE 4
            END
            """;

    /**
     * Display ordering. Rule 5: strictly priority alpha, 99a first, on every screen.
     *
     * <p>Band is cast to a number before comparison. String comparison sorts {@code 101b} above
     * {@code 99a}, which is the bug this clause exists to avoid.
     *
     * <p><b>Known gap.</b> Within a single alpha value, rank is driven by a model score calculated
     * against balance, produced by a dedicated table in legacy. No screenshot shows that column in
     * a modern payload. If {@code ci.model_score} is absent or null across the board, three
     * consecutive 99b rows come back in arbitrary order and this is a missing-data defect, not a
     * sorting one — no amount of ORDER BY fixes it. Run the payload diagnostic in the README
     * before trusting this clause.
     *
     * <p>{@code NULLS LAST} is not decoration. Oracle sorts nulls <i>first</i> on a DESC sort, so
     * without it a null model score outranks every scored case on the screen.
     */
    public static final String DISPLAY_ORDER_BY =
            " ORDER BY "
                    + QIND_STATUS_RANK
                    + """
                     ASC,
                     CAST(SUBSTR(ci.priority_alpha, 1, LENGTH(ci.priority_alpha) - 1) AS INTEGER) DESC,
                     SUBSTR(ci.priority_alpha, -1) ASC,
                     ci.model_score DESC NULLS LAST,
                     ci.tin ASC
                    """;

    // ---------------------------------------------------------------------------------------
    // Projection
    // ---------------------------------------------------------------------------------------

    /**
     * The column projection. Matches the MTEST Auto Selection Priority 99 layout column for
     * column, so the existing Priority 99 table component can be reused on both the RO and group
     * screens.
     *
     * <p>{@code case_balance} must be a decimal type end to end. Values run past $55,000,000 and
     * floating point breaks the BE-E reconciliation the first time it is run for real.
     */
    public static final String CASE_COLUMNS = """
            ci.priority_alpha            AS priority_alpha,
            ci.case_type                 AS case_type,
            ci.case_grade                AS case_grade,
            ci.hinf_941_indicator        AS hinf_941,
            ci.case_balance              AS case_balance,
            ci.taxpayer_name             AS taxpayer_name,
            ci.city                      AS city,
            ci.state_code                AS state_code,
            ci.zip_code                  AS zip_code,
            ci.tin                       AS tin,
            ci.tin_file_source           AS tin_file_source,
            ci.potential_assignment_no   AS potential_assignment_no,
            ci.current_assignment_no     AS current_assignment_no,
            ci.queue_pick_flag           AS queue_pick_flag,
            ci.date_assigned_queue_file  AS date_assigned_queue_file,
            ci.selection_status          AS selection_status,
            ci.model_score               AS model_score,
            ci.row_version               AS row_version
            """;

    /**
     * The four counts in one aggregate over whichever predicate is supplied.
     *
     * <p>Deriving all four from a single query is what guarantees they reconcile. A separately
     * maintained counter drifts the first time a rule changes and the drift is invisible until a
     * manager reports it.
     */
    public static String countsOver(String predicate) {
        return """
                SELECT
                  SUM(CASE WHEN ci.selection_status = 'QUEUED'  THEN 1 ELSE 0 END) AS queued_count,
                  COUNT(*)                                                         AS listed_count,
                  SUM(CASE WHEN ci.selection_status IN ('QUEUED','SELECTED','PENDING')
                           THEN 1 ELSE 0 END)                                      AS gs_p99_count,
                  SUM(CASE WHEN ci.selection_status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count
                FROM case_inventory ci
                WHERE """
                + predicate;
    }
}
