#!/usr/bin/env bash
#
# =====================================================================
#  run_entity_dryrun.sh
#
#  ENTITY DDL dry run against the ENTITYDEV sandbox.
#
#  USAGE
#      ./run_entity_dryrun.sh              Run the dry run
#      ./run_entity_dryrun.sh teardown     Clean the sandbox afterwards
#
#  This script does everything in one pass:
#      1. Checks its own prerequisites
#      2. Runs read-only pre-flight checks as SYS
#      3. Stops if anything looks wrong -- otherwise continues
#      4. Prepares the ENTITYDEV account as SYS
#      5. Runs the DDL dry run as ENTITYDEV
#      6. Bundles every log into one file to send back
#
#  It never touches the ENTITY schema, and it never runs the DDL with
#  DBA privileges.
#
#  Questions: contact the development team before improvising. Nothing
#  here is urgent enough to guess at.
# =====================================================================

set -uo pipefail

cd "$(dirname "$0")"

# ---------------------------------------------------------------------
# Settings
# ---------------------------------------------------------------------
readonly SANDBOX_USER="ENTITYDEV"
readonly DEFAULT_TNS="ALSDEV"
readonly DDL_DIR="ENTITY_DDLs_DryRun"
readonly MASTER="master_run_entitydev.sql"
readonly STAMP="$(date '+%Y%m%d_%H%M%S')"
readonly WORKLOG="dryrun_session_${STAMP}.log"

RC=0

# ---------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------
say()   { printf '%s\n' "$*" | tee -a "$WORKLOG"; }
head1() { say ""; say "======================================================================"; say " $*"; say "======================================================================"; }
ok()    { say "  [OK]    $*"; }
warn()  { say "  [WARN]  $*"; }
fail()  { say "  [ERROR] $*"; }

die() {
    say ""
    say "======================================================================"
    say " STOPPED"
    say "======================================================================"
    say ""
    say " $*"
    say ""
    say " Nothing further has been run."
    say " Please send this file to the development team: $WORKLOG"
    say ""
    exit 1
}

# ---------------------------------------------------------------------
# SQL*Plus wrappers
#
# Connects via /nolog + CONNECT so the password never appears in the
# process list where other users on this server could read it.
#
# SYS_CONNECT and PDB_NAME are resolved in STEP 2. On a multitenant
# database, "/ AS SYSDBA" lands in CDB$ROOT rather than the PDB that
# holds ENTITYDEV, so a container switch is issued when one is needed.
# ---------------------------------------------------------------------
SYS_CONNECT="${SYS_CONNECT:-/ AS SYSDBA}"
PDB_NAME="${PDB:-}"

run_sql_as_sys() {
    local script="$1"
    local container_sql=""
    [[ -n "$PDB_NAME" ]] && container_sql="ALTER SESSION SET CONTAINER = ${PDB_NAME};"
    sqlplus -S /nolog <<SQLEOF
WHENEVER OSERROR EXIT 9
CONNECT ${SYS_CONNECT}
${container_sql}
@${script}
SQLEOF
}

# Runs an inline query as SYS, in the correct container, and returns
# the bare result.
query_as_sys() {
    local sql="$1"
    local container_sql=""
    [[ -n "$PDB_NAME" ]] && container_sql="ALTER SESSION SET CONTAINER = ${PDB_NAME};"
    sqlplus -S /nolog <<SQLEOF 2>/dev/null
CONNECT ${SYS_CONNECT}
SET HEADING OFF FEEDBACK OFF PAGESIZE 0 VERIFY OFF TERMOUT ON
WHENEVER SQLERROR CONTINUE
${container_sql}
${sql}
EXIT
SQLEOF
}

run_sql_as_sandbox() {
    local script="$1"
    sqlplus -S /nolog <<SQLEOF
WHENEVER OSERROR EXIT 9
CONNECT ${SANDBOX_USER}/"${SANDBOX_PW}"@${TNS_ALIAS}
@${script}
SQLEOF
}

: > "$WORKLOG"

# =====================================================================
# TEARDOWN MODE
#
#   ./run_entity_dryrun.sh teardown
#
# Drops every object in the sandbox. Run only when the development team
# has confirmed the results are reviewed.
# =====================================================================
if [[ "${1:-}" == "teardown" ]]; then

    head1 "ENTITY SANDBOX TEARDOWN  --  ${STAMP}"

    command -v sqlplus >/dev/null 2>&1 \
        || die "sqlplus was not found on the PATH. Load the Oracle environment first."

    [[ -f 99_entitydev_teardown.sql ]] \
        || die "Missing 99_entitydev_teardown.sql. Request a fresh copy of the package."

    say ""
    say "  This drops EVERY object owned by ${SANDBOX_USER}."
    say "  It cannot affect any other schema -- the script stops unless"
    say "  the connected user is exactly ${SANDBOX_USER}."
    say ""
    say "  Only continue if the development team has asked you to."
    say ""

    read -r -p "  Type CONFIRM to proceed: " CONFIRM
    [[ "$CONFIRM" == "CONFIRM" ]] || die "Not confirmed. Nothing was dropped."

    read -r -p "  TNS alias / service name [${DEFAULT_TNS}]: " TNS_ALIAS
    TNS_ALIAS="${TNS_ALIAS:-$DEFAULT_TNS}"

    say ""
    say "  Setting a working password for ${SANDBOX_USER} so this script"
    say "  can connect. Choose anything -- it is only used here."
    say ""

    while true; do
        read -r -s -p "  Password: " SANDBOX_PW; echo
        read -r -s -p "  Confirm : " PW2; echo
        if [[ -z "$SANDBOX_PW" ]]; then
            echo "  Password cannot be empty."
        elif [[ "$SANDBOX_PW" != "$PW2" ]]; then
            echo "  Passwords did not match. Try again."
        else
            break
        fi
    done
    unset PW2

    TD_SQL="generated_teardown_pw_${STAMP}.sql"
    {
        echo "SET ECHO ON"
        echo "ALTER USER ${SANDBOX_USER} ACCOUNT UNLOCK;"
        echo "ALTER USER ${SANDBOX_USER} IDENTIFIED BY \"${SANDBOX_PW}\";"
        echo "EXIT"
    } > "$TD_SQL"

    run_sql_as_sys "$TD_SQL" >> "$WORKLOG" 2>&1
    rm -f "$TD_SQL"

    say ""
    say "  Dropping objects..."
    say ""

    run_sql_as_sandbox "99_entitydev_teardown.sql" 2>&1 | tee -a "$WORKLOG"

    say ""
    say "======================================================================"
    say " TEARDOWN FINISHED"
    say "======================================================================"
    say ""
    say " Please send entitydev_teardown.log to the development team."
    say " If any objects are listed as remaining, report them rather than"
    say " running this again."
    say ""
    exit 0
fi

head1 "ENTITY DDL DRY RUN  --  ${STAMP}"
say " Sandbox schema : ${SANDBOX_USER}"
say " Session log    : ${WORKLOG}"


# =====================================================================
# STEP 1 : Prerequisites
# =====================================================================
head1 "STEP 1 of 6 : Checking prerequisites"

command -v sqlplus >/dev/null 2>&1 \
    || die "sqlplus was not found on the PATH. Load the Oracle environment first."
ok "sqlplus found: $(command -v sqlplus)"

for f in 01_entitydev_preflight_checks.sql 99_entitydev_teardown.sql; do
    [[ -f "$f" ]] || die "Missing required file: $f
 The package is incomplete. Please request a fresh copy."
done
ok "Support scripts present"

[[ -d "$DDL_DIR" ]] || die "Missing directory: $DDL_DIR
 The package is incomplete. Please request a fresh copy."

[[ -f "$DDL_DIR/$MASTER" ]] || die "Missing: $DDL_DIR/$MASTER
 The package is incomplete. Please request a fresh copy."
ok "DDL package present"

# --- Safety net -------------------------------------------------------
# The DDL must already have been retargeted from ENTITY to ENTITYDEV
# before it was sent. If any reference survived, that statement would
# act on the real ENTITY schema. Refuse to run.
say ""
say "  Verifying the DDL is correctly retargeted..."

LEFTOVER=$(grep -rniE '\bENTITY\.' --include='*.sql' "$DDL_DIR" 2>/dev/null \
           | grep -viE '\bENTITYDEV\.' || true)

if [[ -n "$LEFTOVER" ]]; then
    say ""
    printf '%s\n' "$LEFTOVER" | head -20 | sed 's/^/      /' | tee -a "$WORKLOG"
    say ""
    die "The DDL still contains references to the ENTITY schema.

 This package was not fully prepared before it was sent. Running it
 could affect the real ENTITY schema.

 Do not attempt to edit these files. Send the lines above to the
 development team and request a corrected package."
fi
ok "No stray ENTITY references -- safe to proceed"


# =====================================================================
# STEP 2 : Connection details
# =====================================================================
head1 "STEP 2 of 6 : Connection details"

say ""
say "  This script connects twice:"
say "    - as SYS, using operating system authentication, for the checks"
say "    - as ${SANDBOX_USER}, to run the DDL"
say ""

read -r -p "  TNS alias / service name [${DEFAULT_TNS}]: " TNS_ALIAS
TNS_ALIAS="${TNS_ALIAS:-$DEFAULT_TNS}"

say ""
say "  ${SANDBOX_USER} was last used in February and its password has not"
say "  been maintained since March, so it is almost certainly expired."
say "  This script will reset it. Please choose a new password now."
say "  It is only used for this sandbox."
say ""

while true; do
    read -r -s -p "  New password for ${SANDBOX_USER}: " SANDBOX_PW; echo
    read -r -s -p "  Confirm: " PW2; echo
    if [[ -z "$SANDBOX_PW" ]]; then
        echo "  Password cannot be empty."
    elif [[ "$SANDBOX_PW" != "$PW2" ]]; then
        echo "  Passwords did not match. Try again."
    else
        break
    fi
done
unset PW2

say ""
ok "Target: ${SANDBOX_USER}@${TNS_ALIAS}"
say "  (the password is not written to any log)"


# ---------------------------------------------------------------------
# Locate ENTITYDEV -- non-CDB, or inside a PDB
#
# "/ AS SYSDBA" lands in CDB$ROOT on a multitenant database. DBA_USERS
# there does not show users that live in a PDB, so the account would
# look missing. Check the current container first; if the account is
# not visible, search the PDBs and switch.
# ---------------------------------------------------------------------
say ""
say "  Locating ${SANDBOX_USER}..."

CUR_CONTAINER=$(query_as_sys \
  "SELECT NVL(SYS_CONTEXT('USERENV','CON_NAME'),'NON-CDB') FROM dual;" \
  | tr -d ' \r\n')
[[ -n "$CUR_CONTAINER" ]] && say "  Connected container : ${CUR_CONTAINER}"

FOUND_LOCAL=$(query_as_sys \
  "SELECT COUNT(*) FROM dba_users WHERE username = '${SANDBOX_USER}';" \
  | tr -d ' \r\n')

if [[ "$FOUND_LOCAL" == "1" ]]; then
    ok "Found in the current container"
else
    say "  Not in this container -- searching pluggable databases..."

    PDB_NAME=$(query_as_sys \
      "SELECT p.name FROM cdb_users u JOIN v\$pdbs p ON p.con_id = u.con_id
        WHERE u.username = '${SANDBOX_USER}' AND ROWNUM = 1;" \
      | tr -d ' \r\n' | grep -iE '^[A-Za-z0-9_$#]+$' || true)

    if [[ -n "$PDB_NAME" ]]; then
        ok "Found in PDB: ${PDB_NAME}"
        say "  All SYS operations will run with:"
        say "      ALTER SESSION SET CONTAINER = ${PDB_NAME};"

        RECHECK=$(query_as_sys \
          "SELECT COUNT(*) FROM dba_users WHERE username = '${SANDBOX_USER}';" \
          | tr -d ' \r\n')
        [[ "$RECHECK" == "1" ]] \
            || die "Switched to container ${PDB_NAME} but ${SANDBOX_USER} is
 still not visible. Send ${WORKLOG} to the development team."
        ok "Container switch verified"
    else
        die "Could not find ${SANDBOX_USER} in this container or in any PDB.

 If this is a multitenant database and you know which PDB holds the
 account, re-run with it named explicitly:

     PDB=<pdb_name> ./run_entity_dryrun.sh

 If SYS needs a password rather than OS authentication:

     SYS_CONNECT='sys/password@service AS SYSDBA' ./run_entity_dryrun.sh

 Otherwise please send ${WORKLOG} to the development team."
    fi
fi


# =====================================================================
# STEP 3 : Pre-flight checks  (read-only, as SYS)
# =====================================================================
head1 "STEP 3 of 6 : Pre-flight checks (read-only)"

say ""
say "  Running 01_entitydev_preflight_checks.sql as SYS."
say "  This only reads -- nothing is created, altered or dropped."
say ""

run_sql_as_sys "01_entitydev_preflight_checks.sql" >> "$WORKLOG" 2>&1
PF_RC=$?

if [[ $PF_RC -eq 9 ]]; then
    die "Could not connect as SYSDBA.
 Check that ORACLE_SID / ORACLE_HOME are set for this instance."
fi

[[ -f entitydev_preflight.log ]] \
    && ok "Pre-flight log written: entitydev_preflight.log" \
    || warn "Expected entitydev_preflight.log was not produced"


# =====================================================================
# STEP 4 : Gates
#
# Decides whether it is safe to continue, and what the account needs.
# =====================================================================
head1 "STEP 4 of 6 : Evaluating results"

GATE=$(query_as_sys "
SELECT (SELECT COUNT(*) FROM dba_users   WHERE username = '${SANDBOX_USER}')
       || '|' ||
       (SELECT COUNT(*) FROM dba_objects WHERE owner    = '${SANDBOX_USER}')
       || '|' ||
       NVL((SELECT account_status FROM dba_users WHERE username = '${SANDBOX_USER}'), 'MISSING')
       || '|' ||
       NVL((SELECT COUNT(*) FROM dba_sys_privs
            WHERE grantee = '${SANDBOX_USER}' AND privilege = 'CREATE ANY PROCEDURE'), 0)
       || '|' ||
       NVL((SELECT COUNT(*) FROM dba_sys_privs
            WHERE grantee = '${SANDBOX_USER}' AND privilege = 'CREATE ANY TRIGGER'), 0)
       || '|' ||
       NVL((SELECT t.contents FROM dba_users u
            JOIN dba_tablespaces t ON t.tablespace_name = u.temporary_tablespace
            WHERE u.username = '${SANDBOX_USER}'), 'UNKNOWN')
FROM dual;")

GATE="$(printf '%s' "$GATE" | tr -d ' \r\n')"

IFS='|' read -r G_EXISTS G_OBJECTS G_STATUS G_ANYPROC G_ANYTRIG G_TEMP <<< "$GATE"

if [[ -z "${G_EXISTS:-}" ]]; then
    die "Could not read the account state from the database.
 Send ${WORKLOG} to the development team."
fi

say ""
say "  Account exists       : ${G_EXISTS}  (1 = yes)"
say "  Objects in schema    : ${G_OBJECTS}"
say "  Account status       : ${G_STATUS}"
say "  Temp tablespace type : ${G_TEMP}"
say ""

[[ "$G_EXISTS" == "1" ]] \
    || die "The ${SANDBOX_USER} account does not exist on this instance.
 Stop here and contact the development team."
ok "Account exists"

if [[ "$G_OBJECTS" != "0" ]]; then
    die "${SANDBOX_USER} already contains ${G_OBJECTS} object(s).

 The sandbox must be empty for the results to mean anything.

 If those objects are genuinely disposable, run:
     ./run_entity_dryrun.sh teardown
 and then run this script again.

 If you are not certain they are disposable, stop and ask."
fi
ok "Schema is empty"

if [[ "$G_TEMP" != "TEMPORARY" ]]; then
    warn "Temporary tablespace reports as '${G_TEMP}', expected TEMPORARY."
    warn "Noted -- this does not block the run, but please mention it in your reply."
else
    ok "Temporary tablespace is correct"
fi

[[ "$G_STATUS" == "OPEN" ]] && ok "Account is open" \
                            || warn "Account is ${G_STATUS} -- will be reset below"


# =====================================================================
# STEP 5 : Prepare the account  (as SYS)
#
# Generated from what STEP 4 found, so there is nothing to uncomment.
# =====================================================================
head1 "STEP 5 of 6 : Preparing the ${SANDBOX_USER} account"

GEN_SQL="generated_prepare_${STAMP}.sql"

{
    echo "-- Generated by run_entity_dryrun.sh at $(date '+%Y-%m-%d %H:%M:%S')"
    echo "-- Applies only to ${SANDBOX_USER}."
    echo "SET ECHO ON"
    echo "SET FEEDBACK ON"
    echo "WHENEVER SQLERROR CONTINUE"
    echo "SPOOL entitydev_prepare_${STAMP}.log"
    echo
    echo "ALTER USER ${SANDBOX_USER} ACCOUNT UNLOCK;"
    echo "ALTER USER ${SANDBOX_USER} IDENTIFIED BY \"${SANDBOX_PW}\";"
    echo

    if [[ "$G_ANYPROC" == "1" ]]; then
        echo "-- Narrow CREATE ANY PROCEDURE to the sandbox schema only."
        echo "-- ANY would let this account create objects inside ENTITY."
        echo "REVOKE CREATE ANY PROCEDURE FROM ${SANDBOX_USER};"
        echo "GRANT  CREATE PROCEDURE     TO ${SANDBOX_USER};"
        echo
    fi

    if [[ "$G_ANYTRIG" == "1" ]]; then
        echo "-- Same treatment for CREATE ANY TRIGGER."
        echo "REVOKE CREATE ANY TRIGGER FROM ${SANDBOX_USER};"
        echo "GRANT  CREATE TRIGGER     TO ${SANDBOX_USER};"
        echo
    fi

    echo "PROMPT >> Privileges now held by ${SANDBOX_USER}:"
    echo "SELECT privilege FROM dba_sys_privs WHERE grantee = '${SANDBOX_USER}' ORDER BY privilege;"
    echo "SPOOL OFF"
    echo "EXIT"
} > "$GEN_SQL"

say ""
say "  Actions being applied:"
say "    - unlock the account and set the password you chose"
[[ "$G_ANYPROC" == "1" ]] && say "    - narrow CREATE ANY PROCEDURE to CREATE PROCEDURE"
[[ "$G_ANYTRIG" == "1" ]] && say "    - narrow CREATE ANY TRIGGER to CREATE TRIGGER"
say ""

run_sql_as_sys "$GEN_SQL" >> "$WORKLOG" 2>&1
ok "Account prepared"

# The generated file contains the password. Remove it immediately.
rm -f "$GEN_SQL"
ok "Temporary script removed"


# =====================================================================
# STEP 6 : The dry run  (as ENTITYDEV -- never as SYS)
# =====================================================================
head1 "STEP 6 of 6 : Running the DDL dry run"

say ""
say "  Connecting as ${SANDBOX_USER} and running the deployment."
say ""
say "  IMPORTANT -- the script reports errors as it goes. That is by"
say "  design: it continues past failures so a single pass captures"
say "  everything. A wall of ORA-00942 messages during the table step"
say "  is expected and normal."
say ""
say "  Please let it finish. Do not interrupt it."
say ""

pushd "$DDL_DIR" >/dev/null || die "Could not enter $DDL_DIR"

sqlplus -S /nolog <<SQLEOF 2>&1 | tee -a "../${WORKLOG}"
WHENEVER OSERROR EXIT 9
CONNECT ${SANDBOX_USER}/"${SANDBOX_PW}"@${TNS_ALIAS}
@${MASTER}
SQLEOF
DR_RC=$?

popd >/dev/null || true

if [[ $DR_RC -eq 9 ]]; then
    die "Could not connect as ${SANDBOX_USER}.
 The password reset may not have taken effect.
 Send ${WORKLOG} to the development team."
fi

DRYLOG=$(ls -1t "$DDL_DIR"/entitydev_dryrun_*.log 2>/dev/null | head -1 || true)

if [[ -n "$DRYLOG" ]]; then
    ok "Dry run log: $DRYLOG"
else
    warn "No dry run log found -- check ${WORKLOG} for the reason"
    RC=1
fi


# =====================================================================
# Bundle
# =====================================================================
head1 "Collecting results"

BUNDLE="entity_dryrun_results_${STAMP}.tar.gz"

FILES=()
[[ -f entitydev_preflight.log ]]        && FILES+=("entitydev_preflight.log")
[[ -f "entitydev_prepare_${STAMP}.log" ]] && FILES+=("entitydev_prepare_${STAMP}.log")
[[ -n "$DRYLOG" ]]                      && FILES+=("$DRYLOG")
FILES+=("$WORKLOG")

if tar czf "$BUNDLE" "${FILES[@]}" 2>/dev/null; then
    ok "Created: $BUNDLE"
    BUNDLED=1
else
    warn "Could not create the archive -- please attach the files individually."
    BUNDLED=0
fi

say ""
say "======================================================================"
say " FINISHED"
say "======================================================================"
say ""
say " Please reply to the development team and attach:"
say ""
if [[ "${BUNDLED:-0}" == "1" ]]; then
    say "     $BUNDLE"
else
    for f in "${FILES[@]}"; do say "     $f"; done
fi
say ""
say " Please also mention anything marked [WARN] above."
say ""
say " The sandbox still holds the objects that were created. Leave them"
say " in place until the development team confirms the results have been"
say " reviewed. They will ask you to run:"
say ""
say "     ./run_entity_dryrun.sh teardown"
say ""
say " Thank you."
say ""

exit $RC
