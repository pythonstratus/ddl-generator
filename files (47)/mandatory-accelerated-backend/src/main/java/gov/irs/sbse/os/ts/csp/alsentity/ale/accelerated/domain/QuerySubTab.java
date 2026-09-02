package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Sub-tabs on Case Assignment &rarr; Query.
 *
 * <p>Rule 11 (confirmed 01 Sep, not in the original requirements document): while a restriction is
 * active, every sub-tab is disabled except PRIORITY_99 and CREATE_QUERY, and the view defaults to
 * Priority 99 results.
 *
 * <p>CREATE_QUERY stays live because Query is one of the two sanctioned workarounds. Sarah's
 * framing was that it should not be advertised, but it must exist.
 *
 * <p>This is state, not routing. Greying a tab in the client is a courtesy; the server still
 * refuses a blocked selection arriving from any of these surfaces.
 */
public enum QuerySubTab {

    MILLION_DOLLAR_CASES,
    PRIORITY_99,
    HINF,
    EGREGIOUS_941,
    HIGH_PRIORITY_CASES,
    NATIONAL,
    LOCAL,
    MY_SAVED_QUERIES,
    CREATE_QUERY,
    QUERY_RESULTS,
    NATIONAL_QUEUE;

    private static final Set<QuerySubTab> ENABLED_DURING_RESTRICTION =
            Set.of(PRIORITY_99, CREATE_QUERY);

    public static final QuerySubTab DEFAULT_DURING_RESTRICTION = PRIORITY_99;

    public boolean isEnabledDuringRestriction() {
        return ENABLED_DURING_RESTRICTION.contains(this);
    }

    public static List<QuerySubTab> enabledDuringRestriction() {
        return Arrays.stream(values()).filter(QuerySubTab::isEnabledDuringRestriction).toList();
    }

    public static List<QuerySubTab> disabledDuringRestriction() {
        return Arrays.stream(values()).filter(t -> !t.isEnabledDuringRestriction()).toList();
    }
}
