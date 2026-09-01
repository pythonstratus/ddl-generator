-- =====================================================================
-- RUN_ALL.sql
--
-- THE WHOLE DRY RUN, IN ONE SQL*PLUS SCRIPT.
--
-- =====================================================================
-- *** THIS SCRIPT CLEARS THE SANDBOX BEFORE IT DEPLOYS ***
--
--   Step 4 drops EVERY object owned by ENTITYDEV, with PURGE. There is
--   no recycle bin recovery and there is no confirmation prompt.
--
--   That is deliberate. A dry run has to start from a clean schema to
--   mean anything, and clearing ENTITYDEV has been authorised. It also
--   makes the run repeatable -- each execution starts from the same
--   baseline.
--
--   ENTITYDEV must therefore be a schema you are content to lose on
--   every run. It cannot touch any other schema: both the teardown and
--   the deployment refuse to run unless the connected user is exactly
--   ENTITYDEV.
-- =====================================================================
--
-- ---------------------------------------------------------------------
-- BEFORE YOU START -- CHECK THE FOLDER
--
--   This script must sit NEXT TO the ENTITY_DDLs_DryRun folder:
--
--       ENTITY_DryRun_Package/
--         RUN_ALL.sql                       <- this file
--         01_entitydev_preflight_checks.sql
--         02_entitydev_prepare_account.sql
--         99_entitydev_teardown.sql
--         ENTITY_DDLs_DryRun/               <- MUST BE PRESENT
--           master_run_entitydev.sql
--           sequences/ functions/ tables/ indexes/
--           procedures/ views/ synonyms/
--
--   If ENTITY_DDLs_DryRun is missing you get SP2-0310 at the very last
--   step, after everything else has succeeded, and no DDL runs.
--
-- ---------------------------------------------------------------------
-- HOW TO RUN
--
--     cd <the folder containing this file>
--     sqlplus / as sysdba @RUN_ALL.sql
--
--   Start connected as SYS. The script switches to ENTITYDEV itself
--   partway through. That switch is what keeps the DROP statements
--   confined to the sandbox -- do not skip it by running the DDL as SYS.
--
--   Allow time. Clearing ~1,100 objects and then deploying 137 tables
--   is minutes of real work, not seconds. Let it finish.
--
-- ---------------------------------------------------------------------
-- PASSWORDS
--
--   This script does NOT set any password. It asks for the existing
--   ENTITYDEV password so it can reconnect.
--
--   To change it, at the SQL> prompt as SYSDBA, before running:
--
--       SQL> PASSWORD ENTITYDEV
--
--   Use that rather than ALTER USER ... IDENTIFIED BY: when an ALTER
--   USER fails, SQL*Plus echoes the statement back with the password
--   substituted in, putting it in the log in clear text.
--
-- ---------------------------------------------------------------------
-- LOGS TO SEND BACK  (four now)
--
--     entitydev_preflight.log
--     entitydev_prepare.log
--     entitydev_teardown.log      <- inventory of what was removed
--     entitydev_dryrun_<timestamp>.log
--
--   If it stops, send the exact ORA- or SP2- number and the step.
-- =====================================================================

SET ECHO OFF
SET VERIFY OFF
SET FEEDBACK ON
SET LINESIZE 200
SET PAGESIZE 200
SET SERVEROUTPUT ON SIZE UNLIMITED
SET DEFINE ON

PROMPT
PROMPT =====================================================================
PROMPT  ENTITY DDL DRY RUN  --  ALL STEPS
PROMPT =====================================================================
PROMPT
PROMPT  NOTE: step 4 clears the ENTITYDEV sandbox completely before the
PROMPT  deployment runs. This is intended and authorised.
PROMPT
PROMPT  You are currently connected as:
SELECT USER AS connected_as,
       NVL(SYS_CONTEXT('USERENV','CON_NAME'),'NON-CDB') AS container
FROM   dual;

PROMPT
PROMPT  This must be started as SYS. If the above does not say SYS,
PROMPT  stop now and reconnect.
PROMPT

ACCEPT svc CHAR PROMPT 'Service / TNS alias for ENTITYDEV [ALSDEV]: ' DEFAULT 'ALSDEV'


-- =====================================================================
-- STEP 1 : Locate ENTITYDEV
--
-- On a multitenant database "/ as sysdba" may land in CDB$ROOT, where
-- DBA_USERS does not show users that live in a PDB.
-- =====================================================================
WHENEVER SQLERROR EXIT FAILURE

PROMPT
PROMPT ===== STEP 1 of 5 : Locating ENTITYDEV =====

DECLARE
    v_count PLS_INTEGER;
    v_pdb   VARCHAR2(128);
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM   dba_users WHERE username = 'ENTITYDEV';

    IF v_count = 1 THEN
        DBMS_OUTPUT.PUT_LINE('   OK - found in container ' ||
            NVL(SYS_CONTEXT('USERENV','CON_NAME'),'(non-CDB)'));
        RETURN;
    END IF;

    DBMS_OUTPUT.PUT_LINE('   Not in this container - searching PDBs...');

    BEGIN
        SELECT p.name INTO v_pdb
        FROM   cdb_users u
        JOIN   v$pdbs p ON p.con_id = u.con_id
        WHERE  u.username = 'ENTITYDEV'
        AND    ROWNUM = 1;
    EXCEPTION
        WHEN OTHERS THEN
            RAISE_APPLICATION_ERROR(-20001,
                'ABORTED: could not find ENTITYDEV in this container or ' ||
                'in any PDB. Nothing has been changed.');
    END;

    EXECUTE IMMEDIATE 'ALTER SESSION SET CONTAINER = ' || v_pdb;
    DBMS_OUTPUT.PUT_LINE('   Switched to PDB: ' || v_pdb);

    SELECT COUNT(*) INTO v_count
    FROM   dba_users WHERE username = 'ENTITYDEV';

    IF v_count != 1 THEN
        RAISE_APPLICATION_ERROR(-20002,
            'ABORTED: switched to ' || v_pdb || ' but ENTITYDEV is still ' ||
            'not visible. Nothing has been changed.');
    END IF;

    DBMS_OUTPUT.PUT_LINE('   OK - container switch verified.');
END;
/


-- =====================================================================
-- STEP 2 : Pre-flight checks  (read-only, as SYS)
--
-- Runs BEFORE the teardown, so the pre-flight log records what the
-- schema held. That log is the evidence of the starting state.
-- =====================================================================
PROMPT
PROMPT ===== STEP 2 of 5 : Pre-flight checks (read-only) =====
PROMPT

@@01_entitydev_preflight_checks.sql


-- =====================================================================
-- STEP 3 : Unlock and narrow privileges  (as SYS)
--
-- Must run before step 4, which needs to connect as ENTITYDEV.
-- =====================================================================
PROMPT
PROMPT ===== STEP 3 of 5 : Unlocking and adjusting privileges =====
PROMPT

@@02_entitydev_prepare_account.sql


-- =====================================================================
-- STEP 4 : Clear the sandbox  (as ENTITYDEV)
--
-- Reconnects first, because the teardown refuses to run as anyone other
-- than ENTITYDEV. That check is what confines the drops to the sandbox.
-- =====================================================================
PROMPT
PROMPT ===== STEP 4 of 5 : Clearing the ENTITYDEV sandbox =====
PROMPT
PROMPT  The EXISTING ENTITYDEV password is needed to reconnect.
PROMPT  Typing is hidden. Nothing is changed or stored.
PROMPT
PROMPT  If you do not know it, press Ctrl-C, then at the SQL> prompt:
PROMPT      PASSWORD ENTITYDEV
PROMPT  ...and run this script again.
PROMPT

ACCEPT entpw CHAR PROMPT 'Existing password for ENTITYDEV: ' HIDE

PROMPT
PROMPT  Reconnecting as ENTITYDEV...
PROMPT

WHENEVER SQLERROR EXIT FAILURE

CONNECT ENTITYDEV/&entpw@&svc

UNDEFINE entpw

PROMPT
PROMPT  Connected as:
SELECT USER AS connected_as,
       NVL(SYS_CONTEXT('USERENV','CON_NAME'),'NON-CDB') AS container
FROM   dual;

PROMPT
PROMPT  Clearing the schema. This drops every object owned by ENTITYDEV
PROMPT  and can take several minutes. Please let it finish.
PROMPT

@@99_entitydev_teardown.sql


-- ---------------------------------------------------------------------
-- Confirm the teardown actually finished before deploying.
--
-- The deployment has its own empty-schema guard, but a failure here
-- gives a clearer reason than "schema is not empty".
-- ---------------------------------------------------------------------
PROMPT
PROMPT >> Confirming the sandbox is now empty...

WHENEVER SQLERROR EXIT FAILURE

DECLARE
    v_count PLS_INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_objects;

    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20003,
            'ABORTED: teardown finished but ' || v_count || ' object(s) ' ||
            'remain. The deployment has not run. Send entitydev_teardown.log ' ||
            'to the development team -- do not re-run, a repeat pass will ' ||
            'not clear an object that has already failed.');
    END IF;

    DBMS_OUTPUT.PUT_LINE('   OK - sandbox is empty.');
END;
/


-- =====================================================================
-- STEP 5 : The dry run  (still as ENTITYDEV)
-- =====================================================================
PROMPT
PROMPT ===== STEP 5 of 5 : Running the DDL dry run =====
PROMPT
PROMPT  The deployment now runs. It reports errors as it goes -- that is
PROMPT  by design, so a single pass captures every problem. A run of
PROMPT  ORA-00942 messages during the table step is expected and normal.
PROMPT
PROMPT  Please let it finish.
PROMPT
PROMPT  If the next line is SP2-0310, the ENTITY_DDLs_DryRun folder is
PROMPT  missing from this directory and NO DDL HAS RUN. See the note at
PROMPT  the top of this file.
PROMPT

WHENEVER SQLERROR CONTINUE

@@ENTITY_DDLs_DryRun/master_run_entitydev.sql

-- master_run_entitydev.sql ends with EXIT, so nothing after this line
-- is reached. The closing summary and log filename are printed there.
