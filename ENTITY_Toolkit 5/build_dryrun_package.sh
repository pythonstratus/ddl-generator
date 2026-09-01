#!/usr/bin/env bash
#
# =====================================================================
#  build_dryrun_package.sh
#
#  Builds the complete ENTITY dry-run package from the production DDL,
#  in one pass.
#
#      ./build_dryrun_package.sh ENTITY_DDLs_Prod
#
#  Steps:
#      1. Record the tablespaces the package expects   (KEEP THIS)
#      2. Copy the source tree -- the original is never modified
#      3. Delete standalone TABLESPACE lines
#      4. Replace the ENTITY. prefix with ENTITYDEV.
#      5. Remove the superseded master driver
#      6. Discover the child scripts and GENERATE master_run_entitydev.sql
#      7. Verify
#      8. Assemble ENTITY_DryRun_Package/ and zip it
#
#  Requires: GNU sed, bash 4+. Run from WSL, Git Bash, or the server.
#  On macOS: brew install gnu-sed, then SED=gsed ./build_dryrun_package.sh
#
#  Nothing here touches a database.
#
#  Companion files must sit in this directory or beside this script:
#      run_entity_dryrun.sh
#      01_entitydev_preflight_checks.sql
#      99_entitydev_teardown.sql
# =====================================================================

set -uo pipefail

SED="${SED:-sed}"

readonly SRC_USER="ENTITY"
DST_USER="${SANDBOX_USER:-ENTITYDEV}"
readonly PKG="ENTITY_DryRun_Package"

SRC="${1:-}"
DST="${2:-ENTITY_DDLs_DryRun}"

if [[ -z "$SRC" ]]; then
    echo "Usage: $0 <production-ddl-dir> [output-dir]" >&2
    echo "  e.g. $0 ENTITY_DDLs_Prod" >&2
    exit 2
fi
[[ -d "$SRC" ]] || { echo "ERROR: not found: $SRC" >&2; exit 2; }

if [[ -e "$DST" ]]; then
    echo "ERROR: $DST already exists. Remove it or pass another name." >&2
    echo "       Refusing to overwrite, so a half-finished build is never" >&2
    echo "       mistaken for a good one." >&2
    exit 2
fi

if ! echo x | $SED -E 's/x/y/' >/dev/null 2>&1; then
    echo "ERROR: '$SED' lacks -E. Install GNU sed or set SED=gsed." >&2
    exit 2
fi

REPORT="${DST}_build_report.txt"
TSLIST="${DST}_tablespaces_expected.txt"
MASTER_OUT="$DST/master_run_entitydev.sql"

exec > >(tee "$REPORT") 2>&1

FAILED=0

echo "======================================================================"
echo " ENTITY dry-run package build"
echo " Date   : $(date '+%Y-%m-%d %H:%M:%S')"
echo " Source : $SRC"
echo " Output : $DST"
echo "======================================================================"
echo


# =====================================================================
# STEP 1 : Tablespace inventory
# =====================================================================
echo "STEP 1 : Recording expected tablespaces"
echo "----------------------------------------------------------------------"

{ grep -rhoiE '\bTABLESPACE\b[[:space:]]+[A-Za-z0-9_$#"]+' \
     --include='*.sql' --exclude-dir=users "$SRC" 2>/dev/null || true; } \
  | awk '{print toupper($2)}' | sort -u > "$TSLIST"

if [[ -s "$TSLIST" ]]; then
    echo "  The production package expects these tablespaces to exist:"
    $SED 's/^/    /' "$TSLIST"
    echo
    echo "  >> Saved to $TSLIST -- KEEP IT."
    echo "     This is a PRODUCTION pre-requisite checklist. Once the"
    echo "     clauses are stripped below, it is the only record."
else
    echo "  No tablespace clauses found."
fi
echo


# =====================================================================
# STEP 2 : Copy
# =====================================================================
echo "STEP 2 : Copying source tree"
echo "----------------------------------------------------------------------"
cp -r "$SRC" "$DST"
echo "  $(find "$DST" -name '*.sql' -not -path '*/users/*' | wc -l | tr -d ' ') .sql file(s) in scope (users/ excluded)"
echo


# =====================================================================
# STEP 3 : Strip standalone TABLESPACE lines
#
# Whole-line match only, so these are left alone:
#   DEFAULT TABLESPACE als / TEMPORARY TABLESPACE als
#   USING INDEX TABLESPACE ENTITY
#   LOB (X) STORE AS (TABLESPACE ENTITY ...)
# Inline survivors are reported in STEP 7.
# =====================================================================
echo "STEP 3 : Removing standalone TABLESPACE lines"
echo "----------------------------------------------------------------------"

TS_COUNT=$( { grep -rciE '^[[:space:]]*TABLESPACE[[:space:]]' \
            --include='*.sql' --exclude-dir=users "$DST" 2>/dev/null || true; } \
            | awk -F: '{s+=$2} END {print s+0}')

find "$DST" -name '*.sql' -not -path '*/users/*' -exec \
    $SED -i -E '/^[[:space:]]*TABLESPACE[[:space:]]+[A-Za-z0-9_$#"]+[[:space:]]*$/d' {} +

echo "  Removed $TS_COUNT line(s)"
echo


# =====================================================================
# STEP 4 : ENTITY. -> ENTITYDEV.
#
# The dot is deliberate. Replacing bare ENTITY would break
# "TABLESPACE ENTITY" and filenames such as ENTITY_TablesProd.sql.
# The I flag catches lowercase, which Oracle treats as equivalent.
# Idempotent: ENTITYDEV. has a D where the pattern needs a dot.
# =====================================================================
echo "STEP 4 : Replacing ${SRC_USER}. with ${DST_USER}."
echo "----------------------------------------------------------------------"

REF_BEFORE=$( { grep -rciE "\\b${SRC_USER}\\." \
              --include='*.sql' --exclude-dir=users "$DST" 2>/dev/null || true; } \
              | awk -F: '{s+=$2} END {print s+0}')

find "$DST" -name '*.sql' -not -path '*/users/*' -exec \
    $SED -i -E "s/\\b${SRC_USER}\\./${DST_USER}./gI" {} +

# Quoted identifiers: "ENTITY"."OBJECT". The unquoted pattern above
# cannot reach these because a quote sits between the Y and the dot.
# Case-sensitive on purpose -- a quoted identifier only matches the
# schema when the case is exact.
QREF=$( { grep -rc "\"${SRC_USER}\"\\." \
        --include='*.sql' --exclude-dir=users "$DST" 2>/dev/null || true; } \
        | awk -F: '{s+=$2} END {print s+0}')

find "$DST" -name '*.sql' -not -path '*/users/*' -exec \
    $SED -i "s/\"${SRC_USER}\"\\./\"${DST_USER}\"./g" {} +

echo "  Rewrote $REF_BEFORE unquoted reference(s)"
echo "  Rewrote $QREF quoted reference(s)  (\"${SRC_USER}\". -> \"${DST_USER}\".)"
echo


# =====================================================================
# STEP 5 : Remove the superseded driver
# =====================================================================
echo "STEP 5 : Removing the superseded master driver"
echo "----------------------------------------------------------------------"

STALE=$(find "$DST" -maxdepth 1 -name 'master_run*.sql' 2>/dev/null || true)
if [[ -n "$STALE" ]]; then
    echo "$STALE" | while read -r s; do
        [[ -n "$s" ]] && { rm -f "$s"; echo "  Deleted $(basename "$s")"; }
    done
    echo "  A fresh driver is generated in STEP 6. The original is"
    echo "  untouched in $SRC and in source control."
else
    echo "  None found."
fi
echo


# =====================================================================
# STEP 6 : Discover child scripts, generate the master driver
#
# Matches the ENTITY_<Type>Prod.sql convention only. This deliberately
# excludes seed-data and ad-hoc files (entitles.sql,
# populate_week_data_holidays.sql, rptname_mod.sql) which the original
# driver also never referenced. Bringing them in would change the
# scope of the deployment without anyone deciding to.
# =====================================================================
echo "STEP 6 : Generating master_run_entitydev.sql"
echo "----------------------------------------------------------------------"

STEP_DIRS=(sequences functions tables indexes procedures views synonyms)
STEP_LABELS=("Creating Sequences"
             "Creating Functions"
             "Creating Tables"
             "Creating Indexes"
             "Creating Stored Procedures"
             "Creating Views"
             "Creating Synonyms and Grants")
# 1 = skip quietly if absent, rather than failing the build.
STEP_OPTIONAL=(0 1 0 0 0 0 0)

# functions/ runs BEFORE tables/ deliberately. The table and index DDL
# calls schema-qualified functions (for virtual columns or
# function-based indexes), so the functions must already exist. In
# Production this is invisible because they are already there; in an
# empty sandbox the tables would fail without them. The original
# six-step driver did not include functions at all.

declare -a RESOLVED=()

idx=0
for d in "${STEP_DIRS[@]}"; do
    opt="${STEP_OPTIONAL[$idx]}"

    if [[ ! -d "$DST/$d" ]]; then
        if [[ "$opt" == "1" ]]; then
            echo "  ${d}/  ->  (folder absent, step skipped)"
        else
            echo "  MISSING FOLDER  $d/"
            FAILED=1
        fi
        RESOLVED+=("")
        idx=$((idx+1))
        continue
    fi

    declare -a hits=()
    while IFS= read -r h; do
        [[ -n "$h" ]] && hits+=("$(basename "$h")")
    done < <(find "$DST/$d" -maxdepth 1 -type f -iname "${SRC_USER}_*Prod.sql" 2>/dev/null | sort)

    case ${#hits[@]} in
        1) echo "  ${d}/  ->  ${hits[0]}" ; RESOLVED+=("${hits[0]}") ;;
        0) if [[ "$opt" == "1" ]]; then
               echo "  ${d}/  ->  (no ${SRC_USER}_<Type>Prod.sql, step skipped)"
           else
               echo "  NO MATCH        ${d}/  (expected ${SRC_USER}_<Type>Prod.sql)"
               FAILED=1
           fi
           RESOLVED+=("") ;;
        *) echo "  AMBIGUOUS       ${d}/  matched ${#hits[@]} files:"
           printf '                    %s\n' "${hits[@]}"
           echo "                  Cannot choose. Rename or remove the extras."
           RESOLVED+=("") ; FAILED=1 ;;
    esac
    unset hits
    idx=$((idx+1))
done

# Indices that will actually be emitted, so the steps number correctly.
declare -a EMIT_IDX=()
for k in "${!STEP_DIRS[@]}"; do
    [[ -n "${RESOLVED[$k]:-}" ]] && EMIT_IDX+=("$k")
done
TOTAL=${#EMIT_IDX[@]}

# Report .sql files that exist but are not being run, so the scope
# decision stays visible instead of silently disappearing.
echo
echo "  Present but NOT referenced by the generated driver:"
UNREF=0
while IFS= read -r f; do
    rel="${f#"$DST"/}"
    base="$(basename "$f")"
    dir="$(dirname "$rel")"
    idx=0; referenced=0
    for d in "${STEP_DIRS[@]}"; do
        if [[ "$dir" == "$d" && "${RESOLVED[$idx]:-}" == "$base" ]]; then referenced=1; fi
        idx=$((idx+1))
    done
    if [[ $referenced -eq 0 ]]; then
        echo "    $rel"
        UNREF=$((UNREF+1))
    fi
done < <(find "$DST" -name '*.sql' -not -path '*/users/*' -not -name 'master_run*' | sort)
[[ $UNREF -eq 0 ]] && echo "    (none)"

if [[ -d "$DST/materialized_views" ]]; then
    echo
    echo "  NOTE: materialized_views/ exists but is not in the sequence,"
    echo "        matching the original driver. It is emitted as a"
    echo "        commented-out step. If it is in scope, uncomment it and"
    echo "        add: GRANT CREATE MATERIALIZED VIEW TO ${DST_USER};"
fi

if [[ -n "${RESOLVED[1]:-}" ]]; then
    echo
    echo "  NOTE: functions/ IS included, placed before tables/. The"
    echo "        original driver omitted it, but the table and index DDL"
    echo "        calls schema-qualified functions, so they must exist"
    echo "        first. Watch the function validation in the log -- if"
    echo "        any compile INVALID, the table step will fail."
fi

if [[ $FAILED -ne 0 ]]; then
    echo
    echo "  Cannot generate the driver until the items above are resolved."
    echo
    echo "======================================================================"
    echo " BUILD STOPPED"
    echo "======================================================================"
    echo
    exit 1
fi

# --- header, session config, guards ----------------------------------
cat > "$MASTER_OUT" <<'SQLHDR'
-- =====================================================================
-- master_run_entitydev.sql   --  GENERATED, DO NOT HAND-EDIT
--
-- Regenerate with:  ./build_dryrun_package.sh ENTITY_DDLs_Prod
--
-- MASTER DDL DEPLOYMENT SCRIPT  --  DRY RUN VARIANT
--
-- Schema      : ENTITYDEV   (sandbox -- NOT the ENTITY schema)
-- Environment : Exadata DEV  -  VL1SMTBORAM7M01.MCC.IRS.GOV:1701/ALSDEV
-- Purpose     : Validate the ENTITY production DDL before the Prod run
--
-- The @@ references below were resolved against the files actually
-- present on disk, not assumed from a naming convention.
--
-- Fixes carried over from the original February driver:
--   - SPOOL worked around SET DEFINE OFF, so no log was ever written
--   - index filters now escape the underscore in LIKE 'SYS_%'
--   - connected-user and empty-schema guards added
--
-- WHENEVER SQLERROR CONTINUE is deliberate: a validation run should
-- surface every failure in one pass. The script therefore runs to
-- completion even when steps fail. Completion is not success.
-- =====================================================================

SET ECHO ON
SET FEEDBACK ON
SET VERIFY OFF
SET TIMING ON
SET SERVEROUTPUT ON SIZE UNLIMITED
SET LINESIZE 200
SET PAGESIZE 50
SET TRIMSPOOL ON
SET TERMOUT ON

SET DEFINE ON
COLUMN log_file NEW_VALUE log_filename NOPRINT
SELECT 'entitydev_dryrun_' || TO_CHAR(SYSDATE, 'YYYYMMDD_HH24MISS') || '.log'
       AS log_file
FROM   DUAL;
SPOOL &log_filename
SET DEFINE OFF


-- =====================================================================
-- GUARD 1 : connected user must be ENTITYDEV
--
-- ENTITY and ENTITYDEV both exist on this instance and differ by three
-- characters. The DDL contains DROP TABLE statements. Running this as
-- SYS, or as ENTITY, could affect the real schema.
-- =====================================================================
WHENEVER SQLERROR EXIT FAILURE

PROMPT
PROMPT >> Verifying connected user...
BEGIN
    IF USER != 'ENTITYDEV' THEN
        RAISE_APPLICATION_ERROR(-20001,
            'ABORTED: connected as ' || USER || '. This script must run as ' ||
            'ENTITYDEV. No DDL has been executed.');
    END IF;
    DBMS_OUTPUT.PUT_LINE('   OK - connected as ' || USER);
END;
/

-- =====================================================================
-- GUARD 2 : sandbox must be empty
-- =====================================================================
PROMPT
PROMPT >> Verifying ENTITYDEV is empty...
DECLARE
    v_count PLS_INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_objects;
    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20002,
            'ABORTED: ENTITYDEV holds ' || v_count || ' object(s). ' ||
            'This schema is NOT empty. Do NOT clear it and do NOT comment ' ||
            'out this guard without confirming with the development team ' ||
            'who owns these objects. No DDL has been executed.');
    END IF;
    DBMS_OUTPUT.PUT_LINE('   OK - schema is empty.');
END;
/

WHENEVER SQLERROR CONTINUE


PROMPT
PROMPT ================================================================
PROMPT  ENTITY DDL DRY RUN - START
PROMPT ================================================================
PROMPT
SELECT 'Dry Run Started At : ' || TO_CHAR(SYSDATE, 'DD-MON-YYYY HH24:MI:SS') AS deployment_info FROM DUAL;
SELECT 'Connected As       : ' || USER                                       AS deployment_info FROM DUAL;
SELECT 'Database           : ' || ORA_DATABASE_NAME                          AS deployment_info FROM DUAL;
SELECT 'Instance           : ' || SYS_CONTEXT('USERENV', 'INSTANCE_NAME')    AS deployment_info FROM DUAL;
SELECT 'Host               : ' || SYS_CONTEXT('USERENV', 'SERVER_HOST')      AS deployment_info FROM DUAL;
SELECT 'Session ID         : ' || SYS_CONTEXT('USERENV', 'SESSIONID')        AS deployment_info FROM DUAL;

PROMPT
PROMPT >> Pre-deployment object counts (expect none):
SELECT object_type, COUNT(*) AS object_count
FROM   user_objects GROUP BY object_type ORDER BY object_type;

-- ---------------------------------------------------------------------
-- NLS DIAGNOSTIC
--
-- The table DDL contains at least one date default written as a string
-- literal (ACTDT DATE DEFAULT '01-JAN-1900'). That parses only when
-- NLS_DATE_FORMAT is DD-MON-YYYY with English months; otherwise
-- ORA-01861.
--
-- Deliberately reported rather than forced. Forcing it would hide a
-- mismatch here that would reappear in Production.
-- ---------------------------------------------------------------------
PROMPT
PROMPT >> Session NLS settings (compare against Production):
SELECT parameter, value FROM nls_session_parameters
WHERE  parameter IN ('NLS_DATE_FORMAT','NLS_DATE_LANGUAGE','NLS_LANGUAGE',
                     'NLS_TERRITORY','NLS_NUMERIC_CHARACTERS')
ORDER  BY parameter;
SQLHDR

# --- per-step blocks -------------------------------------------------
emit_validation() {
    local kind="$1"
    case "$kind" in
      sequences) cat >> "$MASTER_OUT" <<'V'
PROMPT >> Validating Sequences...
SELECT sequence_name, min_value, max_value, increment_by, last_number, cache_size
FROM   user_sequences ORDER BY sequence_name;
PROMPT >> Sequence Count:
SELECT COUNT(*) AS total_sequences FROM user_sequences;
V
      ;;
      tables) cat >> "$MASTER_OUT" <<'V'
PROMPT >> Validating Tables...
SELECT table_name, num_rows, tablespace_name, status
FROM   user_tables ORDER BY table_name;
PROMPT >> Table Count:
SELECT COUNT(*) AS total_tables FROM user_tables;
PROMPT >> Constraints Created With Tables:
SELECT constraint_type,
       DECODE(constraint_type,'P','Primary Key','U','Unique',
                              'C','Check','R','Foreign Key') AS type_desc,
       COUNT(*) AS constraint_count
FROM   user_constraints GROUP BY constraint_type ORDER BY constraint_type;
V
      ;;
      indexes) cat >> "$MASTER_OUT" <<'V'
PROMPT >> Validating Indexes (excluding SYS_ auto-generated)...
SELECT index_name, table_name, uniqueness, index_type, status
FROM   user_indexes
WHERE  index_name NOT LIKE 'SYS\_%' ESCAPE '\'
ORDER  BY table_name, index_name;
PROMPT >> Index Count (explicit only):
SELECT COUNT(*) AS total_explicit_indexes FROM user_indexes
WHERE  index_name NOT LIKE 'SYS\_%' ESCAPE '\';
V
      ;;
      functions) cat >> "$MASTER_OUT" <<'V'
PROMPT >> Validating Functions...
SELECT object_name, object_type, status,
       TO_CHAR(last_ddl_time,'DD-MON-YYYY HH24:MI:SS') AS last_compiled
FROM   user_objects
WHERE  object_type = 'FUNCTION'
ORDER  BY object_name;
PROMPT >> Compilation Errors (if any):
SELECT name, type, line, position, text AS error_text
FROM   user_errors WHERE type = 'FUNCTION'
ORDER  BY name, sequence;
PROMPT
PROMPT >> NOTE: functions must be VALID before the table and index steps.
PROMPT    The table/index DDL calls schema-qualified functions. If any
PROMPT    function above is INVALID, expect failures in later steps.
V
      ;;
      procedures) cat >> "$MASTER_OUT" <<'V'
PROMPT >> Validating Procedures and Functions...
SELECT object_name, object_type, status,
       TO_CHAR(last_ddl_time,'DD-MON-YYYY HH24:MI:SS') AS last_compiled
FROM   user_objects
WHERE  object_type IN ('PROCEDURE','FUNCTION','PACKAGE','PACKAGE BODY')
ORDER  BY object_type, object_name;
PROMPT >> Compilation Errors (if any):
SELECT name, type, line, position, text AS error_text
FROM   user_errors
WHERE  type IN ('PROCEDURE','FUNCTION','PACKAGE','PACKAGE BODY')
ORDER  BY type, name, sequence;
V
      ;;
      views) cat >> "$MASTER_OUT" <<'V'
PROMPT >> Validating Views...
SELECT object_name AS view_name, status FROM user_objects
WHERE  object_type = 'VIEW' ORDER BY object_name;
PROMPT >> View Count:
SELECT COUNT(*) AS total_views FROM user_views;
V
      ;;
      synonyms) cat >> "$MASTER_OUT" <<'V'
PROMPT >> Validating Synonyms...
SELECT synonym_name, table_owner, table_name, db_link
FROM   user_synonyms ORDER BY synonym_name;
PROMPT >> Synonym Count:
SELECT COUNT(*) AS total_synonyms FROM user_synonyms;
PROMPT >> Grants Issued (the synonyms file contains none, so expect no rows):
SELECT grantee, table_name, privilege, grantable
FROM   user_tab_privs_made ORDER BY grantee, table_name, privilege;
V
      ;;
    esac
}

n=0
for i in "${EMIT_IDX[@]}"; do
    n=$((n+1))
    d="${STEP_DIRS[$i]}"
    f="${RESOLVED[$i]}"

    if [[ "$d" == "tables" ]]; then
        cat >> "$MASTER_OUT" <<'TNOTE'


-- =====================================================================
-- *** READ BEFORE INTERPRETING THE LOG ***
--
-- Each table block opens with a DROP:
--     DROP TABLE ENTITYDEV.<name> CASCADE CONSTRAINTS;
--
-- Guard 2 requires an empty schema, so on a clean run EVERY drop fails
-- with ORA-00942: table or view does not exist. One per table. That is
-- expected and is not a defect.
--
-- NOT normal, report immediately:
--   - any error naming ENTITY. rather than ENTITYDEV.
--   - any DROP that SUCCEEDS on a clean run
-- =====================================================================
TNOTE
    fi

    cat >> "$MASTER_OUT" <<EOS


-- =====================================================================
-- STEP $n OF $TOTAL : ${STEP_LABELS[$i]}
-- =====================================================================
PROMPT
PROMPT ================================================================
PROMPT  STEP $n OF $TOTAL : ${STEP_LABELS[$i]}
PROMPT  Source      : ${d}/${f}
PROMPT ================================================================
PROMPT
SELECT TO_CHAR(SYSDATE, 'DD-MON-YYYY HH24:MI:SS') AS step${n}_start FROM DUAL;

@@${d}/${f}

PROMPT
PROMPT >> Step $n Completed.
EOS

    emit_validation "$d"

    if [[ "$d" == "views" && -d "$DST/materialized_views" ]]; then
        echo "" >> "$MASTER_OUT"
        echo "-- materialized_views/ exists but was not part of the original" >> "$MASTER_OUT"
        echo "-- six-step sequence. If in scope, ENTITYDEV also needs:" >> "$MASTER_OUT"
        echo "--     GRANT CREATE MATERIALIZED VIEW TO ENTITYDEV;" >> "$MASTER_OUT"
        echo "-- @@materialized_views/${SRC_USER}_MViewsProd.sql" >> "$MASTER_OUT"
    fi

done

# --- post-validation, isolation proof, summary -----------------------
cat >> "$MASTER_OUT" <<'SQLFTR'


-- =====================================================================
-- POST-DEPLOYMENT VALIDATION
-- =====================================================================
PROMPT
PROMPT ================================================================
PROMPT  POST-DEPLOYMENT VALIDATION
PROMPT ================================================================

PROMPT >> Final Object Counts by Type:
SELECT object_type, COUNT(*) AS object_count,
       SUM(CASE WHEN status='VALID'   THEN 1 ELSE 0 END) AS valid_count,
       SUM(CASE WHEN status='INVALID' THEN 1 ELSE 0 END) AS invalid_count
FROM   user_objects GROUP BY object_type ORDER BY object_type;

PROMPT
PROMPT >> INVALID OBJECTS (requires attention if any):
SELECT object_name, object_type, status,
       TO_CHAR(last_ddl_time,'DD-MON-YYYY HH24:MI:SS') AS last_ddl_time
FROM   user_objects WHERE status='INVALID'
ORDER  BY object_type, object_name;

PROMPT
PROMPT >> Attempting recompilation of invalid objects...
BEGIN
    FOR rec IN (
        SELECT object_name, object_type FROM user_objects
        WHERE  status = 'INVALID'
        ORDER  BY DECODE(object_type,'SEQUENCE',1,'TABLE',2,'INDEX',3,
                                     'PROCEDURE',4,'FUNCTION',5,'PACKAGE',6,
                                     'PACKAGE BODY',7,'VIEW',8,'TRIGGER',9,
                                     'SYNONYM',10,99)
    ) LOOP
        BEGIN
            IF rec.object_type = 'PACKAGE BODY' THEN
                EXECUTE IMMEDIATE 'ALTER PACKAGE ' || rec.object_name || ' COMPILE BODY';
            ELSE
                EXECUTE IMMEDIATE 'ALTER ' || rec.object_type || ' ' || rec.object_name || ' COMPILE';
            END IF;
            DBMS_OUTPUT.PUT_LINE('  Recompiled: ' || rec.object_type || ' - ' || rec.object_name || ' => SUCCESS');
        EXCEPTION WHEN OTHERS THEN
            DBMS_OUTPUT.PUT_LINE('  Recompiled: ' || rec.object_type || ' - ' || rec.object_name || ' => FAILED: ' || SQLERRM);
        END;
    END LOOP;
END;
/

PROMPT
PROMPT >> Remaining INVALID Objects After Recompilation:
SELECT object_name, object_type, status FROM user_objects
WHERE  status='INVALID' ORDER BY object_type, object_name;

PROMPT
PROMPT >> All Compilation Errors:
SELECT name, type, line || ':' || position AS location, text AS error_text
FROM   user_errors ORDER BY type, name, sequence;


-- =====================================================================
-- ISOLATION PROOF
--
-- Lists any object visible to this session, in any other schema, whose
-- DDL time falls inside today. EXPECTED: NO ROWS.
--
-- Include this in what goes back to the client. It is the evidence that
-- the ENTITY schema was never touched.
-- =====================================================================
PROMPT
PROMPT >> ISOLATION CHECK - objects changed outside ENTITYDEV today:
PROMPT    (expected: no rows)
SELECT owner, object_name, object_type,
       TO_CHAR(last_ddl_time,'DD-MON-YYYY HH24:MI:SS') AS last_ddl_time
FROM   all_objects
WHERE  owner != 'ENTITYDEV' AND last_ddl_time >= TRUNC(SYSDATE)
ORDER  BY owner, object_type, object_name;


PROMPT
PROMPT ================================================================
PROMPT  DRY RUN SUMMARY
PROMPT ================================================================
SELECT 'Sequences'  AS object_type, COUNT(*) AS total FROM user_sequences UNION ALL
SELECT 'Tables',     COUNT(*) FROM user_tables    UNION ALL
SELECT 'Indexes',    COUNT(*) FROM user_indexes WHERE index_name NOT LIKE 'SYS\_%' ESCAPE '\' UNION ALL
SELECT 'Procedures', COUNT(*) FROM user_objects WHERE object_type='PROCEDURE' UNION ALL
SELECT 'Functions',  COUNT(*) FROM user_objects WHERE object_type='FUNCTION'  UNION ALL
SELECT 'Packages',   COUNT(*) FROM user_objects WHERE object_type='PACKAGE'   UNION ALL
SELECT 'Views',      COUNT(*) FROM user_views    UNION ALL
SELECT 'Synonyms',   COUNT(*) FROM user_synonyms UNION ALL
SELECT 'Triggers',   COUNT(*) FROM user_objects WHERE object_type='TRIGGER';

SELECT 'Dry Run Completed At : ' || TO_CHAR(SYSDATE,'DD-MON-YYYY HH24:MI:SS') AS deployment_info FROM DUAL;

SET DEFINE ON
PROMPT
PROMPT ================================================================
PROMPT  ENTITY DDL DRY RUN - COMPLETE
PROMPT  Log file: &log_filename
PROMPT  NOTE: this script continues past errors by design. Completion
PROMPT        does not mean success -- the log must be reviewed.
PROMPT ================================================================

SPOOL OFF
SET ECHO OFF
SET TIMING OFF
EXIT
SQLFTR

echo
if [[ "$DST_USER" != "ENTITYDEV" ]]; then
    $SED -i "s/\\bENTITYDEV\\b/${DST_USER}/g" "$MASTER_OUT"
    echo "  Retargeted generated driver to ${DST_USER}"
fi
echo "  Generated $MASTER_OUT ($(wc -l < "$MASTER_OUT" | tr -d ' ') lines)"
echo


# =====================================================================
# STEP 7 : Verification
# =====================================================================
echo "STEP 7 : Verification"
echo "----------------------------------------------------------------------"

echo
echo "  [1] Leftover ${SRC_USER}. references (expected: none)"
if grep -rniE "\\b${SRC_USER}\\." --include='*.sql' --exclude-dir=users "$DST" 2>/dev/null \
     | grep -viE "\\b${DST_USER}\\." | $SED 's/^/      /' | grep . ; then
    echo "      *** FAIL - these still point at the real ${SRC_USER} schema."
    FAILED=1
else
    echo "      PASS"
fi

echo
echo "  [2] Quoted identifiers \"${SRC_USER}\" (expected: none)"
echo "      The replace cannot match \"${SRC_USER}\".TABLE -- a quote sits"
echo "      between the Y and the dot."
if grep -rn "\"${SRC_USER}\"" --include='*.sql' --exclude-dir=users "$DST" 2>/dev/null \
     | $SED 's/^/      /' | grep . ; then
    echo "      *** FAIL - handle these by hand."
    FAILED=1
else
    echo "      PASS"
fi

echo
echo "  [3] Remaining TABLESPACE clauses (inline)"
# \b both sides, so this matches the keyword only -- never the
# tablespace_name column used in the driver's own validation queries.
# Occurrences inside single quotes are PL/SQL dynamic SQL building a
# statement at runtime (v_sql := v_sql || ' TABLESPACE ' || ...), not
# tablespace clauses. Removing those would break the procedure.
if grep -rniE '\bTABLESPACE\b[[:space:]]+' --include='*.sql' --exclude-dir=users "$DST" 2>/dev/null \
     | grep -viE "'[^']*TABLESPACE" \
     | grep -viE '\-\-.*TABLESPACE' \
     | $SED 's/^/      /' | grep . ; then
    echo
    echo "      *** REVIEW - inline clauses the line-delete cannot remove."
    echo "      Usually: delete just the TABLESPACE <name> token pair."
    echo "        USING INDEX TABLESPACE ENTITY  ->  USING INDEX"
    echo "      Exception: a LOB clause containing ONLY a tablespace would"
    echo "      leave STORE AS () which will not parse. Ask before editing."
    FAILED=1
else
    echo "      PASS"
fi

echo
echo "  [4] Generated @@ references resolve"
while read -r ref; do
    [[ -z "$ref" ]] && continue
    [[ -f "$DST/$ref" ]] && echo "      OK       $ref" \
                         || { echo "      MISSING  $ref"; FAILED=1; }
done < <(grep -oE '^[[:space:]]*@@[^[:space:]]+' "$MASTER_OUT" 2>/dev/null \
         | $SED -E 's/^[[:space:]]*@@//' || true)

echo
echo "  [5] DROP statements retained"
DROPS=$( { grep -rciE '^[[:space:]]*DROP[[:space:]]+TABLE' \
        --include='*.sql' --exclude-dir=users "$DST" 2>/dev/null || true; } \
        | awk -F: '{s+=$2} END {print s+0}')
echo "      $DROPS DROP TABLE statement(s)."
echo "      Kept deliberately: they make the run repeatable, and each"
echo "      produces one expected ORA-00942 against an empty schema."

echo
echo "======================================================================"
if [[ $FAILED -eq 0 ]]; then
    echo " VERIFICATION: PASS"
else
    echo " VERIFICATION: REVIEW REQUIRED -- see above."
fi
echo "======================================================================"
echo


# =====================================================================
# STEP 8 : Assemble
# =====================================================================
if [[ $FAILED -ne 0 ]]; then
    echo " Package not assembled. Resolve the items above and re-run."
    echo " Report: $REPORT"
    echo
    exit 1
fi

echo "STEP 8 : Assembling the package"
echo "----------------------------------------------------------------------"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

find_companion() {
    local n="$1"
    if   [[ -f "./$n"           ]]; then printf '%s' "./$n"
    elif [[ -f "$SCRIPT_DIR/$n" ]]; then printf '%s' "$SCRIPT_DIR/$n"
    else return 1; fi
}

COMPANIONS=(run_entity_dryrun.sh
            RUN_ALL.sql
            01_entitydev_preflight_checks.sql
            02_entitydev_prepare_account.sql
            99_entitydev_teardown.sql)

declare -a FOUND_C=() ; MISSING_C=0
for c in "${COMPANIONS[@]}"; do
    if p=$(find_companion "$c"); then
        FOUND_C+=("$p"); echo "  Found    $c   <- $p"
    else
        echo "  MISSING  $c"; MISSING_C=1
    fi
done

if [[ $MISSING_C -eq 1 ]]; then
    echo
    echo "  Cannot assemble -- the files above are supplied by the"
    echo "  development team, not generated here. Place them in this"
    echo "  directory or beside this script, then re-run."
    echo
    echo "  The transformed DDL in $DST/ is complete and correct; only"
    echo "  the packaging step was skipped."
    echo
    exit 1
fi

# Staleness check. A RUN_ALL.sql without the teardown step will run the
# deployment against a schema that was never cleared -- the guard then
# aborts and the whole run is wasted. Catch it here, not on the DBA's side.
RUNALL_SRC=""
for p in "${FOUND_C[@]}"; do
    [[ "$(basename "$p")" == "RUN_ALL.sql" ]] && RUNALL_SRC="$p"
done
if [[ -n "$RUNALL_SRC" ]]; then
    if ! grep -q '@@99_entitydev_teardown' "$RUNALL_SRC"; then
        echo
        echo "  *** STALE RUN_ALL.sql ***"
        echo "      $RUNALL_SRC"
        echo "      does not source the teardown step, so it will not clear"
        echo "      the sandbox before deploying. The deployment would then"
        echo "      abort on the empty-schema guard."
        echo
        echo "      Replace it with the current RUN_ALL.sql and re-run."
        echo
        exit 1
    fi
    echo "  RUN_ALL.sql includes the teardown step - OK"
fi

rm -rf "$PKG"; mkdir -p "$PKG"
for p in "${FOUND_C[@]}"; do cp "$p" "$PKG/"; done
if [[ "$DST_USER" != "ENTITYDEV" ]]; then
    $SED -i "s/\\bENTITYDEV\\b/${DST_USER}/g" "$PKG"/*.sql 2>/dev/null || true
    echo "  Retargeted companion scripts to ${DST_USER}"
fi
cp -r "$DST" "$PKG/"

cat > "$PKG/RUN_ORDER.txt" <<'ROEOF'
ENTITY DDL DRY RUN -- HOW TO RUN
=================================

There are two ways to do this. Pick whichever you prefer; they do the
same thing and produce the same logs.


OPTION A -- ONE COMMAND (recommended)
-------------------------------------

    ./run_entity_dryrun.sh

Handles everything: the checks, the account preparation, the dry run,
and bundling the logs. It detects multitenant databases and switches to
the right PDB on its own.

If it cannot find the account automatically:

    PDB=<pdb_name> ./run_entity_dryrun.sh
    SYS_CONNECT='sys/password@service AS SYSDBA' ./run_entity_dryrun.sh


OPTION B -- SQL*Plus, ONE SCRIPT
--------------------------------

    sqlplus / as sysdba @RUN_ALL.sql

Start connected as SYS. It does everything Option A does: locates the
account (switching PDB if needed), runs the checks, prepares the
account, then reconnects as ENTITYDEV and runs the DDL. It asks for the
service name and a password, once each.

Teardown is not included; it is run separately afterwards.


OPTION C -- SQL*Plus, STEP BY STEP
----------------------------------

If you would rather see each stage separately. Three scripts, in this
order, across TWO DIFFERENT CONNECTIONS.

  1. As SYS -- read-only checks
         sqlplus / as sysdba @01_entitydev_preflight_checks.sql
     Produces: entitydev_preflight.log

  2. As SYS -- prepare the account (prompts for a password)
         sqlplus / as sysdba @02_entitydev_prepare_account.sql
     Produces: entitydev_prepare.log

  3. As ENTITYDEV -- the dry run itself
         cd ENTITY_DDLs_DryRun
         sqlplus ENTITYDEV@<service> @master_run_entitydev.sql
     Produces: entitydev_dryrun_<timestamp>.log

  Later, once the results have been reviewed and you are asked to:
         sqlplus ENTITYDEV@<service> @99_entitydev_teardown.sql

Three things that matter in Option C:

  * Step 3 must NOT be run as SYS. The DDL contains DROP TABLE
    statements; connecting as ENTITYDEV is what keeps them confined to
    the sandbox. The script refuses to run as anyone else.

  * Step 3 must be run from INSIDE the ENTITY_DDLs_DryRun folder. It
    sources the child scripts by relative path.

  * On a multitenant database, steps 1 and 2 need the container set.
    Both scripts report which container they landed in, and carry a
    commented ALTER SESSION SET CONTAINER line near the top. Step 3
    connects through the service name, so it lands in the right PDB
    on its own.


WHAT TO SEND BACK
-----------------

Option A bundles everything into entity_dryrun_results_<timestamp>.tar.gz.

Option B: send the three .log files listed above.


READING THE LOG
---------------

The dry run continues past errors on purpose, so one pass captures
every problem. It will run to completion even when steps fail --
finishing is not the same as succeeding.

Expected and normal:
  * One ORA-00942 per table during the table step. The DDL drops each
    table before creating it, and the schema starts empty.
  * Synonyms pointing at ALS_LEGACY_REPLICA reporting success but
    being unusable. That schema does not exist here.

Report immediately, do not work around:
  * Any error naming ENTITY rather than ENTITYDEV.
  * Any DROP that SUCCEEDS on the first run.
  * Anything in the ISOLATION CHECK near the end. That should return
    no rows -- it is the evidence the ENTITY schema was untouched.
ROEOF
echo "  Wrote RUN_ORDER.txt (covers both the shell and SQL-only paths)"

# A CRLF shebang produces "bad interpreter" on the server, which is a
# baffling failure for someone who did nothing wrong.
if command -v dos2unix >/dev/null 2>&1; then
    dos2unix -q "$PKG"/*.sh 2>/dev/null || true
else
    $SED -i 's/\r$//' "$PKG"/*.sh 2>/dev/null || true
fi
chmod +x "$PKG"/*.sh 2>/dev/null || true
echo "  Line endings normalised, execute bit set"
echo "  Assembled $PKG/ ($(find "$PKG" -type f | wc -l | tr -d ' ') files)"

ARCHIVE=""
command -v zip >/dev/null 2>&1 && zip -rq "${PKG}.zip" "$PKG" && ARCHIVE="${PKG}.zip"
[[ -z "$ARCHIVE" ]] && tar czf "${PKG}.tar.gz" "$PKG" 2>/dev/null && ARCHIVE="${PKG}.tar.gz"

echo
echo "======================================================================"
echo " READY TO SEND"
echo "======================================================================"
echo
if [[ -n "$ARCHIVE" ]]; then
    echo "   $ARCHIVE"
else
    echo "   $PKG/   (no zip or tar available -- archive it yourself, from"
    echo "            WSL or Git Bash so the line endings survive)"
fi
echo
echo " One command on the DBA's side:"
echo "     cd $PKG && ./run_entity_dryrun.sh"
echo
echo " KEEP, do not send:"
echo "     $TSLIST"
echo "     Tablespaces the production package expects to exist. Now that"
echo "     the clauses are stripped, this is the only record."
echo
echo " Report: $REPORT"
echo

exit 0
