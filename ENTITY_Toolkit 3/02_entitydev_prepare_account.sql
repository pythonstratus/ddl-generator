-- =====================================================================
-- 02_entitydev_prepare_account.sql
--
-- PURPOSE : Prepare the ENTITYDEV sandbox account for the dry run.
--           Run AFTER 01_entitydev_preflight_checks.sql.
--
-- RUN AS  : SYS
--
--     sqlplus / as sysdba @02_entitydev_prepare_account.sql
--
-- This script decides for itself what needs doing. There is nothing to
-- uncomment and no judgement calls. It will:
--
--     1. Confirm ENTITYDEV is visible in this container
--     2. Unlock the account and set a password you choose
--     3. Narrow CREATE ANY PROCEDURE / CREATE ANY TRIGGER to the
--        schema-scoped equivalents, if they are present
--     4. Report the resulting privileges
--
-- It affects ENTITYDEV only. It creates nothing and drops nothing, and
-- it is safe to run more than once.
--
-- The password prompt hides typing, and command echo is off throughout,
-- so the password is not written to the log file.
--
-- ---------------------------------------------------------------------
-- MULTITENANT / PLUGGABLE DATABASES
--
--   "/ as sysdba" connects to CDB$ROOT, not to the PDB that holds
--   ENTITYDEV. The container is reported below. If it shows CDB$ROOT
--   and the account lives in a PDB, find it with:
--
--       SELECT p.name FROM cdb_users u
--       JOIN v$pdbs p ON p.con_id = u.con_id
--       WHERE u.username = 'ENTITYDEV';
--
--   ...then uncomment the ALTER SESSION line below and set the name.
-- =====================================================================

SET ECHO OFF
SET FEEDBACK ON
SET VERIFY OFF
SET LINESIZE 200
SET PAGESIZE 200
SET SERVEROUTPUT ON SIZE UNLIMITED

COLUMN container_name FORMAT A34
COLUMN username       FORMAT A22
COLUMN account_status FORMAT A20
COLUMN privilege      FORMAT A35

SPOOL entitydev_prepare.log

PROMPT
PROMPT =====================================================================
PROMPT  ENTITYDEV ACCOUNT PREPARATION
PROMPT =====================================================================

-- Uncomment and set this if the container reported below is wrong.
-- ALTER SESSION SET CONTAINER = <your_pdb_name>;

PROMPT
PROMPT ===== Connected container =====
SELECT NVL(SYS_CONTEXT('USERENV','CON_NAME'), 'NON-CDB / not applicable')
       AS container_name
FROM   dual;


-- ---------------------------------------------------------------------
-- Guard : the account must be visible from here
-- ---------------------------------------------------------------------
WHENEVER SQLERROR EXIT FAILURE

PROMPT
PROMPT ===== Verifying ENTITYDEV is visible =====

DECLARE
    v_count PLS_INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM   dba_users
    WHERE  username = 'ENTITYDEV';

    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20001,
            'ABORTED: ENTITYDEV is not visible in container ' ||
            NVL(SYS_CONTEXT('USERENV','CON_NAME'),'(non-CDB)') ||
            '. If this is a multitenant database, set the container at ' ||
            'the top of this script. Nothing has been changed.');
    END IF;

    DBMS_OUTPUT.PUT_LINE('   OK - ENTITYDEV found.');
END;
/

WHENEVER SQLERROR CONTINUE


-- ---------------------------------------------------------------------
-- Unlock and set the password
--
-- Pick anything; it is only used to connect for the dry run. The
-- account was last used in February and has not been maintained since
-- March, so it is very likely expired.
-- ---------------------------------------------------------------------
PROMPT
PROMPT ===== Setting the ENTITYDEV password =====
PROMPT (typing is hidden, and the value is not written to the log)
PROMPT

ACCEPT new_pw CHAR PROMPT 'New password for ENTITYDEV: ' HIDE

ALTER USER ENTITYDEV ACCOUNT UNLOCK;
ALTER USER ENTITYDEV IDENTIFIED BY "&new_pw";

-- new_pw is deliberately left defined. RUN_ALL.sql reuses it to connect
-- as ENTITYDEV for the dry run, so you are only asked once. It is a
-- session variable and disappears when SQL*Plus exits.

PROMPT
PROMPT >> Account unlocked and password set.


-- ---------------------------------------------------------------------
-- Narrow the ANY privileges
--
-- phase2.sql granted CREATE ANY PROCEDURE and CREATE ANY TRIGGER. ANY
-- means any schema, including ENTITY, which exists on this instance.
-- The DDL is schema-qualified, so a reference that escaped the rename
-- would silently succeed against the real schema.
--
-- The scoped privileges are fully sufficient for building objects in
-- ENTITYDEV's own schema, so nothing legitimate is lost. A missed
-- reference then fails visibly with ORA-01031 instead.
--
-- Conditional, so re-running is harmless.
-- ---------------------------------------------------------------------
PROMPT
PROMPT ===== Narrowing ANY privileges to the sandbox schema =====

DECLARE
    PROCEDURE swap(p_any IN VARCHAR2, p_scoped IN VARCHAR2) IS
        v_has PLS_INTEGER;
    BEGIN
        SELECT COUNT(*) INTO v_has
        FROM   dba_sys_privs
        WHERE  grantee = 'ENTITYDEV' AND privilege = p_any;

        IF v_has > 0 THEN
            EXECUTE IMMEDIATE 'REVOKE ' || p_any || ' FROM ENTITYDEV';
            DBMS_OUTPUT.PUT_LINE('   Revoked ' || p_any);
        ELSE
            DBMS_OUTPUT.PUT_LINE('   ' || p_any || ' not held - nothing to revoke');
        END IF;

        SELECT COUNT(*) INTO v_has
        FROM   dba_sys_privs
        WHERE  grantee = 'ENTITYDEV' AND privilege = p_scoped;

        IF v_has = 0 THEN
            EXECUTE IMMEDIATE 'GRANT ' || p_scoped || ' TO ENTITYDEV';
            DBMS_OUTPUT.PUT_LINE('   Granted ' || p_scoped);
        ELSE
            DBMS_OUTPUT.PUT_LINE('   ' || p_scoped || ' already held');
        END IF;
    END swap;
BEGIN
    swap('CREATE ANY PROCEDURE', 'CREATE PROCEDURE');
    swap('CREATE ANY TRIGGER',   'CREATE TRIGGER');
END;
/


-- ---------------------------------------------------------------------
-- Result
-- ---------------------------------------------------------------------
PROMPT
PROMPT ===== Account status (expect OPEN) =====
SELECT username, account_status, expiry_date
FROM   dba_users
WHERE  username = 'ENTITYDEV';

PROMPT
PROMPT ===== Privileges now held by ENTITYDEV =====
PROMPT Expect: CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SEQUENCE,
PROMPT         CREATE SYNONYM, CREATE PROCEDURE, CREATE TRIGGER
PROMPT Expect NOT to see: CREATE ANY PROCEDURE, CREATE ANY TRIGGER
SELECT privilege
FROM   dba_sys_privs
WHERE  grantee = 'ENTITYDEV'
ORDER  BY privilege;

PROMPT
PROMPT ===== Tablespace quota (expect a row for ALS) =====
SELECT tablespace_name,
       CASE WHEN max_bytes = -1 THEN 'UNLIMITED'
            ELSE TO_CHAR(ROUND(max_bytes/1024/1024/1024, 2)) || ' GB'
       END AS quota
FROM   dba_ts_quotas
WHERE  username = 'ENTITYDEV';

PROMPT
PROMPT =====================================================================
PROMPT  PREPARATION COMPLETE
PROMPT
PROMPT  Next: connect as ENTITYDEV -- NOT as SYS -- and run the dry run:
PROMPT
PROMPT      cd ENTITY_DDLs_DryRun
PROMPT      sqlplus ENTITYDEV@<service> @master_run_entitydev.sql
PROMPT
PROMPT  Please send back entitydev_prepare.log with the other logs.
PROMPT =====================================================================

SPOOL OFF


-- =====================================================================
-- AFTERWARDS (optional)
--
-- Restores the privileges phase2.sql originally granted, leaving the
-- sandbox as it was found. Run only once the dry run and teardown are
-- complete.
--
--   REVOKE CREATE PROCEDURE FROM ENTITYDEV;
--   REVOKE CREATE TRIGGER   FROM ENTITYDEV;
--   GRANT  CREATE ANY PROCEDURE TO ENTITYDEV;
--   GRANT  CREATE ANY TRIGGER   TO ENTITYDEV;
-- =====================================================================
