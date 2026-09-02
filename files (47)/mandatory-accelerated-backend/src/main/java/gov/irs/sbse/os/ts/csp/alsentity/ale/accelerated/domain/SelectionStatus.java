package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain;

/**
 * Lifecycle of a case selection, and the QIND letter the screens display for it. The counts in
 * {@link AcceleratedCounts} are derived from this, which is why they behave differently from one
 * another.
 *
 * <p>{@code displayRank} drives the "Selected and Pending pin to the top" rule. It is mirrored by
 * a CASE expression in {@code EligibilitySql#QIND_STATUS_RANK} so that the ordering is applied in
 * the database and survives pagination — sorting after the fetch re-sequences each page
 * independently, which is the defect logged against modern on 08/26/2026.
 */
public enum SelectionStatus {

    /** S-Selected. Chosen by a manager but not yet delivered. Still recoverable. */
    SELECTED("S", 0),

    /** P-Pending. Past the point of recovery. */
    PENDING("P", 1),

    /**
     * H-Hold. GM Hold File. Currently outside the eligibility predicate — see open question 4,
     * which asks the same thing about K-Skipped. Present in the enum so a row that carries it
     * maps rather than throwing.
     */
    HOLD("H", 2),

    /** K-Skipped. Accelerated cases cannot be skipped, so this should never appear. Open item. */
    SKIPPED("K", 3),

    /** Not selected. Sitting in available queue inventory. */
    QUEUED("", 4),

    /** Delivered to the Revenue Officer's inventory. Has left the queue entirely. */
    DELIVERED("", 5);

    private final String qind;
    private final int displayRank;

    SelectionStatus(String qind, int displayRank) {
        this.qind = qind;
        this.displayRank = displayRank;
    }

    /** The QIND letter shown on the accelerated list. Empty for statuses that show no letter. */
    public String qind() {
        return qind;
    }

    public int displayRank() {
        return displayRank;
    }

    /** Ordinary selections are unpickable only before they reach Pending. */
    public boolean isUnpickableByStatus() {
        return this == SELECTED;
    }

    /** Counted by the Group Summary Priority 99 column, which holds until delivery. */
    public boolean isUndelivered() {
        return this == QUEUED || this == SELECTED || this == PENDING;
    }
}
