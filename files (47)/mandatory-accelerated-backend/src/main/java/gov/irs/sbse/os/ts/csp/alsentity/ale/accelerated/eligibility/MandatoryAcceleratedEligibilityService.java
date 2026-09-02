package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.AcceleratedCounts;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import java.util.Map;

/**
 * The authoritative eligibility rule set. Everything downstream consumes this interface and
 * nothing re-implements the rules.
 *
 * <p>Two access patterns, deliberately separate:
 *
 * <ul>
 *   <li><b>Set-based</b> — "all eligible cases for RO X". A query. Used by the list screens.
 *   <li><b>Predicate-based</b> — "is this one case eligible". Called on every assignment attempt
 *       and on every guarded service call, so it has to be cheap. Do not implement it by
 *       materialising the set and searching it.
 * </ul>
 *
 * <p>{@code invalidate} is on the interface rather than only on the implementation. The previous
 * revision had the write path inject the concrete class to reach it, which works only while
 * Spring is producing CGLIB proxies and fails the day someone sets
 * {@code spring.aop.proxy-target-class=false}. Callers depend on this interface and nothing else.
 */
public interface MandatoryAcceleratedEligibilityService {

    /**
     * Whether the restriction currently applies to this RO. False for International ROs without
     * touching the database.
     */
    boolean restrictionActive(RoAssignmentNumber ro);

    /**
     * All four counts, derived from one query so they cannot disagree with the lists. May be
     * served from the per-request cache.
     */
    AcceleratedCounts counts(RoAssignmentNumber ro);

    /**
     * The same counts, always read fresh.
     *
     * <p>Used inside the assignment transaction. Reading through the cache there would either
     * return a pre-write value or, worse, populate the cache with a value that is only visible
     * inside an uncommitted transaction.
     */
    AcceleratedCounts countsUncached(RoAssignmentNumber ro);

    /**
     * Counts for every Revenue Officer in a group, in one query.
     *
     * <p>Rule 14: the Group Summary employee table lists all employees at all times, so ROs with
     * no accelerated inventory appear here with zeroes rather than being absent.
     */
    Map<RoAssignmentNumber, AcceleratedCounts> countsForGroup(String groupId);

    /** Deduplicated counts across a whole group, for the group screen header. */
    AcceleratedCounts groupWideCounts(String groupId);

    /** Predicate access. Cheap by contract. */
    EligibilityDecision evaluate(CaseKey caseKey, RoAssignmentNumber ro);

    /**
     * Same as {@link #evaluate} but only valid inside the caller's write transaction, where the
     * case row is already locked. The aspect's pre-check is advisory; this one is authoritative.
     */
    EligibilityDecision evaluateForUpdate(CaseKey caseKey, RoAssignmentNumber ro);

    /** Drops any cached state for this RO. Safe to call when nothing is cached. */
    void invalidate(RoAssignmentNumber ro);
}
