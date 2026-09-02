package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.audit;

/** Outcome recorded against every audited action. */
public enum AuditOutcome {
    SUCCESS,
    /** Refused by the restriction. */
    BLOCKED,
    /**
     * Refused because the RO or the case sat outside the actor's effective group. Distinct from
     * BLOCKED because it is the signature of an attempted scope escape rather than a manager
     * meeting the rule, and those two should not share a bucket in a compliance report.
     */
    BLOCKED_OUT_OF_GROUP,
    /** Case had left available queue inventory, or the row version had moved. */
    REJECTED_STALE,
    /** Case failed the in-transaction eligibility re-check. */
    REJECTED_INELIGIBLE
}
