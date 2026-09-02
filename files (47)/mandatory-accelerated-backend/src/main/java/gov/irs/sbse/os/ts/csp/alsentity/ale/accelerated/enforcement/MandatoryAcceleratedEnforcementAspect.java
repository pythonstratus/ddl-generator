package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.audit.AuditOutcome;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.audit.CaseAssignmentAuditService;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.PriorityAlpha;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility.MandatoryAcceleratedEligibilityService;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility.RevenueOfficerLookup;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The one interception layer. Everything gated by Mandatory Accelerated passes through here.
 *
 * <p>Ordered ahead of {@code @Transactional} so the refusal happens before a transaction opens.
 * The check performed here is <b>advisory</b>: two managers can both pass it for the same last
 * case. The authoritative check lives inside the assignment transaction in
 * {@code AcceleratedAssignmentService}.
 *
 * <p>The decision this implements is diagram 2.1 in the diagrams pack, in the same order:
 * effective group, then International, then declared method, then whether eligible inventory
 * remains, then whether the requested case is part of it. There is deliberately no branch anywhere
 * below testing 99a against 99b — rule 4, order within the accelerated set is unrestricted.
 */
@Aspect
@Component
@Order(0)
public class MandatoryAcceleratedEnforcementAspect {

    private static final Logger log =
            LoggerFactory.getLogger(MandatoryAcceleratedEnforcementAspect.class);

    private final MandatoryAcceleratedEligibilityService eligibility;
    private final RevenueOfficerLookup officers;
    private final AssignmentContext context;
    private final CaseAssignmentAuditService audit;

    public MandatoryAcceleratedEnforcementAspect(
            MandatoryAcceleratedEligibilityService eligibility,
            RevenueOfficerLookup officers,
            AssignmentContext context,
            CaseAssignmentAuditService audit) {
        this.eligibility = eligibility;
        this.officers = officers;
        this.context = context;
        this.audit = audit;
    }

    @Pointcut("@annotation(guard)")
    public void guarded(MandatoryAcceleratedGuarded guard) {}

    @Around(value = "guarded(guard)", argNames = "joinPoint,guard")
    public Object enforce(ProceedingJoinPoint joinPoint, MandatoryAcceleratedGuarded guard)
            throws Throwable {

        GuardedAssignmentCommand command = firstCommandArgument(joinPoint);
        if (command == null) {
            // Fail loudly, at the first call rather than at review. A guarded method with no
            // command has no RO to check, which means it is silently unprotected — the exact
            // failure this design exists to prevent.
            throw new IllegalStateException(
                    "@MandatoryAcceleratedGuarded on %s but no GuardedAssignmentCommand argument"
                            .formatted(joinPoint.getSignature()));
        }

        RoAssignmentNumber ro = command.targetRo();

        // Diagram 2.1, step 1. The effective group is resolved from the impersonation context and
        // never from the actor's home group. A National Analyst operating as "Viewing as: Group
        // NNNNNN" inherits that group's restrictions; reading their own group instead is the most
        // likely bypass in the whole epic. Resolving it here also means it is in scope for the
        // audit record below even on the paths that proceed.
        var actor = context.current();
        String effectiveGroup = actor.effectiveGroupId();
        if (!ro.inGroup(effectiveGroup) && !officers.existsInGroup(ro, effectiveGroup)) {
            log.warn(
                    "Refused {} for {} by {}: RO is outside the effective group {}",
                    command.selectionMethod(),
                    ro,
                    actor.auditIdentity(),
                    effectiveGroup);
            audit.recordBlockedAttempt(
                    actor, ro, command.selectionMethod(), AuditOutcome.BLOCKED_OUT_OF_GROUP);
            throw new MandatoryAcceleratedActiveException(ro, 0);
        }

        // Rule 1: International is out of scope entirely. Nothing below applies.
        if (!officers.programTypeOf(ro).isSubjectToMandatoryAccelerated()) {
            return joinPoint.proceed();
        }

        if (!eligibility.restrictionActive(ro)) {
            return joinPoint.proceed();
        }

        if (permitted(command, guard)) {
            // Rule 10: exception paths run, but using one does not clear the restriction and does
            // not decrement the queued count.
            //
            // Only the two sanctioned workarounds are filed as exception usage. The previous
            // revision logged every permitted method, which meant every ordinary accelerated
            // assignment was recorded as a workaround — and the report compliance will actually
            // ask for, "was Query or Assign by TIN used while the restriction was active and by
            // whom", would have returned the entire day's work.
            if (command.selectionMethod().isSanctionedWorkaround()) {
                audit.recordExceptionPathUsage(actor, ro, command.selectionMethod());
            }
            return joinPoint.proceed();
        }

        int queued = eligibility.counts(ro).queued();
        log.info(
                "Refused {} for {} by {}: {} accelerated cases queued",
                command.selectionMethod(),
                ro,
                actor.auditIdentity(),
                queued);

        // Attempted bypasses are the interesting audit question for a control function, so
        // rejections are recorded at the same fidelity as successes.
        audit.recordBlockedAttempt(actor, ro, command.selectionMethod(), AuditOutcome.BLOCKED);

        throw new MandatoryAcceleratedActiveException(ro, queued);
    }

    private boolean permitted(GuardedAssignmentCommand command, MandatoryAcceleratedGuarded guard) {

        // Rule 9: Manager Queue Control is blocked outright. The sanctioned workarounds are
        // selection paths and they do not unlock Hold/Skip.
        if (guard.queueControl()) {
            return false;
        }

        SelectionMethod method = command.selectionMethod();

        // Rule 12. Report-driven selection is gated, but a Priority 99 case picked from report
        // results is still a Priority 99 case and remains selectable while eligible inventory
        // exists.
        //
        // Scope on Reports is still open — Sarah has not reviewed the modern implementation, so
        // this branch is written to the stated intent. If report-based selection turns out not to
        // exist in modern at all, this branch is inert rather than wrong.
        if (method == SelectionMethod.REPORT_SELECT) {
            return isAcceleratedAlpha(command.priorityAlpha());
        }

        return method.isPermittedDuringRestriction();
    }

    private static boolean isAcceleratedAlpha(String alpha) {
        if (alpha == null || alpha.isBlank()) {
            return false;
        }
        try {
            return PriorityAlpha.parse(alpha).isAccelerated();
        } catch (IllegalArgumentException ex) {
            // Unparseable means unknown, and unknown means blocked. For a control function the
            // safe default is refusal.
            return false;
        }
    }

    private static GuardedAssignmentCommand firstCommandArgument(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof GuardedAssignmentCommand command) {
                return command;
            }
        }
        return null;
    }
}
