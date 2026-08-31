ENTITY DDL DRY RUN -- WORKING TOOLKIT (yours, not the DBA's)

    unzip ENTITY_Toolkit.zip
    cd ENTITY_Toolkit
    cp -r /path/to/ENTITY_DDLs_Prod .
    ./build_dryrun_package.sh ENTITY_DDLs_Prod

Produces ENTITY_DryRun_Package.zip -- SEND THE WHOLE ZIP.

  Sending only the loose .sql files fails at the very last step with
  SP2-0310 "unable to open ENTITY_DDLs_DryRun/master_run_entitydev.sql"
  after everything else has succeeded, and no DDL runs at all.

PASSWORDS
  No script sets a password any more. When ALTER USER ... IDENTIFIED BY
  fails, SQL*Plus echoes the statement with the password substituted in,
  putting it in the log in clear text. RUN_ALL.sql asks only for the
  existing password, to reconnect.

  To change it: SQL> PASSWORD ENTITYDEV   (as SYSDBA, before running)

STEP ORDER
  functions/ runs BEFORE tables/. The table and index DDL calls
  schema-qualified functions (e.g. ENTITY.SWITCHROID), so they must
  exist first. The original master_run.sql omitted functions entirely.

Run from WSL or Git Bash, not cmd.exe. Requires GNU sed.

KEEP the generated *_tablespaces_expected.txt.
