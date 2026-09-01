Three tickets — a spike, then backend and frontend. Description, acceptance criteria and definition of done for each.

---

## Ticket 1 — SPIKE

**Title:** SPIKE: Locate where priority alpha ordering breaks in case assignment
**Timebox:** 4 hours

**Description**

Cases display out of priority alpha order across case assignment screens. Observed 01 Sep 2026 during the walkthrough with Sarah: a 103b ranked above Priority 99 cases, and a 99e ranked above a 99a on the same view. Priority alpha order is the master display rule for all of case assignment — there should be no screen or condition where it does not apply.

The two symptoms may have different causes. A 103b above 99 is consistent with lexical string comparison, since the character `1` sorts before `9`. A 99e above a 99a is not explained by that and points to either no ordering applied or ordering by a different field.

Determine where the defect sits before estimating the fix.

**Acceptance Criteria**

- Raw API response inspected on at least three case assignment surfaces
- Recorded: whether the response contains a model score or rank ordinal field
- Recorded: whether the response array arrives in correct priority alpha order
- Recorded: whether rendered order differs from received order
- Confirmed with Samuel or Diane whether modern reproduces the legacy model score calculation, or carries only the alpha letter
- Permitted sort list obtained from legacy (approximately eight options)
- Outcome recorded as backend, frontend or both, with an estimate for each

---

## Ticket 2 — Backend

**Title:** Apply priority alpha ordering in case assignment queries
**Estimate:** 16–24h, contingent on spike outcome

**Description**

Priority alpha order is the master rule for every case assignment surface: group summary, RO selection, query results, reports, pending, hold/skip and the mandatory accelerated screens.

Ordering has two levels:

1. Numeric priority ascending (99 before 101 before 103), then alpha letter ascending (a through e)
2. Within an identical alpha value, by model score rank. Cases sharing an alpha value are **not** interchangeable — the higher-ranked case is more productive and must display above. The score is calculated against balance by a dedicated table in legacy.

Ordering must be applied in the query. Ordering applied after fetch sorts each page independently, so pagination boundaries break even when each page looks internally correct.

**Acceptance Criteria**

- Given a result set spanning multiple priority bands, rows are ordered by numeric priority ascending, then alpha ascending
- Given rows sharing an identical priority alpha, they are ordered by model score rank
- Given a paginated result set, the last row of page 1 and the first row of page 2 are correctly ordered relative to each other
- Each row carries an explicit `sequenceRank` ordinal so consumers can verify ordering without reimplementing the model score formula
- Ordering is applied in the query, not in application code after fetch
- Ordering applies to every case assignment endpoint, including query results and reports
- Applying any permitted sort parameter retains priority alpha as the primary key

**Definition of Done**

- Regression test in CI asserting order against a fixture containing at least three cases sharing an alpha value, and at least two priority bands where the lower-priority band has a numerically longer label (99 and 103 minimum)
- Verified across all case assignment endpoints, not only the one that surfaced the defect
- Query plan reviewed for index coverage on the ordering keys
- Confirmed no client consumer is relying on ordering the response itself

---

## Ticket 3 — Frontend

**Title:** Render case assignment lists in server order and constrain sort options
**Estimate:** 8–12h

**Description**

Case assignment lists must render in the order the API supplies. Client-side sorting silently overrides server ordering and breaks the master rule.

Data grid libraries enable client-side sorting by default — AG Grid, MUI DataGrid and TanStack Table all do. A column header click reorders loaded rows in memory regardless of what the server sent.

Sorts that reorder across priority bands must not be offered at all: absent, not present-but-disabled. Legacy exposes roughly eight sorts across case assignment and four on the accelerated screen (case grade, case type, priority level code, zipcode), specifically because these are the only ones that do not break priority alpha order. Balance and assignment number are explicitly not sortable.

**Acceptance Criteria**

- Rendered row order matches the raw API response order exactly on every case assignment surface
- Client-side sorting is disabled in the grid component; header clicks issue a server request rather than reordering in memory
- Sort options are limited to the permitted legacy set; any option that can break priority alpha order is removed from the UI
- Create Query First Sort, Second Sort and Third Sort dropdowns are constrained to the same permitted set
- Fetching a subsequent page does not re-sort the combined set; appended rows render in received order
- Sort selection does not persist across navigation — returning to a list restores the default sequence
- Priority alpha is parsed as numeric priority plus alpha letter wherever the UI reasons about order, never compared as a string
- An unparseable priority alpha raises an error rather than falling back to a default position

**Definition of Done**

- Automated check asserting rendered order equals received order
- Verified on group summary, RO selection, query results, pending, hold/skip and the mandatory accelerated screens
- Grid component sorting configuration reviewed across every instance, not only the one that surfaced the defect

---

**Dependency:** Ticket 3's verification criteria can't fully pass until Ticket 2 ships, since the frontend can only prove it renders in received order once received order is correct. The grid-sorting disable and the sort-option constraint can start immediately and don't need to wait.
