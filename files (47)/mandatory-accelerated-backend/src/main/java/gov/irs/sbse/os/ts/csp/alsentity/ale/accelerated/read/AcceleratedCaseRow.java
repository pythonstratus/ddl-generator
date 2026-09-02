package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.read;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row on an accelerated list. The field set matches the MTEST Auto Selection Priority 99
 * layout column for column, so the group screen and the RO screen are the same table component
 * with a different data source.
 *
 * @param alignedRoAssignmentNumber the ZIP ASSN TO RO column. Populated on the group list only.
 *     This is information for the manager, not a constraint — rule 6 allows assignment to any RO
 *     in the group regardless of what this says.
 * @param caseBalance decimal, never floating point. Balances run past $55,000,000 and a double
 *     breaks reconciliation the first time it runs against real data.
 * @param modelScore drives rank within a single alpha value. Nullable, and if it is null across
 *     the board the display ordering is not trustworthy — see the README diagnostic.
 * @param rowVersion optimistic lock token, echoed back on assignment so two managers picking the
 *     same case produce one winner and one specific error rather than a duplicate assignment.
 */
public record AcceleratedCaseRow(
        String priorityAlpha,
        String caseType,
        String caseGrade,
        String hinf941,
        BigDecimal caseBalance,
        String taxpayerName,
        String city,
        String stateCode,
        String zipCode,
        String tin,
        String tinFileSource,
        String potentialAssignmentNumber,
        String currentAssignmentNumber,
        String queuePickFlag,
        LocalDate dateAssignedQueueFile,
        SelectionStatus selectionStatus,
        String alignedRoAssignmentNumber,
        Integer modelScore,
        long rowVersion) {

    /** The QIND letter the screen shows: S-Selected, P-Pending, H-Hold, K-Skipped. */
    public String qind() {
        return selectionStatus.qind();
    }

    /** True when this row is still selectable. Selected and Pending stay listed but are not. */
    public boolean selectable() {
        return selectionStatus == SelectionStatus.QUEUED;
    }

    /** Stable key for reconciliation and for client-side row identity. */
    public String caseKey() {
        return tin + "/" + tinFileSource;
    }
}
