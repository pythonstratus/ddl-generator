package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.audit;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.Actor;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail.
 *
 * <p>Every method runs {@code REQUIRES_NEW}. A blocked attempt has to be recorded even though the
 * caller's transaction is about to roll back with an exception — if the audit write joined that
 * transaction, every refusal would roll back its own evidence, and refusals are the interesting
 * audit question for a control function.
 *
 * <p>Do not cut this story. Federal tax system; the control function is what gets examined, and
 * the first question will be who was refused and what they tried next.
 */
@Service
public class CaseAssignmentAuditService {

    private final AuditEventRepository events;

    public CaseAssignmentAuditService(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAssignment(
            Actor actor,
            RoAssignmentNumber ro,
            CaseKey caseKey,
            SelectionMethod method,
            AuditOutcome outcome) {
        write(AuditEventType.ACCELERATED_ASSIGNMENT, actor, ro, caseKey, method, outcome, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBlockedAttempt(
            Actor actor, RoAssignmentNumber ro, SelectionMethod method, AuditOutcome outcome) {
        write(
                AuditEventType.BLOCKED_SELECTION_ATTEMPT,
                actor,
                ro,
                null,
                method,
                outcome,
                outcome == AuditOutcome.BLOCKED_OUT_OF_GROUP
                        ? "Target was outside the actor's effective group"
                        : "Refused while accelerated inventory remained");
    }

    /**
     * Query and Assign by TIN usage during an active restriction, and nothing else.
     *
     * <p>These are permitted, so this is not a violation record — it is the evidence that answers
     * "was a workaround used, by whom, and when" in three months. It stays useful only while it
     * contains just the two sanctioned workarounds; the caller filters on
     * {@link SelectionMethod#isSanctionedWorkaround()} before reaching here.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExceptionPathUsage(
            Actor actor, RoAssignmentNumber ro, SelectionMethod method) {
        write(
                AuditEventType.EXCEPTION_PATH_USED,
                actor,
                ro,
                null,
                method,
                AuditOutcome.SUCCESS,
                "Sanctioned workaround used while restriction was active");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUnpick(
            Actor actor, RoAssignmentNumber ro, CaseKey caseKey, AuditOutcome outcome) {
        write(AuditEventType.UNPICK_ATTEMPT, actor, ro, caseKey, null, outcome, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEmergencyUnselect(
            Actor actor,
            RoAssignmentNumber ro,
            CaseKey caseKey,
            String justification,
            AuditOutcome outcome) {
        write(
                AuditEventType.EMERGENCY_UNSELECT,
                actor,
                ro,
                caseKey,
                null,
                outcome,
                "Justification: " + justification);
    }

    private void write(
            AuditEventType type,
            Actor actor,
            RoAssignmentNumber ro,
            CaseKey caseKey,
            SelectionMethod method,
            AuditOutcome outcome,
            String detail) {

        events.append(
                AuditEvent.of(
                        type,
                        actor.userId(),
                        actor.auditIdentity(),
                        actor.effectiveGroupId(),
                        ro == null ? null : ro.toString(),
                        caseKey == null ? null : caseKey.tin(),
                        caseKey == null ? null : caseKey.tinFileSource(),
                        method,
                        outcome,
                        detail));
    }
}
