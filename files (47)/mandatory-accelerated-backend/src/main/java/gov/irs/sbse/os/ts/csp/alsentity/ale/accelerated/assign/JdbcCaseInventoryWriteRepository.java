package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Reference implementation of {@link CaseInventoryWriteRepository}.
 *
 * <p><b>Prefer an adapter onto the existing Case Assignment service.</b> If ENTITY already owns
 * the selection lifecycle — status transitions, assignment number rewriting, whatever downstream
 * work delivery triggers — implement the interface as a thin call onto it and this class never
 * loads. It is registered in {@code AcceleratedInfrastructureConfig} behind
 * {@code @ConditionalOnMissingBean} precisely so that yours wins without anything being deleted.
 *
 * <p>What it does implement, and what any replacement must also implement:
 *
 * <ul>
 *   <li>a genuine row lock, taken before any eligibility decision
 *   <li>an optimistic version check in the same statement as the lock, so a stale click loses
 *   <li>an assignment number rewrite from the queue to the RO, since queue membership is derived
 *       from the assignment number and not from a separate flag
 * </ul>
 */
public class JdbcCaseInventoryWriteRepository implements CaseInventoryWriteRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCaseInventoryWriteRepository(
            @Qualifier("secondaryNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lock and version check in one statement.
     *
     * <p>{@code FOR UPDATE NOWAIT} rather than a plain wait. Two managers on the same case is the
     * expected case, not the exceptional one, and the loser should get a specific "someone else
     * took it" within milliseconds rather than sitting on a blocked connection until the winner's
     * transaction ends.
     */
    @Override
    public Optional<LockedCase> lockForAssignment(CaseKey caseKey, long expectedRowVersion) {
        List<LockedCase> rows = jdbc.query(
                """
                SELECT ci.tin, ci.tin_file_source, ci.selection_status, ci.row_version
                  FROM case_inventory ci
                 WHERE ci.tin = :tin
                   AND ci.tin_file_source = :tinFileSource
                   AND ci.row_version = :expectedRowVersion
                   FOR UPDATE NOWAIT
                """,
                new MapSqlParameterSource()
                        .addValue("tin", caseKey.tin())
                        .addValue("tinFileSource", caseKey.tinFileSource())
                        .addValue("expectedRowVersion", expectedRowVersion),
                (rs, rowNum) ->
                        new LockedCase(
                                new CaseKey(rs.getString("tin"), rs.getString("tin_file_source")),
                                rs.getString("selection_status"),
                                rs.getLong("row_version")));

        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Moves the case to Selected and rewrites its current assignment number to the target RO.
     *
     * <p>The assignment number rewrite is what takes the case out of the queued count — queue
     * membership is derived from the assignment number, whose RO segment is 7000 for the queue.
     * Updating the status without the number would leave the case counted as queued forever.
     *
     * <p>The version predicate is repeated here even though the row is already locked. Belt and
     * braces on the one write in the module that must not be applied twice.
     */
    @Override
    public void markSelected(
            CaseKey caseKey, RoAssignmentNumber target, long expectedRowVersion) {
        int rows = jdbc.update(
                """
                UPDATE case_inventory
                   SET selection_status      = 'SELECTED',
                       current_assignment_no = :targetAssignmentNumber,
                       row_version           = row_version + 1
                 WHERE tin = :tin
                   AND tin_file_source = :tinFileSource
                   AND row_version = :expectedRowVersion
                   AND selection_status = 'QUEUED'
                """,
                new MapSqlParameterSource()
                        .addValue("tin", caseKey.tin())
                        .addValue("tinFileSource", caseKey.tinFileSource())
                        .addValue("targetAssignmentNumber", target.toString())
                        .addValue("expectedRowVersion", expectedRowVersion));

        if (rows != 1) {
            throw new IllegalStateException(
                    "Expected to update exactly one case row for " + caseKey + ", updated " + rows);
        }
    }

    /**
     * Returns the case to the queue.
     *
     * <p>The queue assignment number is rebuilt from the group segment of whatever the case
     * currently carries, so the case goes back to the queue it came from rather than to a
     * hardcoded one.
     */
    @Override
    public void returnToQueue(CaseKey caseKey) {
        jdbc.update(
                """
                UPDATE case_inventory
                   SET selection_status      = 'QUEUED',
                       current_assignment_no = SUBSTR(current_assignment_no, 1, 4) || '-7000',
                       row_version           = row_version + 1
                 WHERE tin = :tin
                   AND tin_file_source = :tinFileSource
                """,
                new MapSqlParameterSource()
                        .addValue("tin", caseKey.tin())
                        .addValue("tinFileSource", caseKey.tinFileSource()));
    }

    @Override
    public Optional<RoAssignmentNumber> alignedRevenueOfficer(CaseKey caseKey) {
        List<String> aligned = jdbc.query(
                """
                SELECT MIN(rza.ro_assignment_number) AS ro_assignment_number
                  FROM case_inventory ci
                  JOIN ro_zip_alignment rza
                    ON rza.zip_code = ci.zip_code
                   AND rza.active_flag = 'Y'
                  JOIN ro_grade_criteria rgc
                    ON rgc.ro_assignment_number = rza.ro_assignment_number
                   AND ci.case_grade BETWEEN rgc.min_case_grade AND rgc.max_case_grade
                 WHERE ci.tin = :tin
                   AND ci.tin_file_source = :tinFileSource
                """,
                new MapSqlParameterSource()
                        .addValue("tin", caseKey.tin())
                        .addValue("tinFileSource", caseKey.tinFileSource()),
                (rs, rowNum) -> rs.getString("ro_assignment_number"));

        return aligned.stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .map(RoAssignmentNumber::parse);
    }
}
