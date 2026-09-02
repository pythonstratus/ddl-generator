package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.ReasonCode;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.GuardedAssignmentCommand;

/**
 * Assign one accelerated case.
 *
 * @param expectedRowVersion the value the client read with the row. Sent back so a stale click
 *     fails cleanly rather than overwriting someone else's assignment.
 */
public record AcceleratedAssignmentCommand(
        CaseKey caseKey, RoAssignmentNumber targetRo, long expectedRowVersion)
        implements GuardedAssignmentCommand {

    public AcceleratedAssignmentCommand {
        if (caseKey == null || targetRo == null) {
            throw new IllegalArgumentException("caseKey and targetRo are required");
        }
    }

    @Override
    public SelectionMethod selectionMethod() {
        return SelectionMethod.MANDATORY_ACCELERATED;
    }

    /**
     * Fixed, not chosen. FE-C pre-populates everything except the target RO, and the reason is not
     * editable — unlike Assign by TIN, which offers reason options.
     */
    public ReasonCode reasonCode() {
        return ReasonCode.MANDATORY_ACCELERATED_CASE;
    }
}
