package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain;

/**
 * Reason for case request. Persist the code; render the display string at presentation time so it
 * can be reworded without a data migration. Legacy shows the text
 * "REASON FOR CASE REQUEST: MANDATORY ACCELERATED CASE" on screen.
 */
public enum ReasonCode {

    MANDATORY_ACCELERATED_CASE("Mandatory Accelerated Case"),
    STANDARD_ASSIGNMENT("Standard Assignment"),
    CUSTOM_ASSIGNMENT("Custom Assignment");

    private final String displayText;

    ReasonCode(String displayText) {
        this.displayText = displayText;
    }

    public String displayText() {
        return displayText;
    }
}
