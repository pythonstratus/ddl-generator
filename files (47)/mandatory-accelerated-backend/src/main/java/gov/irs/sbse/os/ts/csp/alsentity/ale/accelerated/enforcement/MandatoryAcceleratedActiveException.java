package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;

/**
 * Thrown when an action is refused because accelerated inventory remains.
 *
 * <p>Surfaces as <b>409 Conflict</b>, not 403. A 403 reads as an authorization failure and gets
 * triaged to the security team, who will correctly conclude it is not theirs and hand it back a
 * day later. This is a state conflict: the manager is permitted to do this, just not yet.
 *
 * <p>The payload carries the count and a route because "this action is blocked" with no next step
 * becomes a support ticket every time.
 */
public class MandatoryAcceleratedActiveException extends RuntimeException {

    public static final String ERROR_CODE = "MANDATORY_ACCELERATED_ACTIVE";

    private final transient RoAssignmentNumber ro;
    private final int queuedCount;

    public MandatoryAcceleratedActiveException(RoAssignmentNumber ro, int queuedCount) {
        super("Priority 99 inventory must be assigned first.");
        this.ro = ro;
        this.queuedCount = queuedCount;
    }

    public RoAssignmentNumber ro() {
        return ro;
    }

    public int queuedCount() {
        return queuedCount;
    }

    public String redirect() {
        return "/case-assignment/mandatory-accelerated?ro=" + ro;
    }
}
