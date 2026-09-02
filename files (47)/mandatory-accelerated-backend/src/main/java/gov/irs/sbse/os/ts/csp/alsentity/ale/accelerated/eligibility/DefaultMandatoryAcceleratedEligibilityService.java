package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.AcceleratedCounts;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.ProgramType;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation. All SQL is composed from {@link EligibilitySql} fragments; none of it is
 * written here.
 *
 * <p>Uses {@code secondaryNamedJdbcTemplate}, matching every other read path in ENTITY. If the
 * accelerated tables live on the primary datasource instead, change the qualifier here and in
 * {@code AcceleratedCaseRepository}, {@code JdbcCaseSelectionRepository},
 * {@code JdbcCaseInventoryWriteRepository} and {@code JdbcAuditEventRepository} — those five are
 * the only places a template is injected.
 */
@Service
public class DefaultMandatoryAcceleratedEligibilityService
        implements MandatoryAcceleratedEligibilityService {

    private final NamedParameterJdbcTemplate jdbc;
    private final RevenueOfficerLookup revenueOfficers;
    private final RestrictionStatusCache cache;

    public DefaultMandatoryAcceleratedEligibilityService(
            @Qualifier("secondaryNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            RevenueOfficerLookup revenueOfficers,
            RestrictionStatusCache cache) {
        this.jdbc = jdbc;
        this.revenueOfficers = revenueOfficers;
        this.cache = cache;
    }

    private static final String RO_COUNTS_SQL =
            EligibilitySql.countsOver(EligibilitySql.ELIGIBILITY_PREDICATE);

    private static final String GROUP_COUNTS_SQL =
            EligibilitySql.countsOver(EligibilitySql.GROUP_ELIGIBILITY_PREDICATE);

    private static final String SINGLE_CASE_SQL =
            """
            SELECT ci.tin
              FROM case_inventory ci
             WHERE ci.tin = :tin
               AND ci.tin_file_source = :tinFileSource
               AND """
                    + EligibilitySql.ELIGIBILITY_PREDICATE;

    /**
     * Every RO in the group with its four counts, in one query.
     *
     * <p>Replaces a loop that called the single-RO aggregate once per employee. Roughly eight
     * employees per group is eight round trips per Group Summary render, on the screen a manager
     * opens first every single time.
     *
     * <p>The DISTINCT in the CTE is load-bearing. An RO with three ZIPs and two grade-criteria
     * rows fans a single case out to six join rows, and without DISTINCT that RO's Priority 99
     * column reads six. Status is functionally dependent on the case, so including it in the
     * DISTINCT does not change the row count.
     *
     * <p>The outer LEFT JOIN is rule 14: every employee appears, including one holding no
     * accelerated inventory, who gets zeroes rather than being dropped from the table.
     */
    private static final String GROUP_SUMMARY_SQL =
            """
            WITH ro_cases AS (
                SELECT DISTINCT
                       ro.ro_assignment_number AS ro_assignment_number,
                       ci.tin                  AS tin,
                       ci.tin_file_source      AS tin_file_source,
                       ci.selection_status     AS selection_status
                  FROM revenue_officer ro
                  JOIN ro_zip_alignment rza
                    ON rza.ro_assignment_number = ro.ro_assignment_number
                   AND rza.active_flag = 'Y'
                  JOIN ro_grade_criteria rgc
                    ON rgc.ro_assignment_number = ro.ro_assignment_number
                  JOIN case_inventory ci
                    ON ci.zip_code = rza.zip_code
                   AND ci.case_grade BETWEEN rgc.min_case_grade AND rgc.max_case_grade
                   AND """
                    + EligibilitySql.CASE_CLAUSES
                    + EligibilitySql.EXISTING_RULES
                    + """
                 WHERE ro.group_id = :groupId
                   AND ro.program_type = :programType
            )
            SELECT ro.ro_assignment_number AS ro_assignment_number,
                   COALESCE(SUM(CASE WHEN rc.selection_status = 'QUEUED'
                                     THEN 1 ELSE 0 END), 0)                 AS queued_count,
                   COUNT(rc.tin)                                            AS listed_count,
                   COALESCE(SUM(CASE WHEN rc.selection_status IN ('QUEUED','SELECTED','PENDING')
                                     THEN 1 ELSE 0 END), 0)                 AS gs_p99_count,
                   COALESCE(SUM(CASE WHEN rc.selection_status = 'PENDING'
                                     THEN 1 ELSE 0 END), 0)                 AS pending_count
              FROM revenue_officer ro
              LEFT JOIN ro_cases rc
                ON rc.ro_assignment_number = ro.ro_assignment_number
             WHERE ro.group_id = :groupId
               AND ro.program_type = :programType
               AND ro.ro_assignment_number NOT LIKE '%-7000'
             GROUP BY ro.ro_assignment_number
             ORDER BY ro.ro_assignment_number
            """;

    @Override
    public boolean restrictionActive(RoAssignmentNumber ro) {
        return counts(ro).restrictionActive();
    }

    @Override
    @Transactional(readOnly = true)
    public AcceleratedCounts counts(RoAssignmentNumber ro) {
        return cache.get(ro, () -> countsUncached(ro));
    }

    @Override
    @Transactional(readOnly = true)
    public AcceleratedCounts countsUncached(RoAssignmentNumber ro) {
        // Rule 1: International is excluded entirely. Short-circuit before touching the database
        // so no International path can produce a restriction through a data anomaly.
        if (!revenueOfficers.programTypeOf(ro).isSubjectToMandatoryAccelerated()) {
            return AcceleratedCounts.NONE;
        }
        var params = new MapSqlParameterSource()
                .addValue("roAssignmentNumber", ro.toString())
                .addValue("programType", ProgramType.GENERAL.name());

        return jdbc.queryForObject(RO_COUNTS_SQL, params, (rs, rowNum) -> countsFrom(rs));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<RoAssignmentNumber, AcceleratedCounts> countsForGroup(String groupId) {
        var params = new MapSqlParameterSource()
                .addValue("groupId", groupId)
                .addValue("programType", ProgramType.GENERAL.name());

        // RowMapper rather than RowCallbackHandler. Both overloads accept a lambda and which one
        // binds depends on whether the body happens to be void-compatible, which is a fragile
        // thing for a query on the group's first screen to depend on.
        List<Map.Entry<RoAssignmentNumber, AcceleratedCounts>> rows = jdbc.query(
                GROUP_SUMMARY_SQL,
                params,
                (rs, rowNum) ->
                        Map.entry(
                                RoAssignmentNumber.parse(rs.getString("ro_assignment_number")),
                                countsFrom(rs)));

        Map<RoAssignmentNumber, AcceleratedCounts> byRo = new LinkedHashMap<>();
        rows.forEach(entry -> byRo.put(entry.getKey(), entry.getValue()));
        return byRo;
    }

    @Override
    @Transactional(readOnly = true)
    public AcceleratedCounts groupWideCounts(String groupId) {
        // Not the sum of the per-RO counts. ZIP alignment is not exclusive, so a case aligned to
        // two ROs is counted by both of them and summing gives a header larger than the list it
        // sits above. This aggregate runs over the group predicate, which is one row per case.
        var params = new MapSqlParameterSource()
                .addValue("groupId", groupId)
                .addValue("programType", ProgramType.GENERAL.name());

        return jdbc.queryForObject(GROUP_COUNTS_SQL, params, (rs, rowNum) -> countsFrom(rs));
    }

    @Override
    @Transactional(readOnly = true)
    public EligibilityDecision evaluate(CaseKey caseKey, RoAssignmentNumber ro) {
        return evaluateInternal(caseKey, ro);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EligibilityDecision evaluateForUpdate(CaseKey caseKey, RoAssignmentNumber ro) {
        // MANDATORY propagation is deliberate. This is only correct inside the caller's write
        // transaction; invoked standalone it is a bug, and failing loudly beats returning a
        // decision that was true a millisecond ago.
        //
        // There is deliberately no FOR UPDATE here. The caller has already taken the row lock
        // through CaseInventoryWriteRepository#lockForAssignment, so this read is already
        // consistent, and a second lock acquired in a different statement order across two code
        // paths is a deadlock waiting for the first busy morning.
        return evaluateInternal(caseKey, ro);
    }

    private EligibilityDecision evaluateInternal(CaseKey caseKey, RoAssignmentNumber ro) {
        if (!revenueOfficers.programTypeOf(ro).isSubjectToMandatoryAccelerated()) {
            return EligibilityDecision.ineligible(
                    "RO is International; Mandatory Accelerated does not apply");
        }

        var params = new MapSqlParameterSource()
                .addValue("tin", caseKey.tin())
                .addValue("tinFileSource", caseKey.tinFileSource())
                .addValue("roAssignmentNumber", ro.toString())
                .addValue("programType", ProgramType.GENERAL.name());

        var matches = jdbc.queryForList(SINGLE_CASE_SQL, params);
        return matches.isEmpty()
                ? new EligibilityDecision(false, diagnose(caseKey, ro))
                : EligibilityDecision.eligible();
    }

    /**
     * Runs the clauses separately to say which one rejected the case. Only reached on the failure
     * path, so it costs nothing in the common case, and it turns "why isn't this case on my list"
     * from an investigation into an answer.
     */
    private List<String> diagnose(CaseKey caseKey, RoAssignmentNumber ro) {
        var params = new MapSqlParameterSource()
                .addValue("tin", caseKey.tin())
                .addValue("tinFileSource", caseKey.tinFileSource())
                .addValue("roAssignmentNumber", ro.toString());

        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT ci.priority_alpha   AS priority_alpha,
                       ci.program_type     AS program_type,
                       ci.selection_status AS selection_status,
                       ci.zip_code         AS zip_code,
                       ci.case_grade       AS case_grade,
                       (SELECT COUNT(*) FROM ro_zip_alignment z
                         WHERE z.ro_assignment_number = :roAssignmentNumber
                           AND z.zip_code = ci.zip_code
                           AND z.active_flag = 'Y')                   AS zip_aligned,
                       (SELECT COUNT(*) FROM ro_grade_criteria g
                         WHERE g.ro_assignment_number = :roAssignmentNumber
                           AND ci.case_grade BETWEEN g.min_case_grade AND g.max_case_grade)
                                                                      AS grade_ok
                  FROM case_inventory ci
                 WHERE ci.tin = :tin
                   AND ci.tin_file_source = :tinFileSource
                """,
                params);

        if (rows.isEmpty()) {
            return List.of("case not found in inventory");
        }
        var row = rows.get(0);
        List<String> failures = new ArrayList<>();

        // asText, not String.valueOf. String.valueOf(null) returns the four characters "null",
        // so the previous null check here could never fire.
        String alpha = asText(row.get("priority_alpha"));
        if (alpha == null || !alpha.startsWith("99")) {
            failures.add("priority alpha " + alpha + " is not in the accelerated band");
        }
        if (!ProgramType.GENERAL.name().equals(asText(row.get("program_type")))) {
            failures.add("case is International; excluded from Mandatory Accelerated");
        }
        String status = asText(row.get("selection_status"));
        if ("DELIVERED".equals(status)) {
            failures.add("case has been delivered and has left queue inventory");
        } else if ("SKIPPED".equals(status) || "HOLD".equals(status)) {
            failures.add("case is at " + status + ", which the eligibility predicate excludes");
        }
        if (asInt(row.get("zip_aligned")) == 0) {
            failures.add("case ZIP " + row.get("zip_code") + " is not aligned to " + ro);
        }
        if (asInt(row.get("grade_ok")) == 0) {
            failures.add("case grade " + row.get("case_grade") + " is outside the RO grade criteria");
        }
        if (failures.isEmpty()) {
            failures.add("excluded by an existing Case Assignment eligibility rule");
        }
        return failures;
    }

    @Override
    public void invalidate(RoAssignmentNumber ro) {
        cache.invalidate(ro);
    }

    private static AcceleratedCounts countsFrom(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AcceleratedCounts(
                rs.getInt("queued_count"),
                rs.getInt("listed_count"),
                rs.getInt("gs_p99_count"),
                rs.getInt("pending_count"));
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString();
    }

    private static int asInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
