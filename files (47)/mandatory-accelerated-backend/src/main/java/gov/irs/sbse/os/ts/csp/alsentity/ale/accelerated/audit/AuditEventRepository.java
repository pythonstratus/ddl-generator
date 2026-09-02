package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.audit;

/** Append-only. There is deliberately no update and no delete on this interface. */
public interface AuditEventRepository {

    void append(AuditEvent event);
}
