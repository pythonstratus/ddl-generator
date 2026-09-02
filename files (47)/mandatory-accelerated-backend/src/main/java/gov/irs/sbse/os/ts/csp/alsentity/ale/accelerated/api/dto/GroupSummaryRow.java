package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.api.dto;

/**
 * One row of the Group Summary employee table's Priority 99 column.
 *
 * @param priority99Count holds when cases are selected. An RO showing 46 still shows 46 after two
 *     assignments; {@code pendingCount} rises to 2 instead. Confirmed correct behaviour, not a
 *     defect — do not wire this field to the queued count. It falls only on delivery, because a
 *     selected-but-not-Pending case is still recoverable.
 * @param queuedCount the one that decrements, and the one the restriction and the FE-B counter
 *     read. Present here so the client never has to derive it.
 */
public record GroupSummaryRow(
        String roAssignmentNumber,
        int priority99Count,
        int pendingCount,
        int queuedCount,
        boolean restrictionActive) {}
