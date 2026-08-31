# ENTITY DDL Dry Run — Runbook

**Target user:** `ENTITYDEV`
**Instance:** Exadata DEV — `VL1SMTBORAM7M01.MCC.IRS.GOV:1701/ALSDEV`
**Purpose:** Validate the ENTITY production DDL package before the Prod deployment.

---

## Part 1 — Which user, and why

`ENTITYDEV`. Per Jordan's schema table it is the *"Sandbox for modernizing ENTITY Schema"* (10 GB), and it is the only account in the ENTITY namespace. The remaining candidates are ALS-namespace schemas, and the three highlighted ones are 50 MB — far too small.

`phase2.sql` lines 33–46 tell us exactly how it was provisioned:

```sql
CREATE USER ENTITYDEV
    IDENTIFIED BY &ENTITYDEV_PW
    DEFAULT TABLESPACE als
    TEMPORARY TABLESPACE als      -- see Known Issue 1
    QUOTA 10G ON als;

GRANT CREATE SESSION      TO ENTITYDEV;
GRANT CREATE TABLE        TO ENTITYDEV;
GRANT CREATE VIEW         TO ENTITYDEV;
GRANT CREATE ANY TRIGGER  TO ENTITYDEV;   -- see Known Issue 2
GRANT CREATE ANY PROCEDURE TO ENTITYDEV;  -- see Known Issue 2
GRANT CREATE SEQUENCE     TO ENTITYDEV;
GRANT CREATE SYNONYM      TO ENTITYDEV;
```

**Do not re-run `phase2.sql`.** ENTITYDEV already exists; `CREATE USER` would fail with ORA-01920. That file is the historical record of how the account was built, nothing more.

---

## Part 2 — Do the scripts need the schema user changed?

**Yes — in every child DDL script.** The production package is fully schema-qualified with the `ENTITY.` prefix throughout, and `ENTITY` exists as a real account on this same instance.

What does **not** need changing:

- **`master_run.sql`** — every validation query uses `USER_*` views (`user_objects`, `user_tables`, `user_sequences`, …), which resolve to whoever is connected. The driver is schema-portable as written.

What **does** need changing — the six child scripts:

| Folder | File | Transformed? |
|---|---|---|
| sequences/ | ENTITY_SequencesProd.sql | Not yet |
| tables/ | ENTITY_TablesProd.sql | Not yet |
| indexes/ | ENTITY_IndexesProd.sql | Not yet |
| procedures/ | ENTITY_ProceduresProd.sql | Not yet |
| views/ | ENTITY_ViewsProd.sql | Not yet |
| synonyms/ | ENTITY_SynonymsProd.sql | **Done** — supplied |

### Why this is not optional

`GRANT CREATE ANY PROCEDURE TO ENTITYDEV` means *any schema*. A statement like

```sql
CREATE OR REPLACE PROCEDURE ENTITY.some_proc ...
```

run as ENTITYDEV would **succeed** and overwrite the real dev procedure. Nothing in the log would look wrong.

Two mitigations are in place, and both should be used:

1. Transform every `ENTITY.` reference to `ENTITYDEV.` (below).
2. Run Step 3 of `02_entitydev_prepare_account.sql`, which swaps the `ANY` privileges for schema-scoped ones. Any reference missed by the transform then fails loudly with ORA-01031 instead of silently succeeding.

### Transform recipe

Work on a **copy**. Leave `ENTITY_DDLs_Prod` untouched.

```bash
cp -r ENTITY_DDLs_Prod ENTITY_DDLs_DryRun
cd ENTITY_DDLs_DryRun

# Replace the schema prefix in every SQL file, in place
find . -name '*.sql' -exec sed -i 's/ENTITY\./ENTITYDEV./g' {} +
```

The pattern `ENTITY\.` matches only the prefix followed by a dot. It will not touch filenames like `ENTITY_TablesProd.sql`, and it will not double-apply to `ENTITYDEV.` (the character after `ENTITY` there is `D`, not `.`).

Then drop in the supplied files, replacing what `sed` produced:

- `ENTITY_DDLs_DryRun/master_run_entitydev.sql`
- `ENTITY_DDLs_DryRun/synonyms/ENTITY_SynonymsProd.sql`

*(The supplied synonyms file handles a case `sed` gets wrong — see Known Issue 4.)*

### Verify the transform before handing anything over

```bash
grep -rn -i 'ENTITY\.' --include='*.sql' . | grep -v -i 'ENTITYDEV\.'
```

**Expected output: nothing.** Any line returned is an untransformed reference that could reach the real ENTITY schema. Fix before proceeding.

---

## Part 3 — Execution order

Steps 1–3 are for the DBA. Step 4 can be run by either party.

| # | Script | Run as | Destructive? |
|---|---|---|---|
| 1 | `01_entitydev_preflight_checks.sql` | SYS | No — read-only |
| 2 | `02_entitydev_prepare_account.sql` | SYS | Alters ENTITYDEV only |
| 3 | `master_run_entitydev.sql` | ENTITYDEV | Creates objects in ENTITYDEV |
| 4 | `99_entitydev_teardown.sql` | ENTITYDEV | **Yes** — drops everything in ENTITYDEV |

### Step 1 — Pre-flight (SYS)

```bash
sqlplus / as sysdba @01_entitydev_preflight_checks.sql
```

Read-only. Ten checks, each stating what a PASS looks like. **Return `entitydev_preflight.log` before going further** — several later decisions depend on it.

### Step 2 — Prepare the account (SYS)

Every action in this script is commented out by default. Uncomment only what the pre-flight log shows is needed:

- **Password/unlock** — last login was 02-FEB-2026 and credentials have not been refreshed since March, so expect EXPIRED or LOCKED.
- **`GRANT CREATE MATERIALIZED VIEW`** — only if materialized views are in scope (see Open Question 2).
- **Privilege swap** — recommended, see Known Issue 2.

### Step 3 — The dry run (ENTITYDEV)

```bash
cd /path/to/ENTITY_DDLs_DryRun
sqlplus ENTITYDEV@ALSDEV @master_run_entitydev.sql
```

Connect **without** the password on the command line; SQL\*Plus prompts for it. A password passed as `user/pass@db` is visible to anyone running `ps` on a shared server.

Two guards run before any DDL: the script aborts if the connected user is not ENTITYDEV, and aborts if the schema is not empty.

Return `entitydev_dryrun_<timestamp>.log`.

### Step 4 — Teardown (ENTITYDEV)

Only after the log has been captured and returned.

```bash
sqlplus ENTITYDEV@ALSDEV @99_entitydev_teardown.sql
```

---

## Part 4 — How to read the log

**Completion does not mean success.** `WHENEVER SQLERROR CONTINUE` is deliberately retained so a single pass surfaces every failure. The script runs to the end regardless.

Read in this order:

1. **Both guards passed** — "OK - connected as ENTITYDEV", "OK - schema is empty".
2. **Each step's object count is non-zero.** A zero count usually means the `@@` filename was wrong and the step silently did nothing. Check filenames before concluding the DDL is broken.
3. **"Remaining INVALID Objects After Recompilation"** — the real result. Anything still invalid here needs investigating.
4. **"ISOLATION CHECK"** — must return **no rows**. This is the evidence that nothing outside ENTITYDEV was touched. Include it in what goes back to the client.

### Failures that are expected, not defects

| What you'll see | Why |
|---|---|
| 5 synonyms pointing at `ALS_LEGACY_REPLICA` report success but are unusable | That schema does not exist on this instance. Oracle permits synonyms to non-existent targets; using one raises ORA-00980. Already a deferred item. |
| Objects INVALID for want of cross-schema privileges | The ALSO/SYS grants identified in February exist for ENTITY, not ENTITYDEV. |
| "Grants Issued" returns no rows | `ENTITY_SynonymsProd.sql` contains no GRANT statements, despite the step being labelled "Synonyms & Grants". |

---

## Part 5 — Known issues found in the existing scripts

**1. `phase2.sql` line 37: `TEMPORARY TABLESPACE als`**
ALS is a permanent tablespace. Oracle rejects that assignment with ORA-12911, so ENTITYDEV must have been created with something else. Pre-flight Check 2 confirms what it actually has. A wrong TEMP assignment surfaces as a sort/space failure partway through index creation, not at connect time.

**2. `phase2.sql` lines 43–44: `CREATE ANY TRIGGER` / `CREATE ANY PROCEDURE`**
Too broad for a sandbox. Covered in Part 2 above.

**3. `master_run.sql` — SPOOL was broken**
Line 15 sets `DEFINE OFF`; line 26 then uses `SPOOL &log_filename`. With DEFINE off, `&` is a literal, so the timestamped log was never produced. Fixed in the supplied driver.

**4. `master_run.sql` — all six sourced filenames were wrong**
The original omits the `Prod` suffix (`ENTITY_Synonyms.sql`), but the files are named `ENTITY_<Type>Prod.sql`. Confirmed for tables and synonyms; the other four are inferred from the same convention and **should be checked against the folders**:

```bash
ls -1 sequences/ tables/ indexes/ procedures/ views/ synonyms/
```

**5. `master_run.sql` header says `Environment : Test`, `Generated : 18-FEB-2026`**
The February Test driver was copied into `ENTITY_DDLs_Prod` without being regenerated. That is why it is out of step with the folder contents.

---

## Part 6 — Open questions

**1. ~~Is `ENTITY_TablesProd.sql` free of `TABLESPACE` clauses?~~ — RESOLVED. See Part 7.**

**2. Are `functions/` and `materialized_views/` in scope?**
Neither is referenced by `master_run.sql`. Both are stubbed as commented-out steps in the supplied driver. If functions are already bundled inside the procedures file, delete Step 4b.

**3. What about the seed data?**
`entitles.sql`, `populate_week_data_holidays.sql`, and `rptname_mod.sql` sit in `tables/` and are referenced by nothing. If the client's definition of validating the deployment includes the data loads, they need steps of their own — and that changes the earlier "no data will be loaded" framing.

**4. `DIAL.Y2026W21_TINSUMMARY` — escalate independently of this dry run.**
`ENTITY_SynonymsProd.sql` line 49 points `TINSUMMARY` at a week-stamped table for 2026 week 21 (approximately late May). It is now week 35. This is carried unchanged into the **production** package. Either something repoints it at deploy time, or it is a live defect that the dry run will not catch, because the synonym will create successfully either way.

---

---

## Part 7 — `ENTITY_TablesProd.sql` findings

### 7.1 — Every table block opens with a DROP against ENTITY

```sql
DROP TABLE ENTITY.ACTDELETE CASCADE CONSTRAINTS;

CREATE TABLE ENTITY.ACTDELETE ( ... )
```

**Escalate this beyond the dry run.** The production package drops and recreates every table with `CASCADE CONSTRAINTS`. If ENTITY in Production already holds data, executing this package is data loss, not a deployment. Confirm with the client that Prod ENTITY is greenfield before the Prod run is scheduled. **A dry run in an empty sandbox cannot surface this** — the sandbox has nothing to lose.

For the dry run itself, the risk is contained but worth understanding:

- ENTITYDEV does **not** hold `DROP ANY TABLE` (phase2.sql never granted it), so an untransformed `DROP TABLE ENTITY.x` fails on a privilege error rather than dropping anything.
- **As SYS, every one of those drops succeeds.** A DBA's instinct on an unfamiliar deployment script is often to connect as SYSDBA. Do not run `master_run_entitydev.sql` as SYS. The connected-user guard enforces this, and that guard must not be removed.

On a clean run expect **one ORA-00942 per table** from the DROP statements. That is normal. See the Step 2 header in the master script for how to tell normal noise from a real problem.

### 7.2 — Tablespace: strip the clauses

Confirmed present, one standalone line per table:

```
TABLESPACE ENTITY
```

Strip them, for two independent reasons:

1. Tablespace `ENTITY` may not exist on ALSDEV → **ORA-00959**
2. Even if it does, ENTITYDEV's quota is `10G ON als` and nothing on `ENTITY` → **ORA-01950**

Stripped, objects fall into ENTITYDEV's default `als`, covered by the existing quota. Pre-flight Check 8 lists what actually exists on the instance.

```bash
# Deletes standalone TABLESPACE lines only.
# Safe with LOB storage clauses -- those are not standalone lines.
sed -i -E '/^[[:space:]]*TABLESPACE[[:space:]]+[A-Za-z0-9_$#"]+[[:space:]]*$/d' \
  tables/ENTITY_TablesProd.sql indexes/ENTITY_IndexesProd.sql

# Verify: anything returned is an inline clause the line-delete missed.
grep -rn -i 'TABLESPACE' --include='*.sql' .
```

### 7.3 — STORAGE clauses: leave alone

`INITIAL 64K, NEXT 1M` is small, so there is no quota pressure even across many tables. `PCTUSED` and `PCTINCREASE` are ignored on locally-managed tablespaces. No change needed.

### 7.4 — Date default depends on NLS

```sql
ACTDT  DATE  DEFAULT '01-JAN-1900'
```

A string literal used as a DATE default. This parses only when the session `NLS_DATE_FORMAT` is `DD-MON-YYYY` with English month names; otherwise **ORA-01861**.

The master script now reports the session NLS settings rather than forcing them. Forcing them would hide a mismatch in the dry run that would then reappear in Production. **Capture the reported values and compare them against the Production session before the Prod run.**

---

## What this dry run does and does not prove

**Does:** the DDL is syntactically valid, dependency ordering is correct, objects build, and PL/SQL compiles.

**Does not:** this is not a production rehearsal. ENTITYDEV sits in the shared `als` tablespace with a 10 GB quota rather than production's layout, the cross-schema grants differ, and it runs on the DEV Exadata instance.

Worth setting that expectation with the client up front, so an INVALID count in the log is not read as a script defect.
