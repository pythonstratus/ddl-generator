That's the answer: **there is no ICSZIPS row for 9/4 anymore.** The query shows six rows for that extract date — five daily jobs (rerun today, now COMPLETED) and the S1 weekly FAILED — but the ICSZIPS / COMPLETED row from your Tuesday screenshot is gone. It was deleted along with the others.

The check is now doing exactly what it should: "is ICSZIPS COMPLETED for 9/4?" → no such row → weekly doesn't run.

**How to fix**

Either rerun the ICSZIPS job in DEV so it writes its own row, or re-insert the record you deleted (values from Tuesday's screenshot):

```sql
INSERT INTO job_status
  (extract_name, frequency, job_category, records_loaded,
   start_date, status, steps, stop_date, extract_date)
VALUES
  ('ICSZIPS', 'WEEKLY', 'ICSZIPS WEEKLY', 126573,
   TO_DATE('2026-09-05 14:00:26', 'YYYY-MM-DD HH24:MI:SS'),
   'COMPLETED', 'ICSZIPS',
   TO_DATE('2026-09-05 14:01:16', 'YYYY-MM-DD HH24:MI:SS'),
   DATE '2026-09-04');
COMMIT;
```

Double-check `records_loaded` type — if it's a VARCHAR column (the Java signature passes it as String), quote it: `'126573'`.

Then delete today's S1 FAILED row (and commit) so `saveFailure` doesn't hit the unique constraint again, and rerun the weekly job. It should pass the check and actually run this time.

Going forward, when clearing out FAILED rows, filter on status so the ICSZIPS marker survives:

```sql
DELETE FROM job_status
WHERE TRUNC(extract_date) = DATE '2026-09-04'
  AND status = 'FAILED';
COMMIT;
```
