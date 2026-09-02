package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.audit;

import java.sql.Timestamp;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Writes the audit trail. Insert only — the grants in the migration deliberately withhold UPDATE
 * and DELETE on {@code ma_audit_event}, so this is the only operation that would succeed anyway.
 */
@Repository
public class JdbcAuditEventRepository implements AuditEventRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAuditEventRepository(
            @Qualifier("secondaryNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String INSERT =
            """
            INSERT INTO ma_audit_event (
                event_id, event_type, actor_user_id, actor_identity, acting_as_group,
                ro_assignment_number, tin, tin_file_source, selection_method,
                outcome, detail, occurred_at
            ) VALUES (
                :eventId, :eventType, :actorUserId, :actorIdentity, :actingAsGroup,
                :roAssignmentNumber, :tin, :tinFileSource, :selectionMethod,
                :outcome, :detail, :occurredAt
            )
            """;

    @Override
    public void append(AuditEvent event) {
        jdbc.update(
                INSERT,
                new MapSqlParameterSource()
                        .addValue("eventId", event.eventId().toString())
                        .addValue("eventType", event.eventType().name())
                        .addValue("actorUserId", event.actorUserId())
                        .addValue("actorIdentity", event.actorIdentity())
                        .addValue("actingAsGroup", event.actingAsGroup())
                        .addValue("roAssignmentNumber", event.roAssignmentNumber())
                        .addValue("tin", event.tin())
                        .addValue("tinFileSource", event.tinFileSource())
                        .addValue(
                                "selectionMethod",
                                event.selectionMethod() == null
                                        ? null
                                        : event.selectionMethod().name())
                        .addValue("outcome", event.outcome().name())
                        .addValue("detail", truncate(event.detail()))
                        .addValue("occurredAt", Timestamp.from(event.occurredAt())));
    }

    /**
     * The detail column is 2000 characters. A justification long enough to overflow it should not
     * be the reason an emergency action fails to leave a record.
     */
    private static String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= 2000 ? detail : detail.substring(0, 1997) + "...";
    }
}
