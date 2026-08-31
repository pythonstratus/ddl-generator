-- =====================================================================
-- 99_entitydev_teardown.sql
--
-- PURPOSE : Drop every object in the ENTITYDEV sandbox, returning it to
--           an empty state.
--
-- RUN AS  : ENTITYDEV
--
--     sqlplus ENTITYDEV@<service> @99_entitydev_teardown.sql
--
-- =====================================================================
-- *** THIS SCRIPT IS DESTRUCTIVE AND CANNOT BE UNDONE ***
--
--   Tables are dropped with PURGE, so there is no recycle bin recovery.
--
--   As of 31-AUG-2026 ENTITYDEV held 1,115 objects and roughly 157 GB.
--   Run this ONLY with explicit confirmation that none of it is needed.
--
--   There is no confirmation prompt -- this teardown was authorised in
--   advance, so it runs unattended.
--
--   A hard guard stops the script unless the connected user is exactly
--   ENTITYDEV. It cannot run against ENTITY. That guard is now the only
--   thing standing between this script and the wrong schema. Do not
--   remove it.
--
--   Expect this to take a while -- 460 tables with PURGE is real I/O,
--   not an instant operation. Let it finish.
-- =====================================================================

SET ECHO OFF
SET FEEDBACK ON
SET VERIFY OFF
SET DEFINE ON
SET SERVEROUTPUT ON SIZE UNLIMITED
SET LINESIZE 200
SET PAGESIZE 1000

COLUMN object_name FORMAT A40
COLUMN object_type FORMAT A25

SPOOL entitydev_teardown.log


-- =====================================================================
-- GUARD 1 : ENTITYDEV only
-- =====================================================================
WHENEVER SQLERROR EXIT FAILURE

PROMPT
PROMPT >> Verifying connected user before dropping anything...

BEGIN
    IF USER != 'ENTITYDEV' THEN
        RAISE_APPLICATION_ERROR(-20001,
            'ABORTED: connected as ' || USER || '. This teardown script ' ||
            'runs only as ENTITYDEV. Nothing has been dropped.');
    END IF;
    DBMS_OUTPUT.PUT_LINE('   OK - connected as ' || USER);
END;
/


-- =====================================================================
-- AUTHORISATION
--
-- This script runs unattended. There is no confirmation prompt, because
-- clearing ENTITYDEV has been authorised in advance.
--
--   >>> EDIT THESE TWO LINES BEFORE SENDING <<<
-- =====================================================================
PROMPT
PROMPT =====================================================================
PROMPT  AUTHORISED TEARDOWN OF ENTITYDEV
PROMPT
PROMPT  Confirmed not in use by:  [NAME]
PROMPT  Authorisation date:       [DATE]
PROMPT
PROMPT  Running unattended. Every object owned by ENTITYDEV will be
PROMPT  permanently dropped. There is no recycle bin recovery.
PROMPT
PROMPT  The connected-user check above is the safeguard: this script
PROMPT  cannot run against any schema other than ENTITYDEV.
PROMPT =====================================================================

WHENEVER SQLERROR CONTINUE


-- =====================================================================
-- INVENTORY BEFORE
--
-- Full listing, not just counts. If anyone later asks what was in this
-- schema, this log is the only record.
-- =====================================================================
PROMPT
PROMPT =====================================================================
PROMPT  INVENTORY BEFORE TEARDOWN -- keep this log
PROMPT =====================================================================

PROMPT
PROMPT >> Counts by type:
SELECT object_type, COUNT(*) AS object_count
FROM   user_objects GROUP BY object_type ORDER BY object_type;

PROMPT
PROMPT >> Space in use:
SELECT tablespace_name, ROUND(bytes/1024/1024/1024, 2) AS used_gb
FROM   user_ts_quotas ORDER BY tablespace_name;

PROMPT
PROMPT >> Full object list:
SELECT object_name, object_type, status,
       TO_CHAR(created,'DD-MON-YYYY') AS created
FROM   user_objects
WHERE  object_type NOT IN ('INDEX PARTITION','TABLE PARTITION','LOB')
ORDER  BY object_type, object_name;

PROMPT
PROMPT >> Scheduler jobs:
SELECT job_name, enabled, state, TO_CHAR(next_run_date,'DD-MON-YYYY HH24:MI') AS next_run
FROM   user_scheduler_jobs ORDER BY job_name;


-- =====================================================================
-- STEP 1 : Scheduler jobs
--
-- Jobs are NOT removed by DROP. They need DBMS_SCHEDULER.DROP_JOB, and
-- they are dropped FIRST in case any is on a timer that would recreate
-- objects midway through the teardown.
-- =====================================================================
PROMPT
PROMPT =====================================================================
PROMPT  STEP 1 : Dropping scheduler jobs
PROMPT =====================================================================

DECLARE
    v_done PLS_INTEGER := 0;
BEGIN
    FOR j IN (SELECT job_name FROM user_scheduler_jobs) LOOP
        BEGIN
            BEGIN
                DBMS_SCHEDULER.STOP_JOB(job_name => j.job_name, force => TRUE);
            EXCEPTION WHEN OTHERS THEN NULL;   -- not running: fine
            END;

            DBMS_SCHEDULER.DROP_JOB(job_name => j.job_name, force => TRUE);
            v_done := v_done + 1;
            DBMS_OUTPUT.PUT_LINE('  Dropped job: ' || j.job_name);
        EXCEPTION
            WHEN OTHERS THEN
                DBMS_OUTPUT.PUT_LINE('  FAILED job: ' || j.job_name
                                     || ' => ' || SQLERRM);
        END;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('  Jobs dropped: ' || v_done);
END;
/


-- =====================================================================
-- STEP 2 : Everything else
--
-- Order matters. Synonyms and views first (they depend on tables), then
-- code, then materialized views, then tables, then sequences and types.
--
-- Not dropped directly, because they go with their parent:
--   INDEX, INDEX PARTITION, TABLE PARTITION, LOB, PACKAGE BODY
--
-- Each drop is wrapped individually, so one failure does not stop the
-- rest.
-- =====================================================================
PROMPT
PROMPT =====================================================================
PROMPT  STEP 2 : Dropping schema objects
PROMPT  (460 tables with PURGE -- this will take a few minutes)
PROMPT =====================================================================

DECLARE
    v_dropped PLS_INTEGER := 0;
    v_skipped PLS_INTEGER := 0;
BEGIN
    FOR rec IN (
        SELECT object_name, object_type
        FROM   user_objects
        WHERE  object_type IN ('SYNONYM', 'VIEW', 'MATERIALIZED VIEW',
                               'TRIGGER', 'PROCEDURE', 'FUNCTION',
                               'PACKAGE', 'TABLE', 'SEQUENCE', 'TYPE')
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
        EXCEPTION
            WHEN OTHERS THEN
                -- ORA-04043 / ORA-00942 are normal: already removed as a
                -- dependency of an earlier drop.
                v_skipped := v_skipped + 1;
                DBMS_OUTPUT.PUT_LINE('  Skipped: ' || rec.object_type
                                     || ' - ' || rec.object_name
                                     || ' => ' || SQLERRM);
        END;
    END LOOP;

    DBMS_OUTPUT.PUT_LINE(' ');
    DBMS_OUTPUT.PUT_LINE('  Dropped: ' || v_dropped
                         || '   Skipped: ' || v_skipped);
END;
/


-- =====================================================================
-- STEP 3 : Second pass
--
-- Circular foreign keys and nested types can survive the first pass.
-- Running the same loop again clears what the first ordering could not.
-- =====================================================================
PROMPT
PROMPT =====================================================================
PROMPT  STEP 3 : Second pass for anything left behind
PROMPT =====================================================================

DECLARE
    v_dropped PLS_INTEGER := 0;
BEGIN
    FOR rec IN (
        SELECT object_name, object_type
        FROM   user_objects
        WHERE  object_type IN ('SYNONYM', 'VIEW', 'MATERIALIZED VIEW',
                               'TRIGGER', 'PROCEDURE', 'FUNCTION',
                               'PACKAGE', 'TABLE', 'SEQUENCE', 'TYPE')
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
            DBMS_OUTPUT.PUT_LINE('  Second pass dropped: ' || rec.object_type
                                 || ' - ' || rec.object_name);
        EXCEPTION
            WHEN OTHERS THEN NULL;
        END;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('  Second pass total: ' || v_dropped);
END;
/


-- =====================================================================
-- STEP 4 : Empty the recycle bin
-- =====================================================================
PROMPT
PROMPT >> Purging recycle bin...
PURGE RECYCLEBIN;


-- =====================================================================
-- VERIFY
-- =====================================================================
PROMPT
PROMPT =====================================================================
PROMPT  VERIFICATION
PROMPT =====================================================================

PROMPT
PROMPT >> Remaining objects (expected: no rows):
SELECT object_name, object_type, status
FROM   user_objects ORDER BY object_type, object_name;

PROMPT
PROMPT >> Remaining object count (MUST be 0 before the dry run):
SELECT COUNT(*) AS remaining_objects FROM user_objects;

PROMPT
PROMPT >> Remaining scheduler jobs (expected: no rows):
SELECT job_name, state FROM user_scheduler_jobs;

PROMPT
PROMPT >> Space still charged to ENTITYDEV (expected: 0):
SELECT tablespace_name, ROUND(bytes/1024/1024/1024, 2) AS used_gb
FROM   user_ts_quotas ORDER BY tablespace_name;

PROMPT
PROMPT =====================================================================
PROMPT  TEARDOWN COMPLETE
PROMPT
PROMPT  The remaining object count above must be 0. If anything is listed,
PROMPT  report it rather than running this again -- a repeat pass will not
PROMPT  clear an object that has already failed twice.
PROMPT
PROMPT  Please send entitydev_teardown.log back. It contains the inventory
PROMPT  of what was removed.
PROMPT =====================================================================

SPOOL OFF
