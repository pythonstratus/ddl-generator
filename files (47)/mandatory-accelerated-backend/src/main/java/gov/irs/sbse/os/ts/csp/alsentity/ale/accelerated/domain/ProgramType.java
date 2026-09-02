package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain;

/**
 * Program type. Rule 1: Mandatory Accelerated applies to GENERAL only. INTERNATIONAL is excluded
 * entirely and its existing programming is unchanged — no International path is gated, and an
 * International RO always reports {@code restrictionActive = false}.
 */
public enum ProgramType {
    GENERAL,
    INTERNATIONAL;

    public boolean isSubjectToMandatoryAccelerated() {
        return this == GENERAL;
    }
}
