package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.audit.AuditOutcome;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.audit.CaseAssignmentAuditService;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility.MandatoryAcceleratedEligibilityService;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility.RevenueOfficerLookup;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.AssignmentContext;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.CaseNoLongerAvailableException;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.CrossGroupCaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The accelerated write path.
 *
 * <p>Everything happens in one transaction: verify scope, lock the row, verify eligibility, verify
 * the case is still queue-available, create the selection, stamp method and reason, recalculate
 * the counts and drop the cached status. Recalculation outside the transaction shows stale numbers
 * immediately after the action that changed them, which a manager reads as the counter being
 * broken rather than as a caching artefact.
 */
@Service
public class AcceleratedAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AcceleratedAssignmentService.class);

    private final CaseSelectionRepository selections;
    private final CaseInventoryWriteRepository inventory;
    private final MandatoryAcceleratedEligibilityService eligibility;
    private final RevenueOfficerLookup officers;
    private final AssignmentContext context;
    private final CaseAssignmentAuditService audit;

    public AcceleratedAssignmentService(
            CaseSelectionRepository selections,
            CaseInventoryWriteRepository inventory,
            MandatoryAcceleratedEligibilityService eligibility,
            RevenueOfficerLookup officers,
            AssignmentContext context,
            CaseAssignmentAuditService audit) {
        this.selections = selections;
        this.inventory = inventory;
        this.eligibility = eligibility;
        this.officers = officers;
        this.context = context;
        this.audit = audit;
    }

    @Transactional
    public AssignmentResult assign(AcceleratedAssignmentCommand command) {

        var actor = context.current();
        var target = command.targetRo();
        var caseKey = command.caseKey();
        String effectiveGroup = actor.effectiveGroupId();

        // Rule 6. Any RO in the manager's group is a valid target, including one holding zero
        // accelerated inventory of its own. ZIP alignment decides whose count a case falls into;
        // it does not decide who the case may go to. The stated purpose of the group screen is
        // rebalancing — one RO holding 55 accelerated cases against another holding 5 — so
        // validating the target against alignment would break the feature's main use.
        //
        // The only check on the target is group membership, and it reads the *effective* group so
        // a National Analyst operating as "Viewing as: Group NNNNNN" is bound by that group's
        // roster rather than their own.
        if (!officers.existsInGroup(target, effectiveGroup)) {
            throw new IllegalArgumentException(
                    "%s is not a Revenue Officer in group %s".formatted(target, effectiveGroup));
        }

        // Lock the case row first. Two managers selecting the same case is not hypothetical in a
        // group working a shared 45-case list, and without the lock both pass the checks below.
        var locked = inventory
                .lockForAssignment(caseKey, command.expectedRowVersion())
                .orElseThrow(() -> {
                    audit.recordAssignment(
                            actor, target, caseKey, SelectionMethod.MANDATORY_ACCELERATED,
                            AuditOutcome.REJECTED_STALE);
                    return new CaseNoLongerAvailableException(caseKey);
                });

        // Whose accelerated set is this case in? Resolved after the lock, so it cannot change
        // underneath the checks that follow.
        var alignmentRo = alignmentRoFor(caseKey, target);

        // Rule 6 opens the *target* right up. It says nothing about the case, and the previous
        // revision checked only the target's group membership. A manager who knew a TIN could
        // therefore pull a case belonging to another group's queue into their own, through the
        // ordinary endpoint, and the case would simply vanish from the other group's list. The
        // case has to be inside the effective group as well.
        if (!officers.existsInGroup(alignmentRo, effectiveGroup)) {
            audit.recordAssignment(
                    actor, target, caseKey, SelectionMethod.MANDATORY_ACCELERATED,
                    AuditOutcome.BLOCKED_OUT_OF_GROUP);
            throw new CrossGroupCaseException(caseKey, effectiveGroup);
        }

        // Authoritative re-check inside the transaction. The aspect's pre-check was advisory and
        // both managers racing for the last case will have passed it.
        //
        // Evaluated against the alignment RO, not the target. Checking the target would reject
        // every rebalancing assignment, because the case is not in the target's set — that is what
        // makes it a rebalancing assignment.
        var decision = eligibility.evaluateForUpdate(caseKey, alignmentRo);
        if (!decision.eligible()) {
            audit.recordAssignment(
                    actor, target, caseKey, SelectionMethod.MANDATORY_ACCELERATED,
                    AuditOutcome.REJECTED_INELIGIBLE);
            throw new CaseNoLongerAvailableException(caseKey, decision.summary());
        }
        if (!locked.availableForSelection()) {
            audit.recordAssignment(
                    actor, target, caseKey, SelectionMethod.MANDATORY_ACCELERATED,
                    AuditOutcome.REJECTED_STALE);
            throw new CaseNoLongerAvailableException(caseKey);
        }

        var selection = new CaseSelection(
                caseKey.tin(),
                caseKey.tinFileSource(),
                target.toString(),
                SelectionMethod.MANDATORY_ACCELERATED,
                command.reasonCode(),
                actor.userId(),
                effectiveGroup);

        selections.save(selection);
        inventory.markSelected(caseKey, target, command.expectedRowVersion());

        // Counts are derived, not maintained, so there is no counter to decrement here. Dropping
        // the cached status is what makes the next read correct — and both ROs are dropped,
        // because on a rebalancing assignment the target's list gains nothing while the alignment
        // RO's queued count falls.
        invalidate(alignmentRo);
        invalidate(target);

        // Read uncached. Reading through the cache inside the write transaction would either
        // return the pre-write value or populate the cache from a transaction that has not
        // committed yet.
        var counts = eligibility.countsUncached(alignmentRo);

        audit.recordAssignment(
                actor, target, caseKey, SelectionMethod.MANDATORY_ACCELERATED, AuditOutcome.SUCCESS);

        log.info(
                "Accelerated assignment: {} -> {} by {}; {} queued now {}",
                caseKey,
                target,
                actor.auditIdentity(),
                alignmentRo,
                counts.queued());

        // Rule 7: once the last eligible case is taken the restriction lifts immediately, with no
        // reload and no re-login. Returning the flag lets the client re-enable its controls on the
        // same response that completed the assignment.
        return new AssignmentResult(
                selection.selectionId(),
                caseKey.tin(),
                target.toString(),
                alignmentRo.toString(),
                command.reasonCode().displayText(),
                counts,
                !counts.restrictionActive());
    }

    /**
     * Which RO's accelerated set this case belongs to.
     *
     * <p>Falls back to the target when alignment cannot be resolved, which covers the case with no
     * active ZIP alignment row at all. That case cannot satisfy the eligibility predicate either,
     * so the re-check below rejects it with a stated reason rather than this method guessing.
     */
    private RoAssignmentNumber alignmentRoFor(CaseKey caseKey, RoAssignmentNumber target) {
        return inventory.alignedRevenueOfficer(caseKey).orElse(target);
    }

    /**
     * Manager-initiated unpick. Delegates to the selection's own state machine, then persists the
     * transition.
     *
     * <p>The explicit {@code updateStatus} is not ceremony. Under JPA this worked by dirty
     * checking, which is invisible at the call site and stops working the moment someone moves
     * the call outside a transaction — a silent no-op on a control function.
     */
    @Transactional
    public void unpick(CaseKey caseKey) {
        var selection = selections
                .findByCaseKey(caseKey)
                .orElseThrow(() -> new CaseNoLongerAvailableException(caseKey));

        var actor = context.current();
        var ro = RoAssignmentNumber.parse(selection.roAssignmentNumber());
        try {
            selection.unpick();
            selections.updateStatus(selection);
            inventory.returnToQueue(caseKey);
            invalidate(ro);
            audit.recordUnpick(actor, ro, caseKey, AuditOutcome.SUCCESS);
        } catch (RuntimeException ex) {
            audit.recordUnpick(actor, ro, caseKey, AuditOutcome.BLOCKED);
            throw ex;
        }
    }

    /**
     * Drops the cached status now, and again once the transaction commits.
     *
     * <p>The immediate call is what makes the count on this response correct. The after-commit
     * call covers a read that arrives between the two, which would otherwise repopulate the cache
     * from a transaction that had not committed. A stale "restriction cleared" is a control
     * bypass, so this is worth the four lines.
     */
    private void invalidate(RoAssignmentNumber ro) {
        eligibility.invalidate(ro);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            eligibility.invalidate(ro);
                        }
                    });
        }
    }
}
