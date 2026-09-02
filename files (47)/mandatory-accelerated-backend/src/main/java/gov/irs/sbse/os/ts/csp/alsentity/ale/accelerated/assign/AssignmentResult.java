package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.AcceleratedCounts;
import java.util.UUID;

/**
 * Outcome of an accelerated assignment.
 *
 * <p>Refreshed counts come back on the response so the client updates the header without a second
 * call and without polling. The count changes on a known event; polling only adds load to an
 * endpoint already hit on most page loads.
 *
 * <p><b>Two sets of counts, and the distinction is the whole of rule 6.</b> When a case aligned to
 * one RO is assigned to another — the rebalancing case, and the reason the group screen exists —
 * the numbers that move belong to the <i>alignment</i> RO, because that is whose accelerated set
 * the case was a member of. The target RO's counts do not change at all.
 *
 * @param alignmentRoAssignmentNumber whose accelerated set the case left
 * @param counts the alignment RO's refreshed counts. This is what the header and the unlock read.
 * @param restrictionLifted derived from {@code counts}, so it answers "is the alignment RO now
 *     clear", which is the question rule 7 is about. Deriving it from the target RO's counts —
 *     as the previous revision did — reports on an RO nothing just happened to, and on a
 *     rebalancing assignment it is simply the wrong answer.
 */
public record AssignmentResult(
        UUID selectionId,
        String tin,
        String assignedTo,
        String alignmentRoAssignmentNumber,
        String reasonDisplayText,
        AcceleratedCounts counts,
        boolean restrictionLifted) {}
