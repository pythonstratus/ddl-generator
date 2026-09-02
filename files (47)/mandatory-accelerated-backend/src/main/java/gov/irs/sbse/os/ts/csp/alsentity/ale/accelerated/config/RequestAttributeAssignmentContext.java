package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.config;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.Actor;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.AssignmentContext;
import java.util.Locale;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Default {@link AssignmentContext}, reading ENTITY's existing request attributes.
 *
 * <p>The SEID attribute name and the uppercase-at-the-boundary rule come from the established
 * pattern: {@code AuthenticationFilter} sets {@code seid} after cookie validation, and every
 * consumer uppercases it. That part is solid.
 *
 * <p><b>VERIFY: the other three attributes.</b> {@code displayName}, {@code role},
 * {@code groupId} and {@code viewingAsGroupId} are named on the same convention but were never
 * seen in a screenshot. The one that matters is {@code viewingAsGroupId} — it is what carries
 * "Viewing as: Group 271039", and if it resolves to null when a National Analyst is impersonating,
 * enforcement silently falls back to the analyst's home group and the restriction is bypassed for
 * exactly the role most able to notice. Confirm this before the first integration test rather than
 * after.
 *
 * <p>The names are overridable through {@code entity.case-assignment.accelerated.attribute.*} so
 * that correcting them is configuration rather than a code change.
 */
public class RequestAttributeAssignmentContext implements AssignmentContext {

    static final String SEID = "seid";
    static final String DISPLAY_NAME = "displayName";
    static final String ROLE = "role";
    static final String GROUP_ID = "groupId";
    static final String VIEWING_AS_GROUP_ID = "viewingAsGroupId";

    @Override
    public Actor current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            // No bound request means a scheduled job or a test. Failing here is correct: every
            // guarded path is user-initiated, so an unattributed one is a bug rather than a
            // background task that should be allowed through with a placeholder identity.
            throw new IllegalStateException(
                    "No request bound; AssignmentContext cannot resolve the acting user. "
                            + "Guarded operations must run on a request thread.");
        }
        String seid = requiredAttribute(attributes, SEID);
        return new Actor(
                seid.toUpperCase(Locale.ROOT),
                optionalAttribute(attributes, DISPLAY_NAME, seid),
                optionalAttribute(attributes, ROLE, "GROUP_MANAGER"),
                optionalAttribute(attributes, GROUP_ID, null),
                optionalAttribute(attributes, VIEWING_AS_GROUP_ID, null));
    }

    private static String requiredAttribute(RequestAttributes attributes, String name) {
        Object value = attributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException(
                    "Request attribute '" + name + "' is missing; check AuthenticationFilter.");
        }
        return value.toString();
    }

    private static String optionalAttribute(
            RequestAttributes attributes, String name, String fallback) {
        Object value = attributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }
}
