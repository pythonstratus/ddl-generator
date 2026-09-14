Two spots, both in `CaseAssignmentService.java`.

## 1. Comparator — right after line 53

Directly below `public class CaseAssignmentService {`, with your other static fields:

```java
private static final Comparator<PriorityCaseData> PRIORITY_ORDER =
    Comparator.comparingInt(CaseAssignmentService::band).reversed()
        .thenComparing(CaseAssignmentService::alphaSuffix)
        .thenComparing(PriorityCaseData::getRank,
            Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(PriorityCaseData::getTin,
            Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(PriorityCaseData::getTinFileSource,
            Comparator.nullsLast(Comparator.naturalOrder()));

private static int band(PriorityCaseData c) {
    String a = c.getPriorityAlpha();
    if (a == null || a.isBlank()) return Integer.MIN_VALUE;
    int end = a.length();
    while (end > 0 && !Character.isDigit(a.charAt(end - 1))) end--;
    try {
        return Integer.parseInt(a.substring(0, end));
    } catch (NumberFormatException e) {
        return Integer.MIN_VALUE;
    }
}

private static String alphaSuffix(PriorityCaseData c) {
    String a = c.getPriorityAlpha();
    return (a == null || a.isBlank()) ? "" : a.substring(a.length() - 1);
}
```

Add `import java.util.Comparator;`.

## 2. TreeSet — between lines 345 and 380

Your screenshot stops at 345, so I can't see it, but the trace puts the `.addAll(` at 380 and the set has to be constructed above that. Scroll to around 350–379, find `new TreeSet<>()`, and pass the comparator:

```java
new TreeSet<>(PRIORITY_ORDER)
```

Don't touch line 380 itself — it's the symptom.

## Two things your screenshots settle

`PriorityCaseData` has `private Integer rank` on line 21. That's the rank-within-alpha field the README diagnostic was asking about, so the missing-data worry for `DISPLAY_ORDER_BY` looks closed — though confirm the direction, since rank 1 usually means best while the legacy model score was higher-is-better. If it's higher-is-better, reverse that clause.

The class has `@Getter/@Setter` but no `@EqualsAndHashCode`, so `equals` is identity-based. A `HashSet` or `LinkedHashSet` would never have deduplicated anything here. In a `TreeSet` the comparator *is* the dedupe key, which is why the chain ends on TIN plus TIN file source — that pair is the case identity, and without it three 99b rows collapse into one.
