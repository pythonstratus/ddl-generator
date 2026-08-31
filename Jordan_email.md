Subject: ENTITY dry run halted — ENTITYDEV holds 1,115 objects, requesting a clean schema

Hi Jordan,

Christina ran the pre-flight checks against ENTITYDEV this morning ahead of the ENTITY DDL dry run. They stopped the run before any DDL executed, and I think you'll want to see why.

ENTITYDEV currently holds 1,115 objects — 460 tables, 276 indexes, 118 functions, 57 views — and roughly 157 GB across the ALS and ENTITY tablespaces. It looks like an active working copy of ENTITY rather than an empty sandbox.

That matters because the production DDL package opens each table block with DROP TABLE ... CASCADE CONSTRAINTS, 137 of them. Our 137 table names are very likely a subset of the 460 already in there. Had the run gone ahead, it would have dropped those tables and whatever depends on them. The script has a guard that requires an empty schema, and that guard is what stopped it.

So, two questions:

1. Do you know who is using ENTITYDEV? I'd like to make sure nobody is caught out, and confirm nothing was disturbed — nothing was, but worth saying.

2. Rather than clearing it, could we get a separate empty account for this? I've attached 00_create_entitydryrun_user.sql, which creates ENTITYDRYRUN configured to match what ENTITYDEV actually has today — default tablespace ALS, temporary ALS_TEMP, profile APP_SCHEMA_PROFILE — with a bounded 10G quota and schema-scoped privileges only. It creates nothing else and does not touch ENTITYDEV.

We are explicitly not proposing to clear ENTITYDEV, and I've asked Christina not to run the teardown script against it.

One thing worth flagging separately: the pre-flight also showed ENTITYDEV's actual configuration differs from phase2.sql in several places — privileges, quotas and temporary tablespace all differ from what that script specifies. phase2.sql is not a reliable description of what is deployed, which is worth knowing before the production run.

Christina — the logs came through fine, thank you. Please hold off on any further runs until we have a clean account, and please do not run 99_entitydev_teardown.sql against ENTITYDEV. The guard message in that log suggests it as an option; that advice was written assuming an empty sandbox and does not apply here. My fault, and I've corrected it.

Thanks,
Santosh
