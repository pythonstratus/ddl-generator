package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;

/**
 * Thrown when a case belongs to a group other than the one the actor is operating as.
 *
 * <p>Rule 6 opens the assignment <i>target</i> right up — any RO in the group, aligned or not.
 * It says nothing about the case, and the previous revision checked only that the target RO was a
 * group member. A manager who knew a TIN could therefore assign a case aligned to another group's
 * RO into their own group, through the ordinary endpoint, and the case would simply leave the
 * other group's queue.
 *
 * <p>409 rather than 403 for the same reason as the restriction error: this is a state and scope
 * conflict, not an authentication problem, and it should be triaged to the case assignment team.
 */
public class CrossGroupCaseException extends RuntimeException {

    public static final String ERROR_CODE = "CASE_OUTSIDE_EFFECTIVE_GROUP";

    private final transient CaseKey caseKey;

    public CrossGroupCaseException(CaseKey caseKey, String effectiveGroupId) {
        super(
                "Case %s is not in the accelerated inventory of group %s."
                        .formatted(caseKey, effectiveGroupId));
        this.caseKey = caseKey;
    }

    public CaseKey caseKey() {
        return caseKey;
    }
}
