-- =====================================================================
-- 99_entitydev_teardown.sql
--
-- PURPOSE : Drop every object in the ENTITYDEV sandbox, returning it to
--           an empty state after the dry run.
--
-- RUN AS  : ENTITYDEV
-- TARGET  : Exadata DEV  -  VL1SMTBORAM7M01.MCC.IRS.GOV:1701/ALSDEV
--
--     cd /path/to/ENTITY_DDLs_DryRun
--     sqlplus ENTITYDEV@ALSDEV @99_entitydev_teardown.sql
--
-- ---------------------------------------------------------------------
-- *** THIS SCRIPT IS DESTRUCTIVE ***
--
--   It drops ALL objects owned by the connected user, with PURGE (no
--   recycle bin recovery). It is intended for a disposable sandbox.
--
--   A hard guard stops the script unless the connected user is exactly
--   ENTITYDEV. It cannot run against ENTITY. Do not remove that guard.
--
--   Run this only when the dry run log has been captured and returned.
-- =====================================================================

SET ECHO ON
SET FEEDBACK ON
SET SERVEROUTPUT ON SIZE UNLIMITED
SET LINESIZE 200
SET PAGESIZE 50
SET DEFINE OFF

SPOOL entitydev_teardown.log


-- =====================================================================
-- SAFETY GUARD : ENTITYDEV only
-- =====================================================================
WHENEVER SQLERROR EXIT FAILURE

PROMPT
PROMPT >> Verifying connected user before dropping anything...

BEGIN
    IF USER != 'ENTITYDEV' THEN
        RAISE_APPLICATION_ERROR(
            -20001,
            'ABORTED: connected as ' || USER || '. This teardown script ' ||
            'runs only as ENTITYDEV. Nothing has been dropped.');
    END IF;
    DBMS_OUTPUT.PUT_LINE('   OK - connected as ' || USER);
END;
/

WHENEVER SQLERROR CONTINUE


-- =====================================================================
-- BEFORE : what is about to be dropped
-- =====================================================================
PROMPT
PROMPT >> Objects present before teardown:
SELECT object_type, COUNT(*) AS object_count
FROM   user_objects
GROUP  BY object_type
ORDER  BY object_type;


-- =====================================================================
-- DROP LOOP
-- ---------------------------------------------------------------------
-- Order matters. Synonyms and views first (they depend on tables),
-- then code, then materialized views, then tables, then sequences.
--
-- Notes:
--   - Indexes are not dropped directly; they go with their tables.
--   - PACKAGE BODY is not dropped directly; it goes with its PACKAGE.
--   - Dropping a MATERIALIZED VIEW also removes its container table, so
--     materialized views are handled before tables.
--   - Each drop is wrapped individually, so one failure does not stop
--     the rest.
-- =====================================================================
PROMPT
PROMPT >> Dropping objects...

DECLARE
    v_dropped  PLS_INTEGER := 0;
    v_failed   PLS_INTEGER := 0;
BEGIN
    FOR rec IN (
        SELECT object_name, object_type
        FROM   user_objects
        WHERE  object_type IN ('SYNONYM', 'VIEW', 'MATERIALIZED VIEW',
                               'PROCEDURE', 'FUNCTION', 'PACKAGE',
                               'TRIGGER', 'TABLE', 'SEQUENCE', 'TYPE')
        ORDER  BY DECODE(object_type, 'SYNONYM',           1,
                                      'VIEW',              2,
                                      'TRIGGER',           3,
                                      'PROCEDURE',         4,
                                      'FUNCTION',          5,
                                      'PACKAGE',           6,
                                      'MATERIALIZED VIEW', 7,
                                      'TABLE',             8,
                                      'SEQUENCE',          9,
                                      'TYPE',             10, 99)
    ) LOOP
        BEGIN
            IF rec.object_type = 'TABLE' THEN
                EXECUTE IMMEDIATE 'DROP TABLE "' || rec.object_name
                                  || '" CASCADE CONSTRAINTS PURGE';
            ELSIF rec.object_type = 'TYPE' THEN
                EXECUTE IMMEDIATE 'DROP TYPE "' || rec.object_name || '" FORCE';
            ELSE
                EXECUTE IMMEDIATE 'DROP ' || rec.object_type
                                  || ' "' || rec.object_name || '"';
            END IF;

            v_dropped := v_dropped + 1;
            DBMS_OUTPUT.PUT_LINE('  Dropped: ' || rec.object_type
                                 || ' - ' || rec.object_name);
        EXCEPTION
            WHEN OTHERS THEN
                -- ORA-04043 / ORA-00942 are normal here: the object was
                -- already removed as a dependency of an earlier drop.
                v_failed := v_failed + 1;
                DBMS_OUTPUT.PUT_LINE('  Skipped: ' || rec.object_type
                                     || ' - ' || rec.object_name
                                     || ' => ' || SQLERRM);
        END;
    END LOOP;

    DBMS_OUTPUT.PUT_LINE(' ');
    DBMS_OUTPUT.PUT_LINE('  Dropped: ' || v_dropped
                         || '   Skipped: ' || v_failed);
END;
/


-- =====================================================================
-- Empty the recycle bin
-- =====================================================================
PROMPT
PROMPT >> Purging recycle bin...
PURGE RECYCLEBIN;


-- =====================================================================
-- AFTER : confirm the schema is clean
-- =====================================================================
PROMPT
PROMPT >> Remaining objects (expected: no rows):
SELECT object_name, object_type, status
FROM   user_objects
ORDER  BY object_type, object_name;

PROMPT
PROMPT >> Remaining object count (expected: 0):
SELECT COUNT(*) AS remaining_objects FROM user_objects;

PROMPT
PROMPT >> Space still charged to ENTITYDEV (expected: 0 MB):
SELECT tablespace_name,
       ROUND(bytes/1024/1024, 2) AS used_mb
FROM   user_ts_quotas
ORDER  BY tablespace_name;

PROMPT
PROMPT ================================================================
PROMPT  TEARDOWN COMPLETE
PROMPT  If any objects remain above, report them rather than forcing
PROMPT  a second pass.
PROMPT ================================================================

SPOOL OFF
SET ECHO OFF
EXIT
