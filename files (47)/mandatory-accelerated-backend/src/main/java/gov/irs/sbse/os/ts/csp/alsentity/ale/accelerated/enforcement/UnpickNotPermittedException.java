package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement;

/**
 * Thrown when an unpick is attempted against a Mandatory Accelerated selection.
 *
 * <p>Rule 8. A distinct error code from the ordinary "already Pending" rejection, so the message
 * can explain that this is a property of accelerated selections rather than a timing problem the
 * manager might retry. FE-C must state it in the confirmation before the selection is made, not
 * leave it to be discovered here.
 */
public class UnpickNotPermittedException extends RuntimeException {

    public static final String ERROR_CODE = "UNPICK_NOT_PERMITTED_MANDATORY_ACCELERATED";

    public UnpickNotPermittedException(String detail) {
        super(detail);
    }
}
