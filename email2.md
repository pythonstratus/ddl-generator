'''

Subject: E2 ETL failure (Sun 9/20) – summary and next steps

Hi all,

Here's a quick summary of Sunday's ETL failure, where things stand, and what's next.

What happened
- 9/11: Ranjita asked Matthew to check on GoldenGate in Dev and Test.
- 9/14: Matthew explained that since the target DBs had been restored to an earlier point in time, GoldenGate was far behind the source, and the replicats had to be reset to the restore point to re-apply the source changes. The replica tables weren't updated for the rest of the week.
- Sat 9/19: Matthew rebuilt the replication, which recreated the ALS_LEGACY_REPLICA tables (LA, for example, was created 9/19 at 4:42 PM).
- Sun 9/20: The replica tables were up to date that morning, but the E2 ETL job failed when it ran.

Root cause
- When the tables were recreated, the grants were re-applied, but ENTITY's direct grants didn't come back on LA, LS, LD, XE and XF. LA's grants tab shows SELECT only for ALSRPT, ALS_LEGACY_RO, DEAL and DEALRPT, with no ENTITY.
- GETREFDATE reads these tables and needs direct grants, so it failed and took E2 down with it.
- This is the same fix Matthew applied in ALSTEST on 3/14, when he granted direct privs on the ALS_LEGACY_REPLICA-owned tables to ENTITY.

Impact
- The dependency is narrow: GETREFDATE only supplies the lien refile date that E2 loads into INTMOD.
- No files were expected Sunday night, so we have until end of day today (9/21) to get this fixed.

Status and next steps
- Ranjita asked Matthew Sunday afternoon to grant direct privileges to ENTITY again. He replied at 8:11 this morning that he's looking into it; he granted privs on the tables but thinks his script may not have had the full list. Going forward, it would help to have ENTITY covered in that script so a future GoldenGate reset doesn't break the ETL again.
- As a stopgap, GETREFDATE is temporarily pointed at our local tables so it compiles. That data isn't current, so once the grants are back we'll point it back to the replica, re-run E2 and validate.
- We decided against copying the tables over manually (one has ~10M rows), since that defeats the purpose of replication. The weekly backup is there if we need to restore.
- Longer term, I have a ticket to rework GETREFDATE. Until the Legacy data migration is done, though, the ALS tables won't have the right data, so we'll depend on GoldenGate until then.

Also this week
- Leading-zeros fix and other open tickets – targeting today
- Aiming for one ETL dry run in production
- Working session today on which tables need seeding
- Moving ahead with the DDL script deployment

Screenshots of the LA grants and our chat with Matthew are attached.

A big thank-you to Ranjita for jumping on this over the weekend, tracking down the cause, and calling me right away.

Best,
Santosh & Ranjita

'''
