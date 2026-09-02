package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request body for an accelerated assignment.
 *
 * @param tin nine digits, with or without the hyphen. Normalised by {@code CaseKey}.
 * @param targetRoAssignmentNumber any RO in the manager's group. Deliberately not validated
 *     against ZIP or grade alignment — rule 6, and validating it here would break rebalancing,
 *     which is the group screen's whole purpose.
 * @param expectedRowVersion the version the client read with the row, echoed back so a stale click
 *     fails cleanly instead of overwriting someone else's assignment.
 */
public record AssignRequest(
        @NotBlank @Pattern(regexp = "^\\d{2}-?\\d{7}$|^\\d{3}-?\\d{2}-?\\d{4}$") String tin,
        @NotBlank String tinFileSource,
        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{4}$") String targetRoAssignmentNumber,
        @PositiveOrZero long expectedRowVersion) {}
