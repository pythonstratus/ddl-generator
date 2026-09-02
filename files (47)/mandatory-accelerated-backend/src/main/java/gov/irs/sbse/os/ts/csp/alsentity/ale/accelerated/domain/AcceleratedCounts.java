package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain;

/**
 * The four counts. Two fall on selection and two hold, and getting this backwards is the likeliest
 * defect in the build — so all four are derived from one query rather than maintained separately.
 *
 * <table>
 *   <caption>Behaviour on selection</caption>
 *   <tr><th>Count</th><th>On selection</th><th>Meaning</th></tr>
 *   <tr><td>queued</td><td>decrements</td><td>eligible and still unselected</td></tr>
 *   <tr><td>listed</td><td>holds</td><td>every row shown on the list, Selected and Pending included</td></tr>
 *   <tr><td>groupSummaryPriority99</td><td>holds</td><td>eligible and not yet delivered</td></tr>
 *   <tr><td>pending</td><td>increments</td><td>eligible and at Pending</td></tr>
 * </table>
 *
 * <p>The restriction lifts when {@code queued} reaches zero, never when {@code listed} does.
 * Carrying a single count produces a counter that appears stuck.
 *
 * <p>The Group Summary column holding at 46 rather than dropping to 44 after two assignments is
 * correct behaviour, confirmed directly on 01 Sep. It holds because a selected-but-not-Pending
 * case is still recoverable through the emergency unselect path, so the figure reflects what has
 * not yet irreversibly left the queue.
 *
 * <p><b>listed and groupSummaryPriority99 are equal today and are still kept apart.</b> They are
 * equal only because the eligibility predicate currently admits exactly QUEUED, SELECTED and
 * PENDING. Open question 4 — whether K-Skipped can appear on the accelerated list — would make
 * {@code listed} larger than {@code groupSummaryPriority99} the day it is answered yes. Collapsing
 * them now means finding every call site again then.
 */
public record AcceleratedCounts(
        int queued,
        int listed,
        int groupSummaryPriority99,
        int pending) {

    public static final AcceleratedCounts NONE = new AcceleratedCounts(0, 0, 0, 0);

    public AcceleratedCounts {
        if (queued < 0 || listed < 0 || groupSummaryPriority99 < 0 || pending < 0) {
            throw new IllegalArgumentException("counts cannot be negative");
        }
    }

    /** Rule 2 and rule 7: active while unselected eligible inventory remains, and not after. */
    public boolean restrictionActive() {
        return queued > 0;
    }

    /** Sums two RO-scoped count sets. Only safe where the underlying case sets are disjoint. */
    public AcceleratedCounts plus(AcceleratedCounts other) {
        return new AcceleratedCounts(
                queued + other.queued,
                listed + other.listed,
                groupSummaryPriority99 + other.groupSummaryPriority99,
                pending + other.pending);
    }
}
