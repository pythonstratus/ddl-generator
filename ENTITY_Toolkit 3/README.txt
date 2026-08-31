ENTITY DDL DRY RUN -- WORKING TOOLKIT (yours, not the DBA's)

    unzip ENTITY_Toolkit.zip
    cd ENTITY_Toolkit
    cp -r /path/to/ENTITY_DDLs_Prod .
    ./build_dryrun_package.sh ENTITY_DDLs_Prod

Produces ENTITY_DryRun_Package.zip -- THAT is what goes to the DBA.

She can run it either way; both are in RUN_ORDER.txt inside that package:
    ./run_entity_dryrun.sh              (shell, one command)
    sqlplus / as sysdba @RUN_ALL.sql    (SQL*Plus, one script)

NOTE ON STEP ORDER
  functions/ runs BEFORE tables/. The table and index DDL calls
  schema-qualified functions (e.g. ENTITY.SWITCHROID) for virtual
  columns or function-based indexes, so they must exist first. The
  original master_run.sql omitted functions entirely -- in Production
  that is invisible because they already exist, but in an empty
  sandbox the tables would fail without them.

Run from WSL or Git Bash, not cmd.exe. Requires GNU sed.

KEEP the generated *_tablespaces_expected.txt -- after the build strips
the tablespace clauses it is the only record of what Production needs.
