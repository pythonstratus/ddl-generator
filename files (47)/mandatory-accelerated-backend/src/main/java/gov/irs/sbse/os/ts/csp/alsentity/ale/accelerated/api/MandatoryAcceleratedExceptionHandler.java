package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.api;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.api.dto.ApiError;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.CaseNoLongerAvailableException;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.CrossGroupCaseException;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.MandatoryAcceleratedActiveException;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.UnpickNotPermittedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the enforcement exceptions into the structured error contract.
 *
 * <p><b>Global, not scoped to this module's controller — and that is the fix, not an oversight.</b>
 * The previous revision bound this advice to {@code MandatoryAcceleratedController} alone. But the
 * enforcement aspect sits on the <i>existing</i> selection services, so a blocked Auto Select
 * arrives through the existing Case Assignment controller and would have fallen through to a bare
 * 500. The one thing FE-B most needs — a clear message with the count and a route, from every
 * blocked path — would have worked only on the new screen.
 *
 * <p>Highest precedence so it wins against a broader existing advice, and it handles only the four
 * exception types this module defines. It deliberately does <b>not</b> handle
 * {@code IllegalArgumentException}: a global handler for that would change how unrelated
 * controllers across ENTITY report their own validation failures, which is not this module's call
 * to make.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MandatoryAcceleratedExceptionHandler {

    /**
     * 409, not 403.
     *
     * <p>A 403 reads as an authorization failure and gets routed to the security team, who will
     * spend a day establishing it is not theirs. The manager is permitted to do this — just not
     * until the accelerated inventory is cleared. That is a state conflict.
     */
    @ExceptionHandler(MandatoryAcceleratedActiveException.class)
    public ResponseEntity<ApiError> handleRestriction(MandatoryAcceleratedActiveException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(
                        MandatoryAcceleratedActiveException.ERROR_CODE,
                        ex.getMessage(),
                        ex.ro().toString(),
                        ex.queuedCount(),
                        ex.redirect()));
    }

    /** 409. Someone else took the case between page load and click. */
    @ExceptionHandler(CaseNoLongerAvailableException.class)
    public ResponseEntity<ApiError> handleStale(CaseNoLongerAvailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(CaseNoLongerAvailableException.ERROR_CODE, ex.getMessage()));
    }

    /** 409. The case is not in the effective group's accelerated inventory. */
    @ExceptionHandler(CrossGroupCaseException.class)
    public ResponseEntity<ApiError> handleCrossGroup(CrossGroupCaseException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(CrossGroupCaseException.ERROR_CODE, ex.getMessage()));
    }

    /**
     * 422. Distinct from the restriction error on purpose — this one is permanent, and a message
     * that suggests retrying later would be actively misleading.
     */
    @ExceptionHandler(UnpickNotPermittedException.class)
    public ResponseEntity<ApiError> handleUnpick(UnpickNotPermittedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(UnpickNotPermittedException.ERROR_CODE, ex.getMessage()));
    }
}
