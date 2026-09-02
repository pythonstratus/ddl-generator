package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.ProgramType;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import java.util.List;

/**
 * Narrow read port onto whatever already owns Revenue Officer data in ENTITY.
 *
 * <p>Deliberately an interface with no implementation in this module. If there is already an RO or
 * employee service — {@code EntEmpService} is the likely candidate — adapt to it rather than
 * adding a second source of truth for group membership. Leaving it unimplemented means a missing
 * binding fails at startup rather than at 2pm on a Tuesday.
 *
 * <p>Note that {@code DefaultMandatoryAcceleratedEligibilityService} reads the
 * {@code revenue_officer} table directly for its grouped-count aggregate, because eight round
 * trips per Group Summary render is exactly the N+1 the backlog says to avoid. That makes two
 * readers of the roster, so {@code ReconciliationService} asserts they agree.
 */
public interface RevenueOfficerLookup {

    ProgramType programTypeOf(RoAssignmentNumber ro);

    /** Every RO in the group, excluding the queue pseudo-RO whose segment is 7000. */
    List<RoAssignmentNumber> revenueOfficersInGroup(String groupId);

    boolean existsInGroup(RoAssignmentNumber ro, String groupId);
}
