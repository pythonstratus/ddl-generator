package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.ReasonCode;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionStatus;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.UnpickNotPermittedException;
import java.time.Instant;
import java.util.UUID;

/**
 * A case selection, and the state machine that governs it.
 *
 * <p><b>The unpick rule lives here, not in the unpick controller.</b> Implementing it as a special
 * case inside the controller means any second unpick path — a bulk operation, an admin tool, a
 * screen someone adds next year — silently misses it. The rule is a property of the selection, so
 * it belongs on the selection.
 *
 * <p><b>No longer a JPA entity.</b> The previous revision annotated this {@code @Entity} while
 * every read path in the module used {@code NamedParameterJdbcTemplate}, and every other data
 * access path in ENTITY uses Spring JDBC. A plain object persisted by
 * {@link JdbcCaseSelectionRepository} removes the split and, more usefully, removes the reliance
 * on dirty checking: with JPA, {@code unpick()} persisted itself as a side effect of the
 * transaction closing. That is invisible in the code and is exactly the kind of thing that stops
 * working when someone later moves a call outside a transaction. Callers now save explicitly.
 */
public class CaseSelection {

    private final UUID selectionId;
    private final String tin;
    private final String tinFileSource;
    private final String roAssignmentNumber;
    private final SelectionMethod selectionMethod;
    private final ReasonCode reasonCode;
    private final String selectedBy;
    private final String selectedAsGroup;
    private final Instant selectedAt;

    private SelectionStatus status;

    public CaseSelection(
            String tin,
            String tinFileSource,
            String roAssignmentNumber,
            SelectionMethod selectionMethod,
            ReasonCode reasonCode,
            String selectedBy,
            String selectedAsGroup) {
        this(
                UUID.randomUUID(),
                tin,
                tinFileSource,
                roAssignmentNumber,
                selectionMethod,
                reasonCode,
                selectedBy,
                selectedAsGroup,
                Instant.now(),
                SelectionStatus.SELECTED);
    }

    private CaseSelection(
            UUID selectionId,
            String tin,
            String tinFileSource,
            String roAssignmentNumber,
            SelectionMethod selectionMethod,
            ReasonCode reasonCode,
            String selectedBy,
            String selectedAsGroup,
            Instant selectedAt,
            SelectionStatus status) {
        this.selectionId = selectionId;
        this.tin = tin;
        this.tinFileSource = tinFileSource;
        this.roAssignmentNumber = roAssignmentNumber;
        this.selectionMethod = selectionMethod;
        this.reasonCode = reasonCode;
        this.selectedBy = selectedBy;
        this.selectedAsGroup = selectedAsGroup;
        this.selectedAt = selectedAt;
        this.status = status;
    }

    /** Rebuilds a selection read from the database. Used only by the repository. */
    static CaseSelection rehydrate(
            UUID selectionId,
            String tin,
            String tinFileSource,
            String roAssignmentNumber,
            SelectionMethod selectionMethod,
            ReasonCode reasonCode,
            String selectedBy,
            String selectedAsGroup,
            Instant selectedAt,
            SelectionStatus status) {
        return new CaseSelection(
                selectionId,
                tin,
                tinFileSource,
                roAssignmentNumber,
                selectionMethod,
                reasonCode,
                selectedBy,
                selectedAsGroup,
                selectedAt,
                status);
    }

    /**
     * Manager-initiated unpick. Refused for accelerated selections at any status.
     *
     * <p>There is no override parameter here on purpose. The emergency unselect capability exists
     * and is not a user function — see {@link EmergencyUnselectService}, a separate entry point
     * with its own authorisation and its own audit event type.
     */
    public void unpick() {
        if (selectionMethod.blocksUnpick()) {
            throw new UnpickNotPermittedException(
                    "Mandatory Accelerated selections cannot be unpicked. Case "
                            + tin
                            + " remains assigned to "
                            + roAssignmentNumber
                            + ".");
        }
        if (!status.isUnpickableByStatus()) {
            throw new IllegalStateException(
                    "Selection is at " + status + " and can no longer be unpicked.");
        }
        this.status = SelectionStatus.QUEUED;
    }

    /**
     * Administrative unselect. Bypasses the method check but not the status check — once a case
     * reaches Pending it is past the point of recovery, which is precisely why the Group Summary
     * Priority 99 count holds until delivery rather than dropping at selection.
     */
    void administrativeUnselect() {
        if (status == SelectionStatus.PENDING || status == SelectionStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Selection is at " + status + "; administrative unselect is not possible.");
        }
        this.status = SelectionStatus.QUEUED;
    }

    public void advanceToPending() {
        if (status != SelectionStatus.SELECTED) {
            throw new IllegalStateException("Only a Selected case can advance to Pending.");
        }
        this.status = SelectionStatus.PENDING;
    }

    public UUID selectionId() {
        return selectionId;
    }

    public SelectionStatus status() {
        return status;
    }

    public SelectionMethod selectionMethod() {
        return selectionMethod;
    }

    public ReasonCode reasonCode() {
        return reasonCode;
    }

    public String roAssignmentNumber() {
        return roAssignmentNumber;
    }

    public String tin() {
        return tin;
    }

    public String tinFileSource() {
        return tinFileSource;
    }

    public String selectedBy() {
        return selectedBy;
    }

    public String selectedAsGroup() {
        return selectedAsGroup;
    }

    public Instant selectedAt() {
        return selectedAt;
    }
}
