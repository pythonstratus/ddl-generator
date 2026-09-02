package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.api.dto;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.status.RestrictionState;
import java.util.List;

/** Wire shape for the status endpoint. */
public record StatusResponse(
        String roAssignmentNumber,
        String programType,
        boolean restrictionActive,
        int queuedCount,
        int listedCount,
        int pendingCount,
        List<SelectionMethod> permittedExceptions) {

    public static StatusResponse from(RestrictionState state) {
        return new StatusResponse(
                state.roAssignmentNumber(),
                state.programType().name(),
                state.restrictionActive(),
                state.queuedCount(),
                state.listedCount(),
                state.pendingCount(),
                state.permittedExceptions());
    }
}
