package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.ReasonCode;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionStatus;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Spring JDBC implementation of {@link CaseSelectionRepository}, matching the data access style
 * used everywhere else in ENTITY.
 *
 * <p>Registered through {@code AcceleratedInfrastructureConfig} behind
 * {@code @ConditionalOnMissingBean}, for the same reason as the inventory repository:
 * {@code case_selection} is very likely already owned by the existing Case Assignment service, and
 * if it is, an adapter onto that service should win without anything here being deleted.
 *
 * <p><b>VERIFY before first run.</b> Column names here follow the mapping table in the README and
 * the ALTER statements in the migration. The three that were not visible in any screenshot are
 * {@code selection_id}, {@code selected_by} and {@code selected_at}; if {@code case_selection}
 * already carries equivalents under different names, rename here and in the migration together.
 */
public class JdbcCaseSelectionRepository implements CaseSelectionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCaseSelectionRepository(
            @Qualifier("secondaryNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String INSERT =
            """
            INSERT INTO case_selection (
                selection_id, tin, tin_file_source, ro_assignment_number,
                selection_status, selection_method, reason_code,
                selected_by, selected_as_group, selected_at
            ) VALUES (
                :selectionId, :tin, :tinFileSource, :roAssignmentNumber,
                :selectionStatus, :selectionMethod, :reasonCode,
                :selectedBy, :selectedAsGroup, :selectedAt
            )
            """;

    private static final String UPDATE_STATUS =
            """
            UPDATE case_selection
               SET selection_status = :selectionStatus
             WHERE selection_id = :selectionId
            """;

    private static final String SELECT_BY_KEY =
            """
            SELECT selection_id, tin, tin_file_source, ro_assignment_number,
                   selection_status, selection_method, reason_code,
                   selected_by, selected_as_group, selected_at
              FROM case_selection
             WHERE tin = :tin
               AND tin_file_source = :tinFileSource
               AND selection_status IN ('SELECTED', 'PENDING')
             ORDER BY selected_at DESC
            """;

    @Override
    public void save(CaseSelection selection) {
        jdbc.update(
                INSERT,
                new MapSqlParameterSource()
                        .addValue("selectionId", selection.selectionId().toString())
                        .addValue("tin", selection.tin())
                        .addValue("tinFileSource", selection.tinFileSource())
                        .addValue("roAssignmentNumber", selection.roAssignmentNumber())
                        .addValue("selectionStatus", selection.status().name())
                        .addValue("selectionMethod", selection.selectionMethod().name())
                        .addValue("reasonCode", selection.reasonCode().name())
                        .addValue("selectedBy", selection.selectedBy())
                        .addValue("selectedAsGroup", selection.selectedAsGroup())
                        .addValue("selectedAt", Timestamp.from(selection.selectedAt())));
    }

    @Override
    public void updateStatus(CaseSelection selection) {
        int rows = jdbc.update(
                UPDATE_STATUS,
                new MapSqlParameterSource()
                        .addValue("selectionId", selection.selectionId().toString())
                        .addValue("selectionStatus", selection.status().name()));
        if (rows != 1) {
            throw new IllegalStateException(
                    "Expected to update exactly one selection, updated " + rows);
        }
    }

    /**
     * Most recent open selection for the case.
     *
     * <p>Scoped to SELECTED and PENDING deliberately. A case can have been selected, unpicked and
     * selected again, and the unpick rule has to apply to the selection that is currently live —
     * matching on the case alone would find whichever history row the database felt like
     * returning.
     */
    @Override
    public Optional<CaseSelection> findByCaseKey(CaseKey caseKey) {
        List<CaseSelection> found = jdbc.query(
                SELECT_BY_KEY,
                new MapSqlParameterSource()
                        .addValue("tin", caseKey.tin())
                        .addValue("tinFileSource", caseKey.tinFileSource()),
                (rs, rowNum) ->
                        CaseSelection.rehydrate(
                                UUID.fromString(rs.getString("selection_id")),
                                rs.getString("tin"),
                                rs.getString("tin_file_source"),
                                rs.getString("ro_assignment_number"),
                                SelectionMethod.valueOf(rs.getString("selection_method")),
                                ReasonCode.valueOf(rs.getString("reason_code")),
                                rs.getString("selected_by"),
                                rs.getString("selected_as_group"),
                                rs.getTimestamp("selected_at").toInstant(),
                                SelectionStatus.valueOf(rs.getString("selection_status"))));

        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }
}
