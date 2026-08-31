-- =====================================================================
-- 01_entitydev_preflight_checks.sql
--
-- PURPOSE : Read-only verification of the ENTITYDEV account before the
--           ENTITY production DDL dry run.
--
-- RUN AS  : SYS (or a DBA account with SELECT on DBA_* views)
-- TARGET  : Exadata DEV  -  VL1SMTBORAM7M01.MCC.IRS.GOV:1701/ALSDEV
--
-- SAFETY  : This script contains SELECT statements ONLY.
--           Nothing is created, altered, dropped, or granted here.
--           It is safe to run at any time, including during business hours.
--
-- HOW TO RUN:
--     sqlplus / as sysdba @01_entitydev_preflight_checks.sql
--
--   ...then send back the generated log file:  entitydev_preflight.log
--
-- MULTITENANT / PLUGGABLE DATABASES:
--   On a container database, "/ as sysdba" connects to CDB$ROOT, not to
--   the PDB that holds ENTITYDEV. The DBA_* views below would then show
--   nothing and every check would look like a failure.
--
--   Check where you landed:
--       SELECT SYS_CONTEXT('USERENV','CON_NAME') FROM dual;
--
--   If that returns CDB$ROOT and ENTITYDEV lives in a PDB, find it:
--       SELECT p.name FROM cdb_users u
--       JOIN v$pdbs p ON p.con_id = u.con_id
--       WHERE u.username = 'ENTITYDEV';
--
--   ...then uncomment and set the line below before running.
--
--   (run_entity_dryrun.sh does all of this automatically -- this note is
--    only for running this script by hand.)

-- ALTER SESSION SET CONTAINER = <your_pdb_name>;

-- Each check below prints what a PASS looks like. If any check does not
-- match, stop and report it -- do not proceed to the DDL run.
-- =====================================================================

SET PAGESIZE 200
SET LINESIZE 200
SET FEEDBACK ON
SET ECHO OFF
SET VERIFY OFF

COLUMN username             FORMAT A22
COLUMN account_status       FORMAT A20
COLUMN default_tablespace   FORMAT A22
COLUMN temporary_tablespace FORMAT A22
COLUMN profile              FORMAT A20
COLUMN privilege            FORMAT A35
COLUMN granted_role         FORMAT A30
COLUMN tablespace_name      FORMAT A25
COLUMN object_type          FORMAT A25
COLUMN owning_schema        FORMAT A22
COLUMN table_name           FORMAT A32
COLUMN contents             FORMAT A12
COLUMN status               FORMAT A12

SPOOL entitydev_preflight.log

PROMPT
PROMPT #####################################################################
PROMPT #  ENTITYDEV PRE-FLIGHT CHECKS
PROMPT #####################################################################
SELECT SYSDATE AS run_time, USER AS run_as FROM dual;

PROMPT
PROMPT ===== Container context =====
PROMPT If this reports CDB$ROOT on a multitenant database, and ENTITYDEV
PROMPT lives in a PDB, the checks below will find nothing. See the note
PROMPT at the top of this script.
SELECT NVL(SYS_CONTEXT('USERENV','CON_NAME'), 'NON-CDB / not applicable')
       AS container_name
FROM   dual;


PROMPT
PROMPT =====================================================================
PROMPT  CHECK 1 : Does ENTITYDEV exist, and is the account usable?
PROMPT ---------------------------------------------------------------------
PROMPT  PASS  = one row returned, ACCOUNT_STATUS is OPEN.
PROMPT  NOTE  = last recorded login was 02-FEB-2026, so EXPIRED or LOCKED
PROMPT          is likely. If so, the password must be reset before the run.
PROMPT =====================================================================
SELECT username,
       account_status,
       lock_date,
       expiry_date,
       default_tablespace,
       temporary_tablespace,
       profile,
       created
FROM   dba_users
WHERE  username = 'ENTITYDEV';


PROMPT
PROMPT =====================================================================
PROMPT  CHECK 2 : Is the TEMPORARY tablespace actually a temp tablespace?
PROMPT ---------------------------------------------------------------------
PROMPT  WHY   = phase2.sql line 37 requested "TEMPORARY TABLESPACE als",
PROMPT          but ALS is a permanent tablespace. Oracle rejects that with
PROMPT          ORA-12911, so the account must have been created with
PROMPT          something else. Confirm what it actually has.
PROMPT  PASS  = one row returned, CONTENTS = TEMPORARY.
PROMPT  IMPACT= a wrong TEMP assignment surfaces as a sort/space failure
PROMPT          partway through index creation, not at connect time.
PROMPT =====================================================================
SELECT u.username,
       u.temporary_tablespace,
       t.contents,
       t.status
FROM   dba_users u
LEFT   JOIN dba_tablespaces t
       ON t.tablespace_name = u.temporary_tablespace
WHERE  u.username = 'ENTITYDEV';


PROMPT
PROMPT =====================================================================
PROMPT  CHECK 3 : Is ENTITYDEV empty?
PROMPT ---------------------------------------------------------------------
PROMPT  PASS  = NO ROWS RETURNED.
PROMPT  WHY   = the schema was described as a sandbox, but that was never
PROMPT          confirmed. If objects already exist, we need to agree on a
PROMPT          clean-down before the run, otherwise the dry run result is
PROMPT          not trustworthy.
PROMPT =====================================================================
SELECT object_type,
       COUNT(*) AS object_count
FROM   dba_objects
WHERE  owner = 'ENTITYDEV'
GROUP  BY object_type
ORDER  BY object_type;


PROMPT
PROMPT =====================================================================
PROMPT  CHECK 4 : What system privileges does ENTITYDEV actually hold?
PROMPT ---------------------------------------------------------------------
PROMPT  EXPECTED from phase2.sql (lines 40-46):
PROMPT      CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SEQUENCE,
PROMPT      CREATE SYNONYM, CREATE ANY TRIGGER, CREATE ANY PROCEDURE
PROMPT
PROMPT  LOOK FOR TWO THINGS:
PROMPT    (a) MISSING: there is no CREATE MATERIALIZED VIEW. The DDL package
PROMPT        contains a materialized_views folder, so that step will fail
PROMPT        with ORA-01031 unless the privilege is added.
PROMPT    (b) TOO BROAD: CREATE ANY PROCEDURE / CREATE ANY TRIGGER allow
PROMPT        ENTITYDEV to create objects inside OTHER schemas, including
PROMPT        ENTITY. See script 02 for the recommended swap.
PROMPT =====================================================================
SELECT privilege,
       admin_option
FROM   dba_sys_privs
WHERE  grantee = 'ENTITYDEV'
ORDER  BY privilege;


PROMPT
PROMPT =====================================================================
PROMPT  CHECK 5 : Roles granted to ENTITYDEV
PROMPT ---------------------------------------------------------------------
PROMPT  WHY   = a role such as DBA or RESOURCE would carry privileges that
PROMPT          are not visible in CHECK 4 and could mask the gaps above.
PROMPT =====================================================================
SELECT granted_role,
       admin_option,
       default_role
FROM   dba_role_privs
WHERE  grantee = 'ENTITYDEV'
ORDER  BY granted_role;


PROMPT
PROMPT =====================================================================
PROMPT  CHECK 6 : Tablespace quota
PROMPT ---------------------------------------------------------------------
PROMPT  PASS  = a row for ALS. phase2.sql requested QUOTA 10G ON als.
PROMPT  NOTE  = MAX_BYTES of -1 means UNLIMITED.
PROMPT          No row at all means NO quota, which fails with ORA-01950
PROMPT          on the very first CREATE TABLE.
PROMPT =====================================================================
SELECT tablespace_name,
       ROUND(bytes/1024/1024, 2)     AS used_mb,
       CASE WHEN max_bytes = -1 THEN NULL
            ELSE ROUND(max_bytes/1024/1024, 2) END AS quota_mb,
       CASE WHEN max_bytes = -1 THEN 'UNLIMITED'
            ELSE 'LIMITED' END       AS quota_type
FROM   dba_ts_quotas
WHERE  username = 'ENTITYDEV'
ORDER  BY tablespace_name;


PROMPT
PROMPT =====================================================================
PROMPT  CHECK 7 : Cross-schema object privileges held by ENTITYDEV
PROMPT ---------------------------------------------------------------------
PROMPT  WHY   = the ENTITY DDL references other schemas (ALSO and SYS were
PROMPT          identified previously). Those grants exist for ENTITY, not
PROMPT          for ENTITYDEV. Anything missing here will compile INVALID.
PROMPT  ALSO  = confirms whether ENTITYDEV has any direct reach into the
PROMPT          real ENTITY schema.
PROMPT  PASS  = expected to be empty or near-empty. Report whatever appears.
PROMPT =====================================================================
SELECT grantor AS owning_schema,
       table_name,
       privilege,
       grantable
FROM   dba_tab_privs
WHERE  grantee = 'ENTITYDEV'
ORDER  BY grantor, table_name, privilege;


PROMPT
PROMPT =====================================================================
PROMPT  CHECK 8 : Which tablespaces exist on this instance?
PROMPT ---------------------------------------------------------------------
PROMPT  WHY   = the production DDL carries explicit TABLESPACE clauses. Any
PROMPT          tablespace named there that is absent here causes ORA-00959.
PROMPT          This list tells us whether to retarget or strip the clauses.
PROMPT =====================================================================
SELECT tablespace_name,
       contents,
       status,
       bigfile
FROM   dba_tablespaces
ORDER  BY contents, tablespace_name;


PROMPT
PROMPT =====================================================================
PROMPT  CHECK 9 : Free space in ALS (ENTITYDEV's default tablespace)
PROMPT ---------------------------------------------------------------------
PROMPT  WHY   = a 10G quota is meaningless if the tablespace itself is full
PROMPT          and cannot autoextend.
PROMPT  PASS  = free space comfortably above the expected footprint, or
PROMPT          AUTOEXTEND enabled with room to grow.
PROMPT =====================================================================
SELECT df.tablespace_name,
       ROUND(SUM(df.bytes)/1024/1024/1024, 2)      AS allocated_gb,
       ROUND(SUM(df.maxbytes)/1024/1024/1024, 2)   AS max_gb,
       MAX(df.autoextensible)                      AS autoextend
FROM   dba_data_files df
WHERE  df.tablespace_name = 'ALS'
GROUP  BY df.tablespace_name;

SELECT tablespace_name,
       ROUND(SUM(bytes)/1024/1024/1024, 2) AS free_gb
FROM   dba_free_space
WHERE  tablespace_name = 'ALS'
GROUP  BY tablespace_name;


PROMPT
PROMPT =====================================================================
PROMPT  CHECK 10 : Confirm the naming collision risk
PROMPT ---------------------------------------------------------------------
PROMPT  WHY   = the DDL scripts are hard-coded with the ENTITY. prefix.
PROMPT          If ENTITY exists on THIS instance, an untransformed script
PROMPT          run as ENTITYDEV can act on the real ENTITY schema rather
PROMPT          than on the sandbox.
PROMPT  PASS  = informational. Expect ENTITY and ENTITYDEV to both appear.
PROMPT          This is exactly why the scripts must be transformed before
PROMPT          they are handed over.
PROMPT =====================================================================
SELECT username,
       account_status,
       default_tablespace,
       created
FROM   dba_users
WHERE  username IN ('ENTITY', 'ENTITYDEV', 'ALS', 'ALSO', 'ALSDEV')
ORDER  BY username;


PROMPT
PROMPT #####################################################################
PROMPT #  END OF PRE-FLIGHT CHECKS
PROMPT #
PROMPT #  Please return the file  entitydev_preflight.log  before any
PROMPT #  DDL is executed. No changes have been made by this script.
PROMPT #####################################################################

SPOOL OFF
