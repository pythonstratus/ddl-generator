package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain;

/**
 * Who is acting, and on whose behalf.
 *
 * <p>The screenshots show a National Analyst operating with "Viewing as: Group 271039" in the
 * header. Enforcement and audit must both resolve the <b>effective</b> group, not the actor's own.
 * Reading the actor's own group instead lets an analyst bypass the restriction entirely, which is
 * the most likely security gap in this epic.
 *
 * @param userId the SEID, uppercased at the boundary in line with the rest of ENTITY
 */
public record Actor(
        String userId,
        String displayName,
        String role,
        String homeGroupId,
        String viewingAsGroupId) {

    /** The group whose rules apply to this request. */
    public String effectiveGroupId() {
        return viewingAsGroupId != null && !viewingAsGroupId.isBlank()
                ? viewingAsGroupId
                : homeGroupId;
    }

    public boolean isImpersonating() {
        return viewingAsGroupId != null
                && !viewingAsGroupId.isBlank()
                && !viewingAsGroupId.equals(homeGroupId);
    }

    /** The impersonation chain as it should appear in the audit record. */
    public String auditIdentity() {
        return isImpersonating()
                ? "%s (%s) acting as Group %s".formatted(displayName, role, viewingAsGroupId)
                : "%s (%s)".formatted(displayName, role);
    }
}
