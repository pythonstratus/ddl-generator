package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.status;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.ProgramType;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;
import java.util.List;

/**
 * The single status payload every screen gates on. One call, consumed everywhere — scattering
 * {@code restrictionActive} checks across screens guarantees one of them is missed or drifts.
 */
public record RestrictionState(
        String roAssignmentNumber,
        ProgramType programType,
        boolean restrictionActive,
        int queuedCount,
        int listedCount,
        int pendingCount,
        List<SelectionMethod> permittedExceptions) {

    public static RestrictionState inactive(String ro, ProgramType programType) {
        return new RestrictionState(ro, programType, false, 0, 0, 0, List.of());
    }
}
