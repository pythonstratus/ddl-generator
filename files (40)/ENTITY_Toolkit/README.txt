ENTITY DDL DRY RUN -- WORKING TOOLKIT (yours, not the DBA's)

BEFORE SENDING
  Edit the [NAME] and [DATE] lines near the top of
  99_entitydev_teardown.sql. It now runs unattended as part of
  RUN_ALL.sql, so those two lines are the audit record of who
  authorised clearing the schema.

BUILD
    unzip ENTITY_Toolkit.zip
    cd ENTITY_Toolkit
    cp -r /path/to/ENTITY_DDLs_Prod .
    ./build_dryrun_package.sh ENTITY_DDLs_Prod

  Produces ENTITY_DryRun_Package.zip -- send the whole zip.

THE DBA RUNS ONE COMMAND
    cd ENTITY_DryRun_Package
    sqlplus / as sysdba @RUN_ALL.sql

  Five steps: locate, pre-flight, prepare, CLEAR THE SANDBOX, deploy.
  Asks only for the service name and the ENTITYDEV password.
  Returns four logs.

  RUN_ALL IS NOW DESTRUCTIVE. Step 4 drops every object in ENTITYDEV
  with PURGE and no prompt. Every run starts from a clean baseline,
  which is what makes the result meaningful and the run repeatable.

TARGETING A DIFFERENT SCHEMA
  SANDBOX_USER=ENTITYDRYRUN ./build_dryrun_package.sh ENTITY_DDLs_Prod
  Retargets the generated driver and all companion scripts, including
  the teardown guard. 00_create_entitydryrun_user.sql creates that
  account if ever needed.

PASSWORDS
  No script sets a password. Use SQL> PASSWORD <user> as SYSDBA.

STEP ORDER
  functions/ runs BEFORE tables/ -- the table and index DDL calls
  schema-qualified functions (e.g. ENTITY.SWITCHROID).

Run from WSL or Git Bash, not cmd.exe. Requires GNU sed.
