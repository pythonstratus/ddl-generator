# Mandatory Accelerated backend — revision 2

What changed since the version in the project files, why, and what you have to decide.

Baseline: `mandatory-accelerated-backlog-v2.md`. All 15 confirmed rules still hold; nothing below
reinterprets a rule. Every change is either a defect fix, a gap against the backlog, or an
alignment with ENTITY's actual conventions.

---

## 1. One thing to decide before you read the rest

**The module no longer uses JPA.** `CaseSelection` and `AuditEvent` were `@Entity` classes with
Spring Data repositories, while the eligibility and read paths in the same module used
`NamedParameterJdbcTemplate`. That split was assumption 1 in the previous README, flagged as
"structural, rewrite if wrong".

It was wrong. Everything else in ENTITY that touches these tables uses Spring JDBC —
`NamedParameterJdbcTemplate` under `@Qualifier("secondaryNamedJdbcTemplate")`, `MapSqlParameterSource`,
SQL held as constants or in `.sql` files. So the module now does too, throughout.

The change is not cosmetic. Under JPA, `selection.unpick()` persisted itself through dirty
checking — invisible at the call site, and a silent no-op the day someone moves that call outside a
transaction. On a control function that is a bad way to fail. Callers now save explicitly.

**If ENTITY's case assignment module really is JPA-based,** say so and this reverses cleanly: the
two classes go back to `@Entity`, the two `Jdbc*Repository` classes are deleted, and nothing else
in the module changes. The SQL, the rules, the contracts and the tests all carry over either way.

---

## 2. Defects fixed

Ordered by what they would have cost in production.

### 2.1 The group query had drifted from the RO query

`AcceleratedCaseRepository.findForGroup` restated the eligibility predicate by hand instead of
using `EligibilitySql.ELIGIBILITY_PREDICATE` — and the hand-written copy **omitted the
grade-criteria clause**. The group screen would have listed cases no RO in the group could take,
and reconciliation check 2 would have failed the first time it ran against real data.

This is precisely the failure mode `EligibilitySql`'s own class comment says the design exists to
prevent, so the fix is structural rather than a one-line patch. The predicate is now composed from
fragments, and both the RO and group forms are built from the same ones:

```
ELIGIBILITY_PREDICATE       = CASE_CLAUSES + RO_ALIGNMENT_AND_GRADE    + EXISTING_RULES
GROUP_ELIGIBILITY_PREDICATE = CASE_CLAUSES + GROUP_ALIGNMENT_AND_GRADE + EXISTING_RULES
```

The group list is now the union of the RO lists **by construction**, not by review. Two tests in
`PredicateComposition` fail if anyone unpicks that.

One detail inside `GROUP_ALIGNMENT_AND_GRADE`: the ZIP join and the grade join sit inside a single
`EXISTS`, so both must be satisfied by the *same* RO. Split across two, the group list would admit
a case one RO can take by ZIP and a different RO can take by grade — which no RO can take at all.

### 2.2 The wrong RO's counts came back from an assignment

`assign()` returned `counts(target)` and derived `restrictionLifted` from it. On a rebalancing
assignment — a case aligned to 3921, assigned to 3937, which is the group screen's entire purpose —
the counts that move belong to **3921**. The target gains nothing.

So the response reported on an RO nothing had happened to. The screen's queued count would not have
decremented and the restriction would not have appeared to lift, on exactly the workflow rule 6
exists to enable. Now: counts are read for the alignment RO, both ROs are invalidated, and
`AssignmentResult` carries `alignmentRoAssignmentNumber` so the client knows which header to
update.

### 2.3 A case could be pulled out of another group

Rule 6 opens the assignment *target* right up: any RO in the group, aligned or not. It says nothing
about the case. The previous version checked only that the target RO was a group member, so a
manager who knew a TIN could assign a case aligned to **another group's** RO into their own — and
it would simply leave the other group's queue.

Added: the alignment RO must also be in the effective group. New `CrossGroupCaseException`, 409,
and a distinct `BLOCKED_OUT_OF_GROUP` audit outcome so an attempted scope escape does not share a
bucket with an ordinary refusal in a compliance report. The aspect applies the same check before
any guarded call.

### 2.4 Blocked Auto Select returned 500, not 409

`CaseAssignmentExceptionHandler` was scoped `assignableTypes = MandatoryAcceleratedController.class`.
But the enforcement aspect sits on the **existing** selection services, so a blocked Auto Select
arrives through the existing Case Assignment controller — where the advice did not apply. It would
have fallen through to a bare 500.

The one thing FE-B most needs, a clear message with the count and a route from every blocked path,
would have worked only on the new screen. The handler is now global (renamed
`MandatoryAcceleratedExceptionHandler`), at `HIGHEST_PRECEDENCE`, and handles only the four
exception types this module defines. It deliberately no longer handles `IllegalArgumentException` —
globally, that would change how unrelated ENTITY controllers report their own validation failures.

### 2.5 The cross-request status cache could serve a stale "restriction cleared"

`@Cacheable` on `restrictionActive` with `@CacheEvict` on write. Three problems: the eviction was
not transaction-bound, so a read arriving between evict and commit repopulated the cache from
uncommitted state; the write path reached `invalidate()` by injecting the *concrete* service class,
which only works while Spring produces CGLIB proxies; and a JVM-local cache cannot honour
"invalidate on write, never TTL" across more than one instance.

Replaced with `RestrictionStatusCache`, scoped to a single HTTP request. Every request recomputes
once and is internally consistent for the rest of its own work; nothing can observe a value written
by another request. `invalidate` moved onto the interface. `countsUncached` added for the write
path. Eviction registered `afterCompletion` as well as immediately.

The reasoning is written out in the class comment, including what to do instead if measurement
later says the aggregate is too expensive to run once per request. The answer is not a TTL.

### 2.6 Group Summary was an N+1, and its header double-counted

The backlog says plainly: "Roughly eight employees per group — one grouped query, not a loop." It
was a loop, on the screen a manager opens first every time. Now one grouped query with a `DISTINCT`
CTE (an RO with three ZIPs and two grade rows would otherwise count one case six times) and a
`LEFT JOIN` so rule 14 holds — every employee listed, zeroes included.

Separately, the group screen header summed the per-RO counts. ZIP alignment is not exclusive, so a
case aligned to two ROs was counted twice and the header read higher than the list beneath it. The
header now comes from a group-wide aggregate.

### 2.7 The audit trail recorded two things that never happened

- Every permitted method was filed as "sanctioned workaround used", so every ordinary accelerated
  assignment landed in the exception-usage log. The report compliance will actually ask for — was
  Query or Assign by TIN used while the restriction was active, and by whom — would have returned
  the entire day's work. Now filtered on `isSanctionedWorkaround()`, which is exactly the two.
- `QueueControlCommand.selectionMethod()` returned `AUTO_SELECT`. A refused Hold/Skip was therefore
  recorded as an attempted Auto Select. New `SelectionMethod.QUEUE_CONTROL`.

### 2.8 Smaller ones

| Was | Now |
|---|---|
| `ORDER BY ci.model_score DESC` | `DESC NULLS LAST`. Oracle sorts nulls **first** on DESC, so an unscored case outranked every scored one |
| `ORDER BY ci.qind_status_rank` | Derived with a CASE on `selection_status`. The old column appears in no screenshot, migration or mapping table |
| `evaluateForUpdate` appended `FOR UPDATE` | Removed. The row is already locked by `lockForAssignment`; a second lock in a different statement order across two paths is a deadlock waiting for a busy morning |
| `if (alpha == null)` after `String.valueOf(...)` | `String.valueOf(null)` returns the string `"null"`, so that branch could never fire |
| `ResultSetMetaData` walked per row to test for a column | Constructor flag. The query already knows |
| Assignment returned 200 | 201, matching diagram 2.4 |
| Reconciliation compared list size against listed count | Both derived from the same predicate — a tautology that could not fail. Replaced with checks that can |

---

## 3. Gaps against the backlog, now filled

- **BE-B subtask 4, case detail passthrough.** Was missing entirely. Added as `CaseDetailPort`
  with the week-one risk written on it: a queued case has no assignment context, so if the existing
  case-detail endpoint requires one it returns nothing for exactly the cases this screen is about.
- **`AssignmentContext` was an unimplemented stub.** A default now ships reading ENTITY's `seid`
  request attribute, uppercased at the boundary, registered `@ConditionalOnMissingBean`. **The
  attribute carrying "Viewing as: Group NNNNNN" is a guess and is marked VERIFY** — if it resolves
  null, enforcement silently falls back to the analyst's home group, which is the bypass the
  backlog calls the most likely security gap in the epic.
- **`CaseInventoryWriteRepository` was unimplemented.** A reference JDBC implementation now ships,
  also `@ConditionalOnMissingBean`, so an adapter onto your existing service wins. It does the
  assignment-number rewrite that actually moves a case out of the queued count — queue membership
  derives from the number, not from a separate flag.
- **Append-only was a comment.** The migration now carries the `REVOKE UPDATE, DELETE` that makes
  it true.
- **The migration assumed columns that may not exist.** It added three columns to `case_selection`
  and assumed identity, actor and timestamp columns were already there. Now adds them, with a
  `user_tab_columns` query in section 0 to run first.

`RevenueOfficerLookup` and `CaseDetailPort` are deliberately **not** defaulted. Both need data this
module has no business owning, and a plausible-looking default for either is worse than a startup
failure that names what is missing.

---

## 4. What I still need

In order of how much difference each makes:

1. **One existing controller and its service** — the CaseView or Case Assignment pair. Settles
   package layout, DTO style, error handling and transaction conventions in one look. Worth more
   than the rest combined.
2. **`case_inventory` / `case_selection` DDL.** Every column name in `EligibilitySql` and the
   migration is invented. Section 0 of the migration tells you what to run.
3. **How "Viewing as: Group NNNNNN" is carried on the request.** See 3 above — this one is a
   security control, not a convenience.
4. **`pom.xml` or `build.gradle`** — confirms Java version, Spring version, Lombok/MapStruct.
5. **The existing case detail endpoint signature** — for FE-C's review-before-assign flow.
6. **A sample API response from any case assignment list screen** — for the model-score diagnostic
   in the README. Ten minutes, and it settles whether display ordering is achievable at all.

Screenshots are fine; pasted text is easier to work from.

---

## 5. Open questions this code still does not settle

Unchanged from the backlog, and several need Sarah — in on 02 September, out until 24 September.

| # | Question | What the code does meanwhile |
|---|---|---|
| 1 | Grade criteria: exact, at-or-below, or lookup? | Band comparison in `EligibilitySql` |
| 2 | Emergency unselect: supported path or data fix? | `EmergencyUnselectService`, disabled by default |
| 3 | Existing selections at cutover | Migration assumes not retrospective, and says so |
| 4 | K-Skipped on the accelerated list | Excluded. `SelectionStatus` carries it so a row maps rather than throwing |
| 5 | Recalculation cadence | Real time, per-request caching only |
| 6 | Reports scope | Aspect handles `REPORT_SELECT`; the surface may not exist |
| 7 | Did legacy logic carry into modern and sit dormant? | Ask Samuel and Diane. If it did, most of this is reference rather than build |

Question 7 is the one worth thirty seconds before anyone starts BE-A.
