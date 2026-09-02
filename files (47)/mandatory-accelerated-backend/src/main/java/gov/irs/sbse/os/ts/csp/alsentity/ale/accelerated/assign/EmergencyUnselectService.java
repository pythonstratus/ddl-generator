package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.audit.AuditOutcome;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.audit.CaseAssignmentAuditService;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility.MandatoryAcceleratedEligibilityService;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.AssignmentContext;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.CaseNoLongerAvailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emergency unselect. <b>Not a manager function, and it must never become one.</b>
 *
 * <p>Sarah described past incidents in legacy where a selected-but-not-yet-Pending accelerated
 * case had to be unselected on a manager's behalf, done manually by developers. This is the
 * supported version of that. It is a separate service from the ordinary unpick path specifically
 * so it cannot be reached by accident from a screen.
 *
 * <p><b>Open question 2, and it is still open.</b> Whether modern wants a supported administrative
 * path at all, or whether a controlled data fix remains acceptable as it was in legacy. The
 * service is disabled by default; leave it that way until there is an answer. This is linked to
 * why the Group Summary Priority 99 count holds until delivery — a selected case is still
 * recoverable, and this is what recovers it.
 */
@Service
public class EmergencyUnselectService {

    private final CaseSelectionRepository selections;
    private final CaseInventoryWriteRepository inventory;
    private final MandatoryAcceleratedEligibilityService eligibility;
    private final AssignmentContext context;
    private final CaseAssignmentAuditService audit;

    @Value("${entity.case-assignment.accelerated.emergency-unselect.enabled:false}")
    private boolean enabled;

    public EmergencyUnselectService(
            CaseSelectionRepository selections,
            CaseInventoryWriteRepository inventory,
            MandatoryAcceleratedEligibilityService eligibility,
            AssignmentContext context,
            CaseAssignmentAuditService audit) {
        this.selections = selections;
        this.inventory = inventory;
        this.eligibility = eligibility;
        this.context = context;
        this.audit = audit;
    }

    /**
     * @param justification free text, required, and written to the audit record. An emergency
     *     action with no stated reason is indistinguishable from an unauthorised one after the
     *     fact.
     */
    @Transactional
    public void unselect(CaseKey caseKey, String justification) {
        if (!enabled) {
            throw new UnsupportedOperationException(
                    "Emergency unselect is disabled. Enable it only once the business owner has "
                            + "confirmed a supported administrative path is wanted.");
        }
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("A written justification is required.");
        }

        var selection = selections
                .findByCaseKey(caseKey)
                .orElseThrow(() -> new CaseNoLongerAvailableException(caseKey));

        var ro = RoAssignmentNumber.parse(selection.roAssignmentNumber());
        selection.administrativeUnselect();
        selections.updateStatus(selection);
        inventory.returnToQueue(caseKey);
        eligibility.invalidate(ro);

        audit.recordEmergencyUnselect(
                context.current(), ro, caseKey, justification, AuditOutcome.SUCCESS);
    }
}
