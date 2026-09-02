package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;

/**
 * Reference command for the existing selection paths — Auto Select, ZIP Code Select, Query, Assign
 * by TIN and report-driven selection.
 *
 * <p>Wire your existing selection service methods to accept this and annotate them. That is the
 * whole integration: one parameter type and one annotation per path.
 *
 * <pre>{@code
 * @MandatoryAcceleratedGuarded
 * @Transactional
 * public SelectionResult autoSelect(StandardSelectionCommand command) { ... }
 * }</pre>
 */
public record StandardSelectionCommand(
        RoAssignmentNumber targetRo, SelectionMethod selectionMethod, String priorityAlpha)
        implements GuardedAssignmentCommand {

    public StandardSelectionCommand {
        if (targetRo == null) {
            throw new IllegalArgumentException("targetRo is required to resolve the restriction");
        }
        if (selectionMethod == null) {
            throw new IllegalArgumentException(
                    "selectionMethod must be declared explicitly; it is never inferred");
        }
    }

    /** Convenience for the paths that do not carry a priority alpha. */
    public static StandardSelectionCommand of(RoAssignmentNumber ro, SelectionMethod method) {
        return new StandardSelectionCommand(ro, method, null);
    }
}
