package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method as subject to Mandatory Accelerated enforcement.
 *
 * <p>Placed on <b>service</b> methods, never on controllers and never on URL patterns. A
 * URL-pattern filter is bypassed the moment anyone adds a route, and the requirement is explicit
 * that a different screen, search, sort or workflow must not defeat the restriction. The check has
 * to sit below every entry point rather than in front of some of them.
 *
 * <p>Apply to: Auto Select, ZIP Code Select, report-driven selection, and every future selection
 * path. Also apply to Hold/Skip <i>write</i> operations via {@code queueControl = true}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MandatoryAcceleratedGuarded {

    /**
     * True for Manager Queue Control writes (Hold/Skip Date). Rule 9: blocked outright while a
     * restriction is active, with no permitted-method carve-out, because the two sanctioned
     * workarounds are selection paths and queue control is not one.
     */
    boolean queueControl() default false;
}
