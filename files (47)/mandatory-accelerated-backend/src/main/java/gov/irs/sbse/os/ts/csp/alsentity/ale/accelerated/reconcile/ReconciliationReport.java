package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.reconcile;

import java.time.Instant;
import java.util.List;

/**
 * Output of the reconciliation harness. This is the artifact shown at review, so it reports
 * discrepancies rather than silently absorbing them.
 */
public record ReconciliationReport(
        String groupId,
        Instant runAt,
        boolean reconciled,
        List<Discrepancy> discrepancies,
        List<String> checksRun,
        List<String> checksSkipped) {

    public record Discrepancy(String check, String subject, long expected, long actual) {
        public String describe() {
            return "%s [%s]: expected %d, found %d".formatted(check, subject, expected, actual);
        }
    }

    public String summary() {
        if (reconciled) {
            return "Group %s reconciled at %s (%d checks run, %d skipped)"
                    .formatted(groupId, runAt, checksRun.size(), checksSkipped.size());
        }
        return "Group %s: %d discrepancies%n%s"
                .formatted(
                        groupId,
                        discrepancies.size(),
                        String.join(
                                System.lineSeparator(),
                                discrepancies.stream().map(Discrepancy::describe).toList()));
    }
}
