ENTITY DDL DRY RUN -- WORKING TOOLKIT (yours, not the DBA's)

UNZIP INTO A CLEAN FOLDER
  The build picks up companion scripts from the CURRENT directory
  first. If an older RUN_ALL.sql or 99_*.sql is lying around, that is
  what gets shipped. Start clean, or overwrite everything.

  The build now prints the path of each file it picked up, and refuses
  to assemble if RUN_ALL.sql is missing the teardown step.

BEFORE SENDING
  Edit the [NAME] and [DATE] lines near the top of
  99_entitydev_teardown.sql. It runs unattended inside RUN_ALL.sql, so
  those two lines are the only audit record of who authorised clearing
  the schema.

BUILD
    cd ENTITY_Toolkit
    cp -r /path/to/ENTITY_DDLs_Prod .
    ./build_dryrun_package.sh ENTITY_DDLs_Prod

  Produces ENTITY_DryRun_Package.zip -- send the whole zip.

VERIFY BEFORE SENDING
    cd ENTITY_DryRun_Package
    grep -c "@@99_entitydev_teardown" RUN_ALL.sql                     -> 1
    grep -c "DBMS_SCHEDULER.DROP_JOB" 99_entitydev_teardown.sql       -> 1
    grep -c "@@functions" ENTITY_DDLs_DryRun/master_run_entitydev.sql -> 1

THE DBA RUNS ONE COMMAND
    cd ENTITY_DryRun_Package
    sqlplus / as sysdba @RUN_ALL.sql

  Five steps: locate, pre-flight, prepare, CLEAR THE SANDBOX, deploy.
  Returns four logs, including entitydev_teardown.log.

  RUN_ALL IS DESTRUCTIVE. Step 4 drops every object in ENTITYDEV with
  PURGE and no prompt, so every run starts from a clean baseline.

IF YOU EVER NEED A DIFFERENT SANDBOX SCHEMA
  SANDBOX_USER=<name> ./build_dryrun_package.sh ENTITY_DDLs_Prod
  The account must exist first -- model it on ENTITYDEV: default
  tablespace ALS, temporary ALS_TEMP, profile APP_SCHEMA_PROFILE,
  schema-scoped privileges only (no CREATE ANY).

PASSWORDS
  No script sets a password. Use SQL> PASSWORD <user> as SYSDBA.

STEP ORDER
  functions/ runs BEFORE tables/ -- the table and index DDL calls
  schema-qualified functions (e.g. ENTITY.SWITCHROID).

Run from WSL or Git Bash, not cmd.exe. Requires GNU sed.
