-- =====================================================================
-- 00_create_entitydryrun_user.sql
--
-- PURPOSE : Create a purpose-built, genuinely empty schema for the
--           ENTITY DDL dry run.
--
-- RUN AS  : SYS
-- TARGET  : container ALSDEV on the Exadata DEV instance
--
--     sqlplus / as sysdba @00_create_entitydryrun_user.sql
--
-- ---------------------------------------------------------------------
-- WHY THIS IS NEEDED
--
--   The pre-flight of 31-AUG-2026 found ENTITYDEV holding 1,115 objects
--   (460 tables, 276 indexes, 118 functions) and roughly 157 GB across
--   the ALS and ENTITY tablespaces. It is somebody's active working
--   copy of ENTITY, not an empty sandbox.
--
--   The deployment package opens each table block with
--   DROP TABLE ... CASCADE CONSTRAINTS. Running it there would destroy
--   that work. Do NOT clear ENTITYDEV to make room for the dry run.
--
--   This script creates a separate, empty account instead. ENTITYDEV is
--   left completely untouched.
--
-- ---------------------------------------------------------------------
-- HOW THIS ACCOUNT IS CONFIGURED
--
--   Settings are copied from what ENTITYDEV actually has today (per the
--   pre-flight log), not from phase2.sql, which has drifted from
--   reality in several places.
--
--     Default tablespace   ALS                 (as ENTITYDEV)
--     Temporary tablespace ALS_TEMP            (as ENTITYDEV, verified
--                                               CONTENTS = TEMPORARY)
--     Profile              APP_SCHEMA_PROFILE  (as ENTITYDEV)
--     Quota                10G on ALS
--
--   10G is deliberate rather than UNLIMITED. The dry run creates
--   structure with no data -- 137 tables at a 64K initial extent is
--   under 100 MB -- so 10G is generous, and a bounded quota means a
--   runaway cannot fill a shared tablespace.
--
--   Privileges are the schema-scoped set only. No CREATE ANY PROCEDURE,
--   CREATE ANY TRIGGER or CREATE ANY TYPE: those would let this account
--   create objects inside other schemas, including the real ENTITY.
-- =====================================================================

SET ECHO OFF
SET FEEDBACK ON
SET VERIFY OFF
SET LINESIZE 200
SET PAGESIZE 200
SET SERVEROUTPUT ON SIZE UNLIMITED

COLUMN username       FORMAT A22
COLUMN account_status FORMAT A20
COLUMN privilege      FORMAT A35
COLUMN granted_role   FORMAT A30
COLUMN container_name FORMAT A30

SPOOL create_entitydryrun.log

PROMPT
PROMPT =====================================================================
PROMPT  CREATE ENTITYDRYRUN -- empty schema for the ENTITY DDL dry run
PROMPT =====================================================================

PROMPT
PROMPT ===== Connected container (expect ALSDEV) =====
SELECT NVL(SYS_CONTEXT('USERENV','CON_NAME'),'NON-CDB') AS container_name
FROM   dual;

-- If the above is not ALSDEV, uncomment and set this:
-- ALTER SESSION SET CONTAINER = ALSDEV;


-- ---------------------------------------------------------------------
-- Guard : refuse to touch ENTITYDEV, and refuse to clobber an existing
--         ENTITYDRYRUN that already holds objects.
-- ---------------------------------------------------------------------
WHENEVER SQLERROR EXIT FAILURE

PROMPT
PROMPT ===== Safety checks =====

DECLARE
    v_exists PLS_INTEGER;
    v_objs   PLS_INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_exists
    FROM   dba_users WHERE username = 'ENTITYDRYRUN';

    IF v_exists = 0 THEN
        DBMS_OUTPUT.PUT_LINE('   ENTITYDRYRUN does not exist - will be created.');
        RETURN;
    END IF;

    SELECT COUNT(*) INTO v_objs
    FROM   dba_objects WHERE owner = 'ENTITYDRYRUN';

    IF v_objs > 0 THEN
        RAISE_APPLICATION_ERROR(-20010,
            'ABORTED: ENTITYDRYRUN already exists and holds ' || v_objs ||
            ' object(s). Confirm they are disposable before continuing. ' ||
            'Nothing has been changed.');
    END IF;

    DBMS_OUTPUT.PUT_LINE('   ENTITYDRYRUN exists and is empty - privileges '
                         || 'will be refreshed.');
END;
/

WHENEVER SQLERROR CONTINUE


-- ---------------------------------------------------------------------
-- Create the account
--
-- The password is NOT set here. Set it separately, so it cannot be
-- echoed into this log if the statement fails:
--
--     SQL> PASSWORD ENTITYDRYRUN
--
-- If the account already exists this CREATE fails with ORA-01920,
-- which is harmless -- the grants below still apply.
-- ---------------------------------------------------------------------
PROMPT
PROMPT ===== Creating the account =====
PROMPT (ORA-01920 here just means it already exists -- that is fine)

CREATE USER ENTITYDRYRUN
    IDENTIFIED BY "&&throwaway_placeholder_not_used"
    DEFAULT TABLESPACE ALS
    TEMPORARY TABLESPACE ALS_TEMP
    PROFILE APP_SCHEMA_PROFILE
    QUOTA 10G ON ALS
    ACCOUNT LOCK;

UNDEFINE throwaway_placeholder_not_used


-- ---------------------------------------------------------------------
-- Privileges -- schema-scoped only
-- ---------------------------------------------------------------------
PROMPT
PROMPT ===== Granting privileges =====

GRANT CREATE SESSION            TO ENTITYDRYRUN;
GRANT CREATE TABLE              TO ENTITYDRYRUN;
GRANT CREATE VIEW               TO ENTITYDRYRUN;
GRANT CREATE SEQUENCE           TO ENTITYDRYRUN;
GRANT CREATE SYNONYM            TO ENTITYDRYRUN;
GRANT CREATE PROCEDURE          TO ENTITYDRYRUN;
GRANT CREATE TRIGGER            TO ENTITYDRYRUN;
GRANT CREATE TYPE               TO ENTITYDRYRUN;
GRANT CREATE MATERIALIZED VIEW  TO ENTITYDRYRUN;


-- ---------------------------------------------------------------------
-- Read-only roles
--
-- ENTITYDEV holds these as default roles. The ENTITY DDL creates
-- synonyms pointing at DIAL and ALS_LEGACY_REPLICA, so without them
-- those objects may compile INVALID for reasons unrelated to the DDL.
--
-- Comment these out if your site would rather not grant them.
-- ---------------------------------------------------------------------
PROMPT
PROMPT ===== Granting read-only legacy roles =====

GRANT ALS_LEGACY_RO  TO ENTITYDRYRUN;
GRANT DIAL_LEGACY_RO TO ENTITYDRYRUN;


-- ---------------------------------------------------------------------
-- Unlock, ready for a password to be set
-- ---------------------------------------------------------------------
PROMPT
PROMPT ===== Unlocking =====

ALTER USER ENTITYDRYRUN ACCOUNT UNLOCK;


-- ---------------------------------------------------------------------
-- Verify
-- ---------------------------------------------------------------------
PROMPT
PROMPT ===== Account (expect ALS / ALS_TEMP / APP_SCHEMA_PROFILE) =====
SELECT username, account_status, default_tablespace,
       temporary_tablespace, profile
FROM   dba_users
WHERE  username = 'ENTITYDRYRUN';

PROMPT
PROMPT ===== Privileges (expect 9, none containing ANY) =====
SELECT privilege FROM dba_sys_privs
WHERE  grantee = 'ENTITYDRYRUN' ORDER BY privilege;

PROMPT
PROMPT ===== Roles =====
SELECT granted_role, default_role FROM dba_role_privs
WHERE  grantee = 'ENTITYDRYRUN' ORDER BY granted_role;

PROMPT
PROMPT ===== Quota (expect ALS, 10 GB) =====
SELECT tablespace_name,
       CASE WHEN max_bytes = -1 THEN 'UNLIMITED'
            ELSE TO_CHAR(ROUND(max_bytes/1024/1024/1024,2)) || ' GB'
       END AS quota
FROM   dba_ts_quotas WHERE username = 'ENTITYDRYRUN';

PROMPT
PROMPT ===== Object count (MUST be zero) =====
SELECT COUNT(*) AS object_count FROM dba_objects
WHERE  owner = 'ENTITYDRYRUN';

PROMPT
PROMPT =====================================================================
PROMPT  DONE
PROMPT
PROMPT  Next, set a password:
PROMPT      SQL> PASSWORD ENTITYDRYRUN
PROMPT
PROMPT  Then run the dry run package against ENTITYDRYRUN.
PROMPT  ENTITYDEV has not been touched by this script.
PROMPT =====================================================================

SPOOL OFF
