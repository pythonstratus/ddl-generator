Your project uses Oracle, so here's a PL/SQL block that does it in one shot. Run it as the schema owner:

```sql
BEGIN
  FOR t IN (
    SELECT table_name
    FROM   user_tables
    WHERE  table_name LIKE '%\_WEEKLY' ESCAPE '\'
  ) LOOP
    EXECUTE IMMEDIATE 'ALTER TABLE "' || t.table_name ||
                      '" RENAME TO "' || t.table_name || '_0904"';
  END LOOP;
END;
/
```

A couple of things to note:

- The `ESCAPE '\'` makes `_` match a literal underscore instead of acting as a wildcard, so you only get names that end in `_WEEKLY`, not e.g. `XWEEKLY`.
- If you want to preview before running, swap the loop body for `DBMS_OUTPUT.PUT_LINE(...)` or just run the `SELECT` on its own first.
- Renaming a table keeps indexes, constraints, and grants, but views, synonyms, and stored procedures referencing the old name will go invalid, so check `user_dependencies` afterward if anything points at these tables.

If you're actually running this against a different database (SQL Server, Postgres, etc.), let me know and I'll adjust it.
