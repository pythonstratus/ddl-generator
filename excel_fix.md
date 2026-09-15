Good — that settles it. The Java is correct and Excel is doing the damage on open. So the fix goes in the formatter, not in the query or the writer.

Put it on the three branches that emit bare digits. Not on `case 0` or `case 2` — those already have dashes, they're working, and adding a tab there would inject a stray character into good output.

```java
Integer type = asInt(tintype);
if (type == null) {
    return asExcelText(padded);
}
switch (type) {
    case 0:  return padded.substring(0, 3) + "-" + padded.substring(3, 5)
                    + "-" + padded.substring(5, 9);   // ###-##-#### already safe
    case 1:  return asExcelText(padded.substring(0, 9));
    case 2:  return padded.substring(0, 2) + "-" + padded.substring(2, 9);  // already safe
    default: return asExcelText(padded);
}
```

And next to `pad` in the helpers block:

```java
/**
 * Excel strips leading zeros from any field that parses as a number. A leading tab
 * forces text import so 001234567 survives. Only the bare-digit branches need this --
 * the dashed SSN and EIN formats are already non-numeric.
 */
private static String asExcelText(String digits) {
    return "\t" + digits;
}
```

Four things to check before you call it done:

- **Your CSV writer has to quote that field.** RFC-4180 quoting usually triggers on comma, quote and newline only — a tab won't trip it. The tab trick is far more reliable when the field comes out as `"→001234567"`. If your escaper doesn't quote it, force it for this column.
- **Test in the client's Excel version,** not yours. This behavior has never been consistent across versions and platforms, and Mac Excel in particular is unreliable here.
- **The tab is now real data.** Anything downstream that parses this CSV programmatically gets a leading tab on TIN. If anyone ingests these files into another system, tell them.
- **Your before/after diff regression will now show a delta** on that column. That's intended, but flag it so nobody treats it as a failed gate.

If Excel misbehaves in their version, dashes on `case 1` are the fallback — same protection, no invisible characters, but it changes what the column looks like versus the screen.

**Summary:** Module view CSV export — TIN leading zeros stripped when opened in Excel

---

**Issue reported**

Client reported that TIN values in the Module view CSV export were missing the leading `00` prefix.

**Investigation**

Confirmed the exported file is correct. The leading zeros are present in the raw CSV when opened in a plain text editor — the data loss happens at Excel import, not at export.

Excel parses any field that looks numeric as a number and drops leading zeros. `001234567` becomes `1234567` on open.

**Root cause**

`ModuleViewCsvFormatter.formattedTin(tin, tintype)` renders TIN conditionally on TIN Type:

- Type 0 (SSN) → `001-23-4567`
- Type 2 (EIN) → `00-1234567`
- Type 1 (SSN Invalid) → `001234567`
- `default` and null TIN Type → same bare nine digits

The dashed formats are non-numeric, so Excel leaves them as text and the zeros survive. That protection was never present on the three branches that emit bare digits, which is why only some records were affected.

Ruled out during investigation: the `pad()` helper (verified correct — left-pads with `'0'`), and the source data type.

**Fix**

Added an `asExcelText()` helper that prefixes a leading tab character, forcing Excel to import the field as text. Applied to the three bare-digit branches only — `case 1`, `default`, and the null-TIN-Type early return.

`case 0` and `case 2` are unchanged. They already render safely and adding the tab there would introduce a stray character into working output.

Scope is one method in one file. No change to SQL, the streaming export path, or any other column.

**Alternatives considered**

- `="001234567"` formula wrapper — rejected. A field beginning with `=` matches the CSV injection pattern and is likely to be flagged in a security review regardless of content.
- Dashes on Type 1 — viable fallback if the tab approach proves unreliable in the client's Excel version, but it changes the column's appearance versus the on-screen grid.
- `.xlsx` output with the column typed as text — the permanent fix for this whole class of issue, but a material change to the streaming export. Raising separately.
