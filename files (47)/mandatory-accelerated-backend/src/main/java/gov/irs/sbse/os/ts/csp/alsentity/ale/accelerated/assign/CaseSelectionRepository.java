package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;
import java.util.Optional;

/**
 * Persistence port for selections.
 *
 * <p>An interface rather than a Spring Data repository so the write path depends on a contract
 * this module owns, and so an ENTITY service that already owns the selection lifecycle can be
 * adapted to it without this module knowing.
 */
public interface CaseSelectionRepository {

    void save(CaseSelection selection);

    /** Persists a status transition produced by the selection's own state machine. */
    void updateStatus(CaseSelection selection);

    Optional<CaseSelection> findByCaseKey(CaseKey caseKey);
}
