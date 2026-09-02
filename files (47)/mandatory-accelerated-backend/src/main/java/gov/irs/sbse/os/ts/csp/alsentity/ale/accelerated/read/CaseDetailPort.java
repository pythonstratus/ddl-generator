package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.read;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;
import java.util.Map;

/**
 * BE-B subtask 4 — case detail passthrough for the review-before-assign flow.
 *
 * <p>Deliberately a port with no implementation here. ENTITY already has a case detail service and
 * this module must call it rather than grow a second one.
 *
 * <p><b>Check this early — week one, not week three.</b> The open risk is that the existing case
 * detail endpoint requires an assignment context that a queued case does not yet have. A case
 * sitting in the queue has no RO, so a signature that takes an RO, or a lookup that joins through
 * an assignment record, will return nothing for exactly the cases this screen is about. If that is
 * how it works, FE-C needs a different endpoint and the estimate moves.
 *
 * <p>Returned as a map rather than a typed record on purpose: the shape belongs to the existing
 * service, and inventing a DTO here would mean maintaining a second definition of it.
 */
public interface CaseDetailPort {

    /**
     * Case summary, modules, activity, time, and name and address, in whatever shape the existing
     * service returns them.
     */
    Map<String, Object> caseDetail(CaseKey caseKey);
}
