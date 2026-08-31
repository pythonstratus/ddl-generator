Subject: ENTITY DDL dry run — one SQL script, fixed for multitenant

Hi Christina,

Thanks for spotting the multitenant problem — that was a real bug on my side and it's now fixed. I've also put together a single SQL script, since you'd rather not use the shell script.

The package is attached. Unzip it on the database server, then:

    cd ENTITY_DryRun_Package
    sqlplus / as sysdba @RUN_ALL.sql

That's the whole thing. It locates ENTITYDEV (including working out which PDB it's in and switching containers), runs read-only checks, prepares the account, then reconnects as ENTITYDEV and runs the DDL.

It will ask you two things: the service name for ENTITYDEV, and a new password for that account. The password is expired — pick anything you like, it's only used for this sandbox and it isn't written to any log.

THREE THINGS WORTH KNOWING

Please start as SYS and let the script do the reconnect itself, rather than running the whole thing as SYS. The DDL contains DROP TABLE statements, and connecting as ENTITYDEV is what keeps those confined to the sandbox. The script will stop you if the wrong account is connected, but I'd rather you didn't have to rely on that.

You will see a lot of errors scroll past, particularly ORA-00942 during the table step. That's expected. The script deliberately continues past failures so we capture everything in one pass rather than stopping at the first. Please let it run to the end.

If it stops early, it will say why. Please send me the output rather than working around it — the stops are deliberate and each one means something needs checking on our side first.

WHAT TO SEND BACK

    entitydev_preflight.log
    entitydev_prepare.log
    entitydev_dryrun_<timestamp>.log

The shell script is still in the package (run_entity_dryrun.sh) if you change your mind, and RUN_ORDER.txt covers both approaches plus a step-by-step version if you'd rather see each stage separately.

Please leave the sandbox objects in place afterwards — I'll come back to you with a cleanup step once I've reviewed the results.

Happy to get on a call if that's easier.

Thanks,
Santosh
