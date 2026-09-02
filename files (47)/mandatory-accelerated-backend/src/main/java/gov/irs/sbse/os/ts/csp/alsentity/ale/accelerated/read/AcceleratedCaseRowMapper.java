package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.read;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps the shared column projection onto {@link AcceleratedCaseRow}.
 *
 * <p>Whether the aligned-RO column is present is a property of the query, so it is a constructor
 * argument. The previous revision walked {@code ResultSetMetaData} looking for the column on every
 * row, which is a per-row loop over the column list to answer a question that was already known
 * before the query ran.
 */
public class AcceleratedCaseRowMapper implements RowMapper<AcceleratedCaseRow> {

    private final boolean includesAlignedRo;

    public AcceleratedCaseRowMapper(boolean includesAlignedRo) {
        this.includesAlignedRo = includesAlignedRo;
    }

    @Override
    public AcceleratedCaseRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object rawScore = rs.getObject("model_score");
        Integer modelScore = rawScore == null ? null : ((Number) rawScore).intValue();
        var assignedDate = rs.getDate("date_assigned_queue_file");

        return new AcceleratedCaseRow(
                rs.getString("priority_alpha"),
                rs.getString("case_type"),
                rs.getString("case_grade"),
                rs.getString("hinf_941"),
                rs.getBigDecimal("case_balance"),
                rs.getString("taxpayer_name"),
                rs.getString("city"),
                rs.getString("state_code"),
                rs.getString("zip_code"),
                rs.getString("tin"),
                rs.getString("tin_file_source"),
                rs.getString("potential_assignment_no"),
                rs.getString("current_assignment_no"),
                rs.getString("queue_pick_flag"),
                assignedDate == null ? null : assignedDate.toLocalDate(),
                statusOf(rs.getString("selection_status")),
                includesAlignedRo ? rs.getString("aligned_ro") : null,
                modelScore,
                rs.getLong("row_version"));
    }

    /**
     * An unrecognised status is a data problem, not a reason to fail the whole page. It maps to
     * QUEUED — the conservative direction, since QUEUED is the only status that renders a row as
     * selectable and therefore the only one that gets re-checked server-side before anything
     * happens to the case.
     */
    private static SelectionStatus statusOf(String raw) {
        if (raw == null) {
            return SelectionStatus.QUEUED;
        }
        try {
            return SelectionStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SelectionStatus.QUEUED;
        }
    }
}
