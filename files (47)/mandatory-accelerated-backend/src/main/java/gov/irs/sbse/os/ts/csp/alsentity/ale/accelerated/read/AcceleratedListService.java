package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.read;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.AcceleratedCounts;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility.MandatoryAcceleratedEligibilityService;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serves the RO list, the group list and the Group Summary Priority 99 column. */
@Service
public class AcceleratedListService {

    /**
     * Matches the "Top 200" ceiling on the existing RO case selection dropdown, which is the
     * control FE-A proposes to reuse — if that spike holds, the RO half of the frontend story is
     * a default plus a disable rather than a new screen.
     */
    public static final int DEFAULT_PAGE_SIZE = 50;

    public static final int MAX_PAGE_SIZE = 200;

    private final AcceleratedCaseRepository repository;
    private final MandatoryAcceleratedEligibilityService eligibility;

    public AcceleratedListService(
            AcceleratedCaseRepository repository,
            MandatoryAcceleratedEligibilityService eligibility) {
        this.repository = repository;
        this.eligibility = eligibility;
    }

    @Transactional(readOnly = true)
    public AcceleratedCasePage forRevenueOfficer(RoAssignmentNumber ro, int page, int size) {
        int effectiveSize = clampSize(size);
        return new AcceleratedCasePage(
                repository.findForRo(ro, page, effectiveSize),
                eligibility.counts(ro),
                page,
                effectiveSize,
                repository.countForRo(ro));
    }

    /**
     * The group screen. No analogue exists in modern, which is why this is the one surface that
     * cannot be reduced to a filter on an existing view, and why dropping it from a reduced first
     * release is a real parity regression rather than a cosmetic one — F2 is how a manager
     * rebalances an RO holding 55 accelerated cases against one holding 5.
     *
     * <p>The header counts come from a group-wide aggregate, not from summing the per-RO counts.
     * Summing double-counts every case that aligns to more than one RO, so the header would read
     * higher than the list beneath it and no amount of staring at the list would explain why.
     */
    @Transactional(readOnly = true)
    public AcceleratedCasePage forGroup(String groupId, int page, int size) {
        int effectiveSize = clampSize(size);
        return new AcceleratedCasePage(
                repository.findForGroup(groupId, page, effectiveSize),
                eligibility.groupWideCounts(groupId),
                page,
                effectiveSize,
                repository.countForGroup(groupId));
    }

    /**
     * The Priority 99 column on the Group Summary employee table.
     *
     * <p>Two things this must not do. It must not filter or reorder the employee table — rule 14,
     * every employee is listed all the time, restricted or not. And the value it returns must not
     * decrement when a case is selected: an RO showing 46 still shows 46 after two assignments,
     * with Pending at 2. That is {@link AcceleratedCounts#groupSummaryPriority99()}, not
     * {@link AcceleratedCounts#queued()}. Wiring the wrong one is the single easiest mistake in
     * this epic and it looks correct in a demo.
     *
     * <p>One query, not a loop. The previous revision called the single-RO aggregate once per
     * employee, on the screen a manager opens first every time.
     */
    @Transactional(readOnly = true)
    public Map<RoAssignmentNumber, AcceleratedCounts> groupSummaryCounts(String groupId) {
        return eligibility.countsForGroup(groupId);
    }

    private static int clampSize(int requested) {
        return requested <= 0 ? DEFAULT_PAGE_SIZE : Math.min(requested, MAX_PAGE_SIZE);
    }
}
