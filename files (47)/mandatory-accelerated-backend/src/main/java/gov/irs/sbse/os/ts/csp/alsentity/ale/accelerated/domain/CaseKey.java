package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain;

/**
 * Identifies a case in queue inventory. TIN alone is not sufficient — the same TIN can appear
 * under more than one file source — so the file source is part of the key.
 */
public record CaseKey(String tin, String tinFileSource) {

    public CaseKey {
        if (tin == null || tin.isBlank()) {
            throw new IllegalArgumentException("tin is required");
        }
        if (tinFileSource == null || tinFileSource.isBlank()) {
            throw new IllegalArgumentException("tinFileSource is required");
        }
        tin = tin.replace("-", "").trim();
        tinFileSource = tinFileSource.trim().toUpperCase();
    }

    @Override
    public String toString() {
        return tin + "/" + tinFileSource;
    }
}
