ENTITY DDL DRY RUN -- WORKING TOOLKIT (yours, not the DBA's)

    unzip ENTITY_Toolkit.zip
    cd ENTITY_Toolkit
    cp -r /path/to/ENTITY_DDLs_Prod .
    ./build_dryrun_package.sh ENTITY_DDLs_Prod

Produces ENTITY_DryRun_Package.zip -- THAT is what goes to the DBA.

She can run it either way; both are documented in the RUN_ORDER.txt
inside that package:

    ./run_entity_dryrun.sh              (shell, one command)
    sqlplus / as sysdba @RUN_ALL.sql    (SQL*Plus, one script)

You never open or copy the numbered .sql files yourself. They just need
to sit in this folder; the build bundles them automatically.

Run from WSL or Git Bash, not cmd.exe. Requires GNU sed.

KEEP the generated *_tablespaces_expected.txt -- after the build strips
the tablespace clauses it is the only record of what Production needs.
