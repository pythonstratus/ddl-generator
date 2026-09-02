package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.read;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.ProgramType;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility.EligibilitySql;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads over the eligibility set.
 *
 * <p>Ordering is applied in SQL, never in the client. Sorting a materialised result set in a
 * browser grid is both a correctness problem — page two orders independently of page one — and a
 * performance problem, and it is the cause of the sequencing defect already logged against modern
 * on 08/26/2026.
 *
 * <p>There is no sort parameter anywhere in this class. Rule 5 makes display order
 * non-negotiable, so accepting one would let a caller request an order the business has ruled out.
 */
@Repository
public class AcceleratedCaseRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private final RowMapper<AcceleratedCaseRow> roRowMapper = new AcceleratedCaseRowMapper(false);
    private final RowMapper<AcceleratedCaseRow> groupRowMapper = new AcceleratedCaseRowMapper(true);

    public AcceleratedCaseRepository(
            @Qualifier("secondaryNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String PAGING = " OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY";

    // -------------------------------------------------------------------------------------
    // RO-scoped
    // -------------------------------------------------------------------------------------

    private static final String SELECT_FOR_RO =
            "SELECT "
                    + EligibilitySql.CASE_COLUMNS
                    + "  FROM case_inventory ci\n WHERE "
                    + EligibilitySql.ELIGIBILITY_PREDICATE;

    private static final String COUNT_FOR_RO =
            "SELECT COUNT(*) FROM case_inventory ci WHERE " + EligibilitySql.ELIGIBILITY_PREDICATE;

    public List<AcceleratedCaseRow> findForRo(RoAssignmentNumber ro, int page, int size) {
        return jdbc.query(
                SELECT_FOR_RO + EligibilitySql.DISPLAY_ORDER_BY + PAGING,
                roParams(ro).addValue("limit", size).addValue("offset", (long) page * size),
                roRowMapper);
    }

    public long countForRo(RoAssignmentNumber ro) {
        Long total = jdbc.queryForObject(COUNT_FOR_RO, roParams(ro), Long.class);
        return total == null ? 0L : total;
    }

    // -------------------------------------------------------------------------------------
    // Group-scoped — the Legacy F2 equivalent
    // -------------------------------------------------------------------------------------

    /**
     * The group list.
     *
     * <p>Two changes from the previous revision, both correctness rather than style.
     *
     * <p><b>The predicate is composed, not restated.</b> The old group query hand-wrote its WHERE
     * clause and, in doing so, dropped the grade-criteria rule. The group screen would then list
     * cases no RO in the group could actually take, and reconciliation check 2 would fail the
     * first time it ran against production-equivalent data. It now shares
     * {@code GROUP_ELIGIBILITY_PREDICATE} with everything else.
     *
     * <p><b>Alignment is a correlated scalar subquery, not a join.</b> The old version joined a
     * grouped ZIP-to-RO subquery, which relied on the grouping to avoid fanning a case out once
     * per aligned RO. A scalar subquery cannot fan out at all, so this is one row per case by
     * construction — no DISTINCT, and {@code COUNT(*)} is the number of rows the manager sees.
     *
     * <p>MIN picks the aligned RO to display when a case aligns to several. Arbitrary but stable;
     * an unstable choice makes rows appear to change owner between pages.
     */
    private static final String ALIGNED_RO_COLUMN =
            """
            , (SELECT MIN(rza2.ro_assignment_number)
                 FROM ro_zip_alignment rza2
                 JOIN revenue_officer ro2
                   ON ro2.ro_assignment_number = rza2.ro_assignment_number
                 JOIN ro_grade_criteria rgc2
                   ON rgc2.ro_assignment_number = rza2.ro_assignment_number
                WHERE ro2.group_id = :groupId
                  AND ro2.program_type = :programType
                  AND rza2.zip_code = ci.zip_code
                  AND rza2.active_flag = 'Y'
                  AND ci.case_grade BETWEEN rgc2.min_case_grade AND rgc2.max_case_grade
              ) AS aligned_ro
            """;

    private static final String SELECT_FOR_GROUP =
            "SELECT "
                    + EligibilitySql.CASE_COLUMNS
                    + ALIGNED_RO_COLUMN
                    + "  FROM case_inventory ci\n WHERE "
                    + EligibilitySql.GROUP_ELIGIBILITY_PREDICATE;

    private static final String COUNT_FOR_GROUP =
            "SELECT COUNT(*) FROM case_inventory ci WHERE "
                    + EligibilitySql.GROUP_ELIGIBILITY_PREDICATE;

    public List<AcceleratedCaseRow> findForGroup(String groupId, int page, int size) {
        return jdbc.query(
                SELECT_FOR_GROUP + EligibilitySql.DISPLAY_ORDER_BY + PAGING,
                groupParams(groupId).addValue("limit", size).addValue("offset", (long) page * size),
                groupRowMapper);
    }

    public long countForGroup(String groupId) {
        // Counts the predicate directly rather than wrapping the projection. The projection
        // carries a correlated subquery per row; running it to throw the result away is work the
        // database does not need to do to answer "how many".
        Long total = jdbc.queryForObject(COUNT_FOR_GROUP, groupParams(groupId), Long.class);
        return total == null ? 0L : total;
    }

    // -------------------------------------------------------------------------------------
    // Reconciliation support
    // -------------------------------------------------------------------------------------

    /**
     * Case keys only, for the reconciliation harness. Pulling eighteen columns to compare a set of
     * keys is the difference between a check that runs in MTEST and one that gets switched off.
     */
    public List<String> caseKeysForRo(RoAssignmentNumber ro) {
        return jdbc.query(
                "SELECT ci.tin, ci.tin_file_source FROM case_inventory ci WHERE "
                        + EligibilitySql.ELIGIBILITY_PREDICATE,
                roParams(ro),
                (rs, rowNum) -> rs.getString("tin") + "/" + rs.getString("tin_file_source"));
    }

    public List<String> caseKeysForGroup(String groupId) {
        return jdbc.query(
                "SELECT ci.tin, ci.tin_file_source FROM case_inventory ci WHERE "
                        + EligibilitySql.GROUP_ELIGIBILITY_PREDICATE,
                groupParams(groupId),
                (rs, rowNum) -> rs.getString("tin") + "/" + rs.getString("tin_file_source"));
    }

    private static MapSqlParameterSource roParams(RoAssignmentNumber ro) {
        return new MapSqlParameterSource()
                .addValue("roAssignmentNumber", ro.toString())
                .addValue("programType", ProgramType.GENERAL.name());
    }

    private static MapSqlParameterSource groupParams(String groupId) {
        return new MapSqlParameterSource()
                .addValue("groupId", groupId)
                .addValue("programType", ProgramType.GENERAL.name());
    }
}
