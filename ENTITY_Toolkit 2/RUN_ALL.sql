-- =====================================================================
-- RUN_ALL.sql
--
-- THE WHOLE DRY RUN, IN ONE SQL*PLUS SCRIPT.
--
-- Use this instead of run_entity_dryrun.sh if you would rather stay in
-- SQL*Plus. It does exactly the same work.
--
-- ---------------------------------------------------------------------
-- HOW TO RUN
--
--     cd <the folder containing this file>
--     sqlplus / as sysdba @RUN_ALL.sql
--
--   Start connected as SYS. The script switches to ENTITYDEV itself
--   partway through -- that switch is deliberate and must not be
--   skipped, because the DDL contains DROP TABLE statements and
--   connecting as ENTITYDEV is what confines them to the sandbox.
--
--   You will be asked for two things:
--     - the service / TNS alias for the ENTITYDEV connection
--     - a new password for ENTITYDEV (typing is hidden)
--
--   The password is asked for once and reused. It is not written to any
--   log file.
--
-- ---------------------------------------------------------------------
-- WHAT IT DOES
--
--   1. Locates ENTITYDEV, switching container automatically if this is
--      a multitenant database and the account lives in a PDB
--   2. Runs the read-only pre-flight checks        (as SYS)
--   3. Unlocks the account, sets the password, and narrows the
--      CREATE ANY privileges                       (as SYS)
--   4. Reconnects as ENTITYDEV and runs the DDL    (as ENTITYDEV)
--
--   Teardown is NOT included. It is run separately, and only after the
--   results have been reviewed.
--
-- ---------------------------------------------------------------------
-- LOGS TO SEND BACK
--
--     entitydev_preflight.log
--     entitydev_prepare.log
--     entitydev_dryrun_<timestamp>.log
--
-- ---------------------------------------------------------------------
-- IF SOMETHING STOPS IT
--
--   The stops are deliberate. Each one means something needs checking
--   before any DDL runs. Please send the logs rather than working
--   around it.
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
-- On a multitenant database, "/ as sysdba" lands in CDB$ROOT, where
-- DBA_USERS does not show users that live in a PDB. If the account is
-- not visible here, find which PDB holds it and switch to it.
-- =====================================================================
WHENEVER SQLERROR EXIT FAILURE

PROMPT
PROMPT ===== STEP 1 of 4 : Locating ENTITYDEV =====

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
                'in any PDB. Nothing has been changed. Please send this ' ||
                'output to the development team.');
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
-- Produces entitydev_preflight.log
-- =====================================================================
PROMPT
PROMPT ===== STEP 2 of 4 : Pre-flight checks (read-only) =====
PROMPT

@@01_entitydev_preflight_checks.sql


-- =====================================================================
-- STEP 3 : Prepare the account  (as SYS)
-- Prompts for the ENTITYDEV password. Produces entitydev_prepare.log
-- =====================================================================
PROMPT
PROMPT ===== STEP 3 of 4 : Preparing the ENTITYDEV account =====
PROMPT

@@02_entitydev_prepare_account.sql


-- =====================================================================
-- STEP 4 : The dry run  (as ENTITYDEV -- never as SYS)
--
-- Reconnects using the password set in step 3. Connecting through the
-- service name lands in the correct PDB automatically, so no container
-- switch is needed here.
--
-- The master script carries its own guards: it refuses to run unless
-- the connected user is ENTITYDEV and the schema is empty.
-- =====================================================================
PROMPT
PROMPT ===== STEP 4 of 4 : Running the DDL dry run =====
PROMPT
PROMPT  Reconnecting as ENTITYDEV...
PROMPT

CONNECT ENTITYDEV/"&new_pw"@&svc

UNDEFINE new_pw

PROMPT
PROMPT  Connected as:
SELECT USER AS connected_as,
       NVL(SYS_CONTEXT('USERENV','CON_NAME'),'NON-CDB') AS container
FROM   dual;

PROMPT
PROMPT  The deployment now runs. It reports errors as it goes -- that is
PROMPT  by design, so a single pass captures every problem. A run of
PROMPT  ORA-00942 messages during the table step is expected and normal.
PROMPT
PROMPT  Please let it finish.
PROMPT

WHENEVER SQLERROR CONTINUE

@@ENTITY_DDLs_DryRun/master_run_entitydev.sql

-- master_run_entitydev.sql ends with EXIT, so nothing after this line
-- is reached. The closing summary and log filename are printed there.
