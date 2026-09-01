## ENTITY — CTRS files

**Legacy process (the baseline to replicate)**
1. At month-end, entity users submit time reporting → files land in the legacy `FTP/ctrs` directory.
2. EFTU picks them up (job appears to run every ~15 minutes) and delivers them to the CTRS server.
3. On successful delivery, EFTU renames the file and moves it to `ctrs_archive`. **That move is the delivery confirmation** — it's how CCD knows the file made it.
4. A separate job (built by Diane and Samuel) scans the directory and emails a cumulative status every 2 hours through the month-end window: total files transferred, count for today, and each file's size. Expected size is **5994 bytes** — fixed unless the S1 changes — so any other size flags a partial or corrupted file.

**Where modernization actually stands**
Only step one exists. The modern app writes CTRS files to the PVC `entity/CTRS` folder (Ranjitha showed this), which is the equivalent of legacy `FTP/ctrs`. Diane pushed back on the claim that CTRS is "finished and done" — nothing past file generation is built.

**Remaining work**
- Move files from the PVC to the **EFTU S3 bucket** so EFTU can pick them up.
- Get a delivery confirmation back. Two known options: EFTU moves/renames the file to a designated location, or EFTU sends an email. Samuel had also floated EFTU writing to a log or pushing to the database — unverified, needs the EFTU team's sign-off.
- Archive the delivered file to the PVC. Files cannot stay on the EFTU S3 bucket.
- Build the alert email to CCD for: file not delivered, corrupted file, size 0, or size ≠ 5994. Format doesn't have to match legacy exactly, it just has to confirm all-clear and flag problems. (Ranjitha tied this to ticket 506.)

**Decisions and deferrals**
- **Definition of Done for this story = files archived on the PVC.** Long-term storage is explicitly excluded and will not be mixed into this story (Sree confirmed twice).
- Email vs. file-move confirmation gets decided at the point the EFTU form is filled out. If email is chosen, EFTU doesn't need to do any moving — Diaconia's own job handles archival.
- Sree and Samuel raised replacing the emails with a status view in the modern app (like the extract job status page, possibly its own tab given the volume). Diane agreed it would be "infinitely better" but deferred it given schedule pressure. Sree asked that it still be captured as a requirement; he and Islam will prioritize.

**Open**
- How often the PVC → S3 move should run (legacy is ~15 min).
- Diane to write up exact step-by-step instructions for Ranjitha, who owns the JIRA ticket.

## ALS — 5801 files

**Legacy process**
- The file is `data.com`. It's dropped in the same FTP directory as CTRS. EFTU picks it up, delivers it, and renames it **`data.done`** in place — no separate archive folder, unlike CTRS. `.com` = unsent, `.done` = delivered. The rename is the confirmation.
- A cron job then runs shell script **CK5801.CSH** against the 5801 data. A companion script (**582**) handles the return transaction from IDRS. Errors go to a 5801 error file, which feeds an email notification.
- Parveen: per the job list Z gave him, this runs **Tuesday, Wednesday, Friday at 8:45 AM ET**.

**Modern approach**
Parveen has already built this as a scheduled job (native scheduler or Control-M — TBD). It watches the drop location, archives arriving data to a defined archive location, and sends the error email. He asked whether that's an oversimplification; Diane couldn't confirm since ALS isn't her area, and Jordan said he isn't part of this process.

**The blocking question**
Is the `.com` → `.done` rename actually required by EFTU, or can modernization handle the sent/unsent distinction another way? Parveen will read the script, document what the current job does, and share it with the group.

## The shared blocker

**The EFTU form cannot be submitted for either CTRS or ALS until Diaconia decides the confirmation mechanism and target location.** Until the form goes in, no files transfer and both CTRS and ALS are missing this functionality. Diane flagged this as the critical path item in both halves of the meeting.

## Cross-cutting notes

- **Two different S3 buckets** — Diane insisted on this distinction. The EFTU transfer bucket (in scope) is not the long-term archive bucket (out of scope). Chinmaya's S3 tickets relate to long-term storage only.
- **Long-term storage**: CCD held its internal meeting last week. Options under consideration for backing up the ~52 GB N7 prod drive are EFTU, a curl upload to S3, or another tool installed on the server. Unresolved, Commvault mentioned as a possible cross-the-board choice. **No action needed from Diaconia right now.**
- **SIA files were not covered** — meeting ran out of time. Rescheduled for **Thursday 2–3 PM ET**, Jordan sending the invite to the same attendee list. Topic is the SIA EFTU delivery process.

One caveat: the transcription garbles EFTU badly (it appears as ESU, ESQ, EITU, ESGU, ESKU) and CTRS shows up as CQRS, CPRS, TTRS, and CGRS. I've normalized both. Want me to put this into a Word doc or markdown file you can circulate?
