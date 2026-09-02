package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility;

import java.util.List;

/**
 * Why a case is or is not part of an RO's accelerated set.
 *
 * <p>Carrying the failed reasons rather than a bare boolean is what makes the reconciliation
 * harness and the support path workable. When a manager reports that a case they expected is
 * missing, the answer is here rather than in a query someone has to reconstruct.
 */
public record EligibilityDecision(boolean eligible, List<String> failedRules) {

    public EligibilityDecision {
        failedRules = failedRules == null ? List.of() : List.copyOf(failedRules);
    }

    public static EligibilityDecision eligible() {
        return new EligibilityDecision(true, List.of());
    }

    public static EligibilityDecision ineligible(String... reasons) {
        return new EligibilityDecision(false, List.of(reasons));
    }

    public String summary() {
        return eligible ? "eligible" : String.join("; ", failedRules);
    }
}
