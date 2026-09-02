package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import java.util.Optional;

/**
 * Write port onto case inventory.
 *
 * <p>Kept as an interface because in ENTITY this almost certainly belongs to an existing Case
 * Assignment service rather than to this module. If that service already owns the selection
 * lifecycle, implement this as a thin adapter onto it — do not create a parallel write path.
 *
 * <p>A working JDBC implementation ships as {@link JdbcCaseInventoryWriteRepository} and is
 * registered only when the application defines no other {@code CaseInventoryWriteRepository} bean,
 * so an adapter you write always wins.
 */
public interface CaseInventoryWriteRepository {

    /**
     * Locks the case row for update, failing if the version has moved since the client read it.
     *
     * @return empty when the case is gone or the version is stale, which the caller turns into a
     *     specific "no longer available" error rather than a generic failure
     */
    Optional<LockedCase> lockForAssignment(CaseKey caseKey, long expectedRowVersion);

    void markSelected(CaseKey caseKey, RoAssignmentNumber target, long expectedRowVersion);

    void returnToQueue(CaseKey caseKey);

    /** The RO this case is ZIP-aligned to, which is not necessarily who it is assigned to. */
    Optional<RoAssignmentNumber> alignedRevenueOfficer(CaseKey caseKey);

    /** Minimal locked view. Only what the write path needs to make its decision. */
    record LockedCase(CaseKey caseKey, String selectionStatus, long rowVersion) {
        public boolean availableForSelection() {
            return "QUEUED".equals(selectionStatus);
        }
    }
}
