package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;

/**
 * Contract every gated command implements.
 *
 * <p>The selection method is declared by the caller rather than inferred from which endpoint was
 * hit. That is the whole point: inference means a newly added endpoint silently defaults to
 * permitted, and for a control function the safe default has to be blocked.
 */
public interface GuardedAssignmentCommand {

    /** The RO the case would be assigned to. Determines whose restriction applies. */
    RoAssignmentNumber targetRo();

    /** How the selection is being made. Never inferred. */
    SelectionMethod selectionMethod();

    /**
     * Priority alpha of the case being selected, where known.
     *
     * <p>Used only for report-driven selection, where rule 12 says a Priority 99 case may still be
     * picked from report results while eligible inventory exists. Null means unknown, which is
     * treated as non-accelerated and therefore blocked.
     */
    default String priorityAlpha() {
        return null;
    }
}
