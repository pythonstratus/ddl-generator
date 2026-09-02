package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.Actor;

/**
 * Request-scoped holder for the acting user and the group they are operating as.
 *
 * <p>A default reading ENTITY's existing {@code seid} request attribute ships in
 * {@code config.RequestAttributeAssignmentContext}, and is registered only if the application does
 * not already define an {@code AssignmentContext} bean.
 *
 * <p>The one thing that must not change in any implementation is that enforcement and audit both
 * read {@link Actor#effectiveGroupId()} rather than the actor's home group.
 */
public interface AssignmentContext {

    Actor current();

    default String effectiveGroupId() {
        return current().effectiveGroupId();
    }
}
