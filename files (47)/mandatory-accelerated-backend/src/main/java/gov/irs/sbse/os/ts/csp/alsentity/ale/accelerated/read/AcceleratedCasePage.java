package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.read;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.AcceleratedCounts;
import java.util.List;

/**
 * A page of accelerated cases with its header counts.
 *
 * <p>Counts travel with the page rather than being fetched separately, because they are derived
 * from the same predicate and a separate round trip is exactly how the header and the body end up
 * disagreeing after a concurrent assignment.
 *
 * <p>The legacy header reads {@code QUEUED: 45  LISTED: 45}. Both values are here for parity, and
 * they diverge as soon as assignments begin.
 */
public record AcceleratedCasePage(
        List<AcceleratedCaseRow> rows,
        AcceleratedCounts counts,
        int page,
        int size,
        long totalElements) {

    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    /**
     * Rule 15 and the FE-A empty state. Zero accelerated cases is the signal that normal
     * assignment methods are available, and the screen should say so rather than render a blank
     * table.
     */
    public boolean empty() {
        return rows.isEmpty();
    }
}
