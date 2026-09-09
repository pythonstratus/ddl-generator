Yes, both go in `JobStatusService.java`. Here are the exact edits.

**1. Add an import** (near line 6, with the other `java.time` imports):

```java
import java.time.temporal.TemporalAdjusters;
```

**2. Replace `getCurrentWeekdayDate` (lines 106–115)** — the whole if/else block becomes one line:

```java
public String getCurrentWeekdayDate(LocalDate icszipsRunDate, DayOfWeek targetDay) {
    return icszipsRunDate.with(TemporalAdjusters.previousOrSame(targetDay))
                         .format(formatter);
}
```

**3. Edit `icszipsStatusCheck` (line 95)** — change the null check:

```java
if (jobStatus == null || jobStatus.isEmpty()) {
    return false;
}
```

**Simple explanation of what happened**

Before the weekly jobs run, the code checks "did the ICSZIPS job finish successfully?" To do that, it figures out which date's ICSZIPS row to look up in the database.

The date math was written assuming the job always runs on Sunday. On Sunday, "find Friday" means going back one day — correct. But you reran it on Wednesday, so "find Thursday" went *forward* to 9/10 — a date that hasn't happened yet. The database had no row for 9/10, returned null, and the code crashed trying to call `.isEmpty()` on that null.

**How the fix works**

- `previousOrSame(THURSDAY)` always walks *backward* to the most recent Thursday (or stays put if it already is Thursday), no matter what day you run it. So reruns on any weekday look at a real past date.
- The null check means that even if the row is missing, the job returns `false` cleanly ("ICSZIPS not done") instead of throwing an exception.

After deploying, rerunning today should find the 9/4 COMPLETED ICSZIPS row and proceed.
