package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain;

import java.util.Set;

/**
 * How a case selection was made.
 *
 * <p>This is stored on the selection record rather than a boolean {@code mandatory_accelerated}
 * flag, because the audit and reconciliation work needs to distinguish exception-path usage from
 * ordinary selection, and a boolean cannot answer that.
 *
 * <p>The enforcement aspect reads this from an explicit request parameter and never infers it from
 * which endpoint was called. Inference means a newly added endpoint silently defaults to
 * permitted, which is the wrong failure direction for a control function.
 */
public enum SelectionMethod {

    /** Blocked while a restriction is active. */
    AUTO_SELECT,

    /** Blocked while a restriction is active. */
    ZIP_CODE_SELECT,

    /** Blocked while a restriction is active, except for Priority 99 rows. Rule 12. */
    REPORT_SELECT,

    /** Manager Queue Control — Hold/Skip Date. Never permitted while active. Rule 9. */
    QUEUE_CONTROL,

    /** Sanctioned workaround. Permits any priority level. Does not clear the restriction. */
    QUERY,

    /** Sanctioned workaround. Permits any priority level. Does not clear the restriction. */
    ASSIGN_BY_TIN,

    /** The accelerated path itself. Always permitted; cannot be unpicked. */
    MANDATORY_ACCELERATED;

    /**
     * Rule 10: exactly two sanctioned workarounds. This is the set the audit trail reports as
     * exception-path usage, and it deliberately excludes {@link #MANDATORY_ACCELERATED} — taking
     * an accelerated case is the rule being enforced, not a way around it. Folding it in here
     * would file every legitimate accelerated assignment as a workaround and make the one report
     * compliance will actually ask for useless.
     */
    private static final Set<SelectionMethod> SANCTIONED_WORKAROUNDS = Set.of(QUERY, ASSIGN_BY_TIN);

    /** Methods that may proceed while a restriction is active. */
    private static final Set<SelectionMethod> PERMITTED_DURING_RESTRICTION =
            Set.of(QUERY, ASSIGN_BY_TIN, MANDATORY_ACCELERATED);

    public boolean isPermittedDuringRestriction() {
        return PERMITTED_DURING_RESTRICTION.contains(this);
    }

    /** Rule 10. Drives the exception-usage audit record, not the permit decision. */
    public boolean isSanctionedWorkaround() {
        return SANCTIONED_WORKAROUNDS.contains(this);
    }

    /**
     * Rule 8: a Mandatory Accelerated selection cannot be unpicked. Ordinary selections remain
     * unpickable up to the point they reach Pending.
     */
    public boolean blocksUnpick() {
        return this == MANDATORY_ACCELERATED;
    }
}
