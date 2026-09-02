package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.status;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.ProgramType;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.QuerySubTab;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility.MandatoryAcceleratedEligibilityService;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility.RevenueOfficerLookup;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the status and UI-state payloads from the eligibility counts. */
@Service
public class RestrictionStateService {

    /** Rule 10. Exactly two, and this list is the contract the client renders from. */
    private static final List<SelectionMethod> PERMITTED_EXCEPTIONS =
            List.of(SelectionMethod.QUERY, SelectionMethod.ASSIGN_BY_TIN);

    private final MandatoryAcceleratedEligibilityService eligibility;
    private final RevenueOfficerLookup officers;

    public RestrictionStateService(
            MandatoryAcceleratedEligibilityService eligibility, RevenueOfficerLookup officers) {
        this.eligibility = eligibility;
        this.officers = officers;
    }

    @Transactional(readOnly = true)
    public RestrictionState statusFor(RoAssignmentNumber ro) {
        ProgramType programType = officers.programTypeOf(ro);
        if (!programType.isSubjectToMandatoryAccelerated()) {
            return RestrictionState.inactive(ro.toString(), programType);
        }
        var counts = eligibility.counts(ro);
        return new RestrictionState(
                ro.toString(),
                programType,
                counts.restrictionActive(),
                counts.queued(),
                counts.listed(),
                counts.pending(),
                counts.restrictionActive() ? PERMITTED_EXCEPTIONS : List.of());
    }

    @Transactional(readOnly = true)
    public UiRestrictionState uiStateFor(RoAssignmentNumber ro) {
        var status = statusFor(ro);
        if (!status.restrictionActive()) {
            return UiRestrictionState.unrestricted();
        }
        return new UiRestrictionState(
                true,
                // Rule 7 and the FE-B counter. Queued, not listed — listed never moves, so a
                // counter bound to it never decrements and the unlock never fires.
                status.queuedCount(),
                QuerySubTab.enabledDuringRestriction(),
                QuerySubTab.disabledDuringRestriction(),
                QuerySubTab.DEFAULT_DURING_RESTRICTION,
                false,
                false,
                false,
                true,
                List.of(
                        SelectionMethod.QUERY,
                        SelectionMethod.ASSIGN_BY_TIN,
                        SelectionMethod.MANDATORY_ACCELERATED),
                "/case-assignment/mandatory-accelerated?ro=" + ro);
    }
}
