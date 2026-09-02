package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.status;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.QuerySubTab;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;
import java.util.List;

/**
 * What the UI should render as disabled.
 *
 * <p><b>This is advisory, not enforcement.</b> The server rejects blocked actions regardless of
 * what the client did with this payload. Hiding a control is a courtesy to the manager; it is not
 * a control, and anyone calling the API directly bypasses it entirely. FE-B has to handle the 409
 * as well, because the count can change between page load and click.
 *
 * @param holdSkipWritable rule 9. The Hold/Skip tab stays readable — the write is blocked, not the
 *     view — and re-enables automatically when the restriction lifts, with no reload.
 * @param pendingRestricted always false. Rule 13: Pending is a list view only and carries no
 *     restriction. Present as an explicit false so a client reading this payload does not have to
 *     infer it from an absence.
 * @param groupSummaryFiltered always false. Rule 14: the employee table lists all employees at all
 *     times and is neither filtered nor reordered.
 */
public record UiRestrictionState(
        boolean restrictionActive,
        int queuedCount,
        List<QuerySubTab> enabledQuerySubTabs,
        List<QuerySubTab> disabledQuerySubTabs,
        QuerySubTab defaultQuerySubTab,
        boolean holdSkipWritable,
        boolean pendingRestricted,
        boolean groupSummaryFiltered,
        boolean reportSelectionRestrictedToPriority99,
        List<SelectionMethod> permittedSelectionMethods,
        String acceleratedScreenRoute) {

    public static UiRestrictionState unrestricted() {
        return new UiRestrictionState(
                false,
                0,
                List.of(QuerySubTab.values()),
                List.of(),
                null,
                true,
                false,
                false,
                false,
                List.of(SelectionMethod.values()),
                null);
    }
}
