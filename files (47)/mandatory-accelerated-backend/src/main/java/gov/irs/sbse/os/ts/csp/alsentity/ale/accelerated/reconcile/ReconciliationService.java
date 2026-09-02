package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.reconcile;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.AcceleratedCounts;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility.MandatoryAcceleratedEligibilityService;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility.RevenueOfficerLookup;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.read.AcceleratedCaseRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks that displayed counts and case sets agree with source data.
 *
 * <p>Runs on demand in MTEST against production-equivalent data. A hand-built fixture only proves
 * the code agrees with itself.
 *
 * <p><b>What changed, and why it matters for what this report is worth.</b> The previous revision
 * compared the RO list size against the RO listed count. Both derive from the same predicate, so
 * that check was a tautology — it could never fail, and a green report meant nothing. The checks
 * below are chosen so each one can actually fail:
 *
 * <ol>
 *   <li><b>Internal consistency of the counts.</b> listed must equal queued + selected + pending.
 *       Fails if a status appears that the count arithmetic does not account for, which is the
 *       shape open question 4 (K-Skipped) would take when answered.
 *   <li><b>Union of RO lists equals the group list.</b> Compared as sets of case keys, not as
 *       totals, because a case aligned to two ROs is double-counted by two wrong numbers that can
 *       still sum to the right one. This is the check that catches the group predicate drifting
 *       away from the RO predicate — the specific defect found in the previous revision.
 *   <li><b>Roster agreement.</b> The grouped count query reads {@code revenue_officer} directly
 *       for aggregate efficiency while {@code RevenueOfficerLookup} is the authority for
 *       membership. Two readers of the roster is a drift risk, so they are asserted to agree.
 *   <li><b>Group list against source prioritization data.</b> Not implemented — the source is not
 *       identified in any document available to this build. Reported as skipped rather than
 *       quietly omitted, so nobody reads a green report as covering more than it does.
 * </ol>
 */
@Service
public class ReconciliationService {

    private static final String CHECK_COUNT_ARITHMETIC = "count arithmetic";
    private static final String CHECK_UNION = "union of RO lists vs group list";
    private static final String CHECK_ROSTER = "roster agreement";
    private static final String CHECK_SOURCE = "group list vs source prioritization data";

    private final AcceleratedCaseRepository repository;
    private final MandatoryAcceleratedEligibilityService eligibility;
    private final RevenueOfficerLookup officers;

    public ReconciliationService(
            AcceleratedCaseRepository repository,
            MandatoryAcceleratedEligibilityService eligibility,
            RevenueOfficerLookup officers) {
        this.repository = repository;
        this.eligibility = eligibility;
        this.officers = officers;
    }

    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(String groupId) {
        List<ReconciliationReport.Discrepancy> found = new ArrayList<>();

        var perRo = eligibility.countsForGroup(groupId);

        // 1. Count arithmetic, per RO and for the group header.
        perRo.forEach((ro, counts) -> checkArithmetic(found, ro.toString(), counts));
        checkArithmetic(found, "group " + groupId, eligibility.groupWideCounts(groupId));

        // 2. Union of the RO lists against the group list, as sets of case keys.
        Set<String> union = new HashSet<>();
        perRo.keySet().forEach(ro -> union.addAll(repository.caseKeysForRo(ro)));
        Set<String> groupKeys = new HashSet<>(repository.caseKeysForGroup(groupId));

        if (!union.equals(groupKeys)) {
            var onlyInGroupList = new TreeSet<>(groupKeys);
            onlyInGroupList.removeAll(union);
            var onlyInRoLists = new TreeSet<>(union);
            onlyInRoLists.removeAll(groupKeys);

            found.add(new ReconciliationReport.Discrepancy(
                    CHECK_UNION, groupId, union.size(), groupKeys.size()));

            // Naming the offenders is what turns this from an alarm into a diagnosis. A case in
            // the group list but no RO list means the group predicate is looser than the RO one.
            if (!onlyInGroupList.isEmpty()) {
                found.add(new ReconciliationReport.Discrepancy(
                        CHECK_UNION + " — in group list only, first: " + onlyInGroupList.first(),
                        groupId,
                        0,
                        onlyInGroupList.size()));
            }
            if (!onlyInRoLists.isEmpty()) {
                found.add(new ReconciliationReport.Discrepancy(
                        CHECK_UNION + " — in RO lists only, first: " + onlyInRoLists.first(),
                        groupId,
                        0,
                        onlyInRoLists.size()));
            }
        }

        // 3. The two readers of the roster must agree.
        Set<String> fromLookup = new HashSet<>();
        for (RoAssignmentNumber ro : officers.revenueOfficersInGroup(groupId)) {
            fromLookup.add(ro.toString());
        }
        Set<String> fromCounts = new HashSet<>();
        perRo.keySet().forEach(ro -> fromCounts.add(ro.toString()));
        if (!fromLookup.equals(fromCounts)) {
            found.add(new ReconciliationReport.Discrepancy(
                    CHECK_ROSTER, groupId, fromLookup.size(), fromCounts.size()));
        }

        return new ReconciliationReport(
                groupId,
                Instant.now(),
                found.isEmpty(),
                found,
                List.of(CHECK_COUNT_ARITHMETIC, CHECK_UNION, CHECK_ROSTER),
                List.of(CHECK_SOURCE + " — source not identified; see README open items"));
    }

    private static void checkArithmetic(
            List<ReconciliationReport.Discrepancy> found, String subject, AcceleratedCounts c) {
        // groupSummaryPriority99 counts queued + selected + pending, so selected is derivable.
        int selected = c.groupSummaryPriority99() - c.queued() - c.pending();
        if (selected < 0) {
            found.add(new ReconciliationReport.Discrepancy(
                    CHECK_COUNT_ARITHMETIC + " — negative selected", subject, 0, selected));
        }
        if (c.listed() < c.groupSummaryPriority99()) {
            found.add(new ReconciliationReport.Discrepancy(
                    CHECK_COUNT_ARITHMETIC + " — listed below undelivered",
                    subject,
                    c.groupSummaryPriority99(),
                    c.listed()));
        }
    }
}
