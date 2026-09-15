Here's one you can paste as-is, swapping in the filename:

```
Read @orders_update.pc and write documentation for it. Don't change the code.

Cover:
- What the program does and how it's invoked (args, env vars, expected inputs/outputs)
- Each function: purpose, parameters, return values, side effects
- Every EXEC SQL block: what it does in plain English, which tables and
  columns it touches, and the host/indicator variables it binds
- Cursors: where each is declared, opened, fetched, closed
- Transaction boundaries — where COMMIT and ROLLBACK happen, and what's
  left uncommitted on an error path
- Error handling: WHENEVER directives, SQLCA checks, which sqlcode values
  are handled and which fall through
- Any dynamic SQL, and which method it uses
- External calls: stored procedures, other C modules, shared libraries

Write it to ORDERS_UPDATE.md. Flag anything ambiguous instead of guessing.
```

The "don't change the code" line matters more than it looks — without it Claude Code will often "helpfully" tidy the source while it's in there.

Shorter version if you just want a quick read:

```
Document @orders_update.pc — what it does, each function, every EXEC SQL
block (tables, host variables, cursors), transaction boundaries, and
error handling. Output as markdown. Don't modify the file.
```

Two variations worth knowing:

- **Inline comments instead of a separate doc:** replace the last line with "Add a header comment block summarizing the program, and a brief comment above each EXEC SQL block. Change nothing else."
- **A whole directory of them:** "Document every .pc file in src/, one markdown file each in docs/, plus an index.md listing which tables each program reads and writes." That last part tends to be the genuinely useful artifact on legacy Pro*C — a table-to-program cross reference nobody has had since the original authors left.
