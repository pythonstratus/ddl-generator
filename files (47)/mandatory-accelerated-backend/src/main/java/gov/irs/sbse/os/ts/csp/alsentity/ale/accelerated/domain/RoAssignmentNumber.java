package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain;

import java.util.regex.Pattern;

/**
 * A Revenue Officer assignment number in the form {@code 2710-3910}, where the leading segment
 * identifies the group.
 *
 * <p>An assignment number whose RO segment is {@code 7000} denotes queue membership rather than an
 * individual RO. That was confirmed on the call as a requirement to adhere to, and it is how the
 * queued-versus-listed distinction is derived: a case leaves the queued count when its assignment
 * number changes from the queue to an RO.
 */
public record RoAssignmentNumber(String groupId, String roSegment) {

    private static final Pattern FORMAT = Pattern.compile("^(\\d{4})-(\\d{4})$");

    /** RO segment value denoting the queue rather than an individual Revenue Officer. */
    public static final String QUEUE_SEGMENT = "7000";

    public RoAssignmentNumber {
        if (groupId == null || roSegment == null) {
            throw new IllegalArgumentException("groupId and roSegment are required");
        }
    }

    public static RoAssignmentNumber parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("assignment number is required");
        }
        var matcher = FORMAT.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("expected NNNN-NNNN, got: " + raw);
        }
        return new RoAssignmentNumber(matcher.group(1), matcher.group(2));
    }

    /** True when this number denotes queue inventory rather than an assigned Revenue Officer. */
    public boolean isQueue() {
        return QUEUE_SEGMENT.equals(roSegment);
    }

    /** Rule 6: any RO sharing this group is a valid assignment target. */
    public boolean sameGroupAs(RoAssignmentNumber other) {
        return other != null && groupId.equals(other.groupId);
    }

    public boolean inGroup(String candidateGroupId) {
        return groupId.equals(candidateGroupId);
    }

    @Override
    public String toString() {
        return groupId + "-" + roSegment;
    }
}
