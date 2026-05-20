# Oracle DDL Generator

A standalone Java/Maven tool that connects to an Oracle database, extracts DDL for all objects in a target schema using `DBMS_METADATA`, and generates deployment-ready SQL scripts with a sequenced `master_run.sql`.

## Prerequisites

- **JDK 17+**
- **Maven 3.8+**
- Oracle database access with `SELECT` on `ALL_OBJECTS`, `ALL_TABLES`, `ALL_INDEXES`, `ALL_SYNONYMS`, `ALL_MVIEWS`, `ALL_DB_LINKS`
- `EXECUTE` privilege on `DBMS_METADATA` and `DBMS_OUTPUT`

## Quick Start

```bash
# 1. Edit connection details
vi src/main/resources/application.properties

# 2. Build and run
mvn clean compile exec:java

# -- OR build a fat JAR --
mvn clean package
java -jar target/oracle-ddl-generator-1.0.0.jar

# -- OR pass a custom config path --
java -jar target/oracle-ddl-generator-1.0.0.jar /path/to/my.properties
```

## Configuration

Edit `src/main/resources/application.properties`:

| Property | Description | Default |
|---|---|---|
| `db.url` | JDBC connection URL | *(required)* |
| `db.username` | Database user | *(required)* |
| `db.password` | Database password | *(required)* |
| `target.schema` | Schema to extract (UPPER CASE) | connected user |
| `output.dir` | Root output directory | `./ddl_output` |
| `include.storage` | Include tablespace/storage clauses | `false` |
| `include.grants` | Extract object grants | `true` |

## Output Structure

```
ddl_output/
└── ENTITY/
    ├── master_run.sql              ← Run this in SQL*Plus
    ├── 01_sequences/
    │   ├── entity_seq.sql
    │   └── ...
    ├── 02_types/
    ├── 03_tables/
    ├── 04_indexes/
    ├── 05_views/
    ├── 06_materialized_views/
    ├── 07_functions/
    ├── 08_procedures/
    ├── 09_packages/
    ├── 10_triggers/
    ├── 11_synonyms/
    ├── 12_db_links/
    └── 13_grants/
        └── object_grants.sql
```

## Deployment Order

The `master_run.sql` executes scripts in dependency-safe order:

1. **Sequences** — No dependencies
2. **Types** — May depend on other types
3. **Tables** — Depend on sequences, types
4. **Indexes** — Depend on tables
5. **Views** — Depend on tables, other views
6. **Materialized Views** — Depend on tables/views
7. **Functions** — Depend on tables/views
8. **Procedures** — Depend on tables/views/functions
9. **Packages** — Depend on all above
10. **Triggers** — Depend on tables
11. **Synonyms** — Depend on all objects
12. **Database Links**
13. **Grants** — Depend on all objects

## Generated Script Features

Each individual `.sql` file contains:

- **Header** with object type, qualified name, and generation timestamp
- **DROP IF EXISTS** via PL/SQL anonymous block with error suppression (e.g., ORA-00942 for tables, ORA-04043 for procedures)
- **CREATE** DDL extracted via `DBMS_METADATA.GET_DDL`
- Tables use `CASCADE CONSTRAINTS PURGE` on drop

The `master_run.sql` includes:

- `SET SERVEROUTPUT ON` for `DBMS_OUTPUT` messages
- `SPOOL` to timestamped log file
- `WHENEVER SQLERROR CONTINUE` (change to `EXIT` for fail-fast)
- Post-deployment `DBMS_UTILITY.COMPILE_SCHEMA` for invalid objects
- Validation query listing any remaining `INVALID` objects

## Running the Deployment

```bash
cd ddl_output/ENTITY
sqlplus username/password@database @master_run.sql
```

## DBMS_METADATA Transforms Applied

| Transform | Value | Purpose |
|---|---|---|
| `SQLTERMINATOR` | TRUE | Adds `;` or `/` terminators |
| `PRETTY` | TRUE | Formatted output |
| `SEGMENT_ATTRIBUTES` | configurable | Storage clauses |
| `TABLESPACE` | configurable | Tablespace references |
| `STORAGE` | configurable | Storage parameters |
| `CONSTRAINTS` | TRUE | Inline constraints |
| `REF_CONSTRAINTS` | TRUE | Foreign key constraints |
| `CONSTRAINTS_AS_ALTER` | TRUE | FKs as ALTER TABLE (safer ordering) |
