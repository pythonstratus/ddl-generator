package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A priority alpha value such as "99a" or "95c".
 *
 * <p>Two rules from the 01 Sep walkthrough are encoded here and must not be conflated:
 *
 * <ul>
 *   <li><b>Display order</b> is strictly alpha ascending, 99a first. Non-negotiable.
 *   <li><b>Selection order</b> is the manager's choice. There is deliberately no
 *       "99a must be taken before 99b" gate anywhere in this package.
 * </ul>
 *
 * <p>Never compare these values as strings. Lexical comparison puts {@code 101b} and {@code 103b}
 * above {@code 99a} because {@code 1} sorts before {@code 9}, which is why band and alpha are
 * parsed apart and ordered as a tuple.
 *
 * <p>Alpha alone does not fully order a list. Within a single alpha value, rank is driven by a
 * model score calculated against balance. See {@code EligibilitySql#DISPLAY_ORDER_BY} and the
 * payload diagnostic in the README.
 */
public record PriorityAlpha(int band, char alpha) implements Comparable<PriorityAlpha> {

    /** The band that makes a case subject to Mandatory Accelerated. */
    public static final int ACCELERATED_BAND = 99;

    /** All five accelerated alpha values. Every one of them is in scope, not just 99a. */
    public static final Set<String> ACCELERATED_VALUES = Set.of("99a", "99b", "99c", "99d", "99e");

    /**
     * The same five values in display order, for building SQL IN lists without hand-maintaining a
     * second copy of them. Ordered so a generated literal list reads the way the screen does.
     */
    public static final List<String> ACCELERATED_VALUES_IN_ORDER =
            List.of("99a", "99b", "99c", "99d", "99e");

    public PriorityAlpha {
        if (band < 0) {
            throw new IllegalArgumentException("band must be non-negative: " + band);
        }
        alpha = Character.toLowerCase(alpha);
        if (alpha < 'a' || alpha > 'z') {
            throw new IllegalArgumentException("alpha must be a letter: " + alpha);
        }
    }

    /** Parses values in the form {@code 99a}. Case-insensitive. */
    public static PriorityAlpha parse(String raw) {
        if (raw == null || raw.length() < 2) {
            throw new IllegalArgumentException("unparseable priority alpha: " + raw);
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        char letter = trimmed.charAt(trimmed.length() - 1);
        int band;
        try {
            band = Integer.parseInt(trimmed.substring(0, trimmed.length() - 1));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("unparseable priority alpha: " + raw, ex);
        }
        return new PriorityAlpha(band, letter);
    }

    /** True when this value places the case in the Mandatory Accelerated set. */
    public boolean isAccelerated() {
        return band == ACCELERATED_BAND;
    }

    /**
     * Display ordering: higher band first (99 before 95 before 88), then alpha ascending
     * (a before b). This is the only comparator any list should sort by at the alpha level.
     */
    public static final Comparator<PriorityAlpha> DISPLAY_ORDER =
            Comparator.comparingInt(PriorityAlpha::band).reversed()
                    .thenComparing(PriorityAlpha::alpha);

    @Override
    public int compareTo(PriorityAlpha other) {
        return DISPLAY_ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return band + String.valueOf(alpha);
    }
}
