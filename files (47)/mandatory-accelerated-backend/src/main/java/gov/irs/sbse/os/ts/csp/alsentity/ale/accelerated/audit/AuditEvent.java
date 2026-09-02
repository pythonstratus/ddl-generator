package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.audit;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;
import java.time.Instant;
import java.util.UUID;

/**
 * One audit record. Append-only: no updates, no deletes, no admin-toggleable soft delete. A
 * mutable table is not evidence.
 *
 * <p>The impersonation chain is recorded rather than just the actor, because "S. Vainer, National
 * Analyst, acting as Group 271039" is the meaningful record and "S. Vainer" is not.
 *
 * <p>A record rather than a JPA entity, for the same reason as {@code CaseSelection}. It also
 * makes the append-only property visible in the type: there are no setters to call, so no future
 * code path can update a written event without adding the ability first.
 */
public record AuditEvent(
        UUID eventId,
        AuditEventType eventType,
        String actorUserId,
        String actorIdentity,
        String actingAsGroup,
        String roAssignmentNumber,
        String tin,
        String tinFileSource,
        SelectionMethod selectionMethod,
        AuditOutcome outcome,
        String detail,
        Instant occurredAt) {

    public static AuditEvent of(
            AuditEventType eventType,
            String actorUserId,
            String actorIdentity,
            String actingAsGroup,
            String roAssignmentNumber,
            String tin,
            String tinFileSource,
            SelectionMethod selectionMethod,
            AuditOutcome outcome,
            String detail) {
        return new AuditEvent(
                UUID.randomUUID(),
                eventType,
                actorUserId,
                actorIdentity,
                actingAsGroup,
                roAssignmentNumber,
                tin,
                tinFileSource,
                selectionMethod,
                outcome,
                detail,
                Instant.now());
    }
}
