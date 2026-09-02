package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;

/**
 * Thrown when a case left available queue inventory between the pre-check and the write.
 *
 * <p>Two managers in the same group working a shared 45-case list will select the same case. One
 * commits; the other needs to be told specifically what happened, not handed a generic failure and
 * certainly not allowed to produce a duplicate assignment.
 */
public class CaseNoLongerAvailableException extends RuntimeException {

    public static final String ERROR_CODE = "CASE_NO_LONGER_AVAILABLE";

    private final transient CaseKey caseKey;

    public CaseNoLongerAvailableException(CaseKey caseKey) {
        super("This case is no longer in available queue inventory. Refresh the list.");
        this.caseKey = caseKey;
    }

    public CaseNoLongerAvailableException(CaseKey caseKey, String detail) {
        super(detail);
        this.caseKey = caseKey;
    }

    public CaseKey caseKey() {
        return caseKey;
    }
}
