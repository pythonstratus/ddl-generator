package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.AcceleratedCounts;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Caches the counts <b>for the duration of one HTTP request</b> and no longer.
 *
 * <p>The status call is hit on nearly every page load and several times within a single render —
 * the aspect asks, the list service asks, the controller asks. Caching it matters. Caching it
 * across requests does not, and is dangerous here.
 *
 * <h2>Why not a shared Caffeine cache</h2>
 *
 * The backlog is explicit: invalidate on write, never on a TTL, because a stale "restriction
 * cleared" is a control bypass. A JVM-local cache cannot honour that in a multi-instance
 * deployment — instance A commits an assignment and evicts its own entry, and instance B keeps
 * serving the value it computed before the write. The failure is usually benign (B blocks a
 * manager who has finished) but the reverse ordering is not, and neither is defensible in a
 * control function on a federal tax system. A TTL would fix the staleness and is the thing the
 * business explicitly ruled out.
 *
 * <p>Request scope sidesteps the trade entirely. Every request recomputes once, from indexed
 * aggregates the migration adds indexes for, and is then internally consistent for the rest of
 * its own work. Nothing can observe a value written by another request.
 *
 * <p>If measurement later shows the aggregate is too expensive to run once per request, the
 * replacement is a shared cache <i>plus</i> a distributed invalidation signal — not a TTL, and not
 * a JVM-local map.
 *
 * <p>Outside a request — the reconciliation harness, scheduled work, tests — there is no request
 * bound to the thread and this degrades to a straight pass-through with no caching. That is
 * correct rather than merely tolerable: a reconciliation run must read source data, not a cache.
 */
@Component
public class RestrictionStatusCache {

    private static final String ATTRIBUTE = RestrictionStatusCache.class.getName() + ".counts";

    /**
     * @param loader executed on a miss, and on every call when no request is bound
     */
    public AcceleratedCounts get(RoAssignmentNumber ro, Supplier<AcceleratedCounts> loader) {
        Map<String, AcceleratedCounts> scope = scope();
        if (scope == null) {
            return loader.get();
        }
        return scope.computeIfAbsent(ro.toString(), key -> loader.get());
    }

    /**
     * Drops this RO's entry. Called by the write path so a read later in the same request — the
     * refreshed header on the assignment response, for instance — sees the post-write value.
     */
    public void invalidate(RoAssignmentNumber ro) {
        Map<String, AcceleratedCounts> scope = scope();
        if (scope != null) {
            scope.remove(ro.toString());
        }
    }

    /** Drops everything. Used when a write may have moved more than one RO's numbers. */
    public void invalidateAll() {
        Map<String, AcceleratedCounts> scope = scope();
        if (scope != null) {
            scope.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, AcceleratedCounts> scope() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object existing = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, AcceleratedCounts>) map;
        }
        Map<String, AcceleratedCounts> created = new HashMap<>();
        attributes.setAttribute(ATTRIBUTE, created, RequestAttributes.SCOPE_REQUEST);
        return created;
    }
}
