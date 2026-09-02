package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;

/**
 * Manager Queue Control write — Hold/Skip Date and anything alongside it.
 *
 * <p>Annotate the handling service method with
 * {@code @MandatoryAcceleratedGuarded(queueControl = true)}. Block the write, not the read: the
 * Hold/Skip tab stays viewable throughout, and the block lifts automatically once the restriction
 * is satisfied, with no reload.
 */
public record QueueControlCommand(RoAssignmentNumber targetRo, String operation)
        implements GuardedAssignmentCommand {

    /**
     * Queue control is not a selection method, and the previous revision returned AUTO_SELECT
     * here — a value that was never true and that landed in the audit record as though a manager
     * had attempted an Auto Select. {@code QUEUE_CONTROL} exists so the refusal is recorded as
     * what it actually was.
     */
    @Override
    public SelectionMethod selectionMethod() {
        return SelectionMethod.QUEUE_CONTROL;
    }
}
