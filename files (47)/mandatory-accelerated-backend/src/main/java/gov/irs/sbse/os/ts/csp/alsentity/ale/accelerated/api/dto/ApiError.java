package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Structured error body.
 *
 * <p>The count and route are part of the contract, not decoration. A blocked action with no stated
 * next step turns into a support call, and the client cannot construct the route itself without
 * duplicating the enforcement rules.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String errorCode,
        String message,
        String roAssignmentNumber,
        Integer queuedCount,
        String redirect) {

    public static ApiError of(String errorCode, String message) {
        return new ApiError(errorCode, message, null, null, null);
    }
}
