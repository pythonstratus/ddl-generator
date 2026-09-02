# Mandatory Accelerated Case Assignment — Backend

Backend implementation of the epic, covering stories BE-A through BE-E from
`mandatory-accelerated-backlog-v2.md`.

**Read `CHANGES.md` first if you have seen the previous revision.** It lists seven defects, what
each would have cost, and the one decision you need to make before the rest is useful.

---

## 1. Assumptions, and what each costs if wrong

The **business rules** come from the confirmed backlog and are solid. The **shape of the code** is
read from ENTITY's established conventions.

| # | Assumption | Basis | Cost if wrong |
|---|---|---|---|
| 1 | Java 17+, Spring Boot 3, Spring JDBC, Spring AOP. **No JPA in this module** | Matches CaseView / TimeView / ActivityView | Low. Two classes go back to `@Entity`, two `Jdbc*Repository` classes get deleted |
| 2 | `NamedParameterJdbcTemplate` under `@Qualifier("secondaryNamedJdbcTemplate")` | The qualifier used across the view services | Change the qualifier in six places, all listed below |
| 3 | Package root `gov.irs.sbse.os.ts.csp.alsentity.ale`, feature subtree `.accelerated` | Confirmed from `entity-service` — see section 8 | Subtree layout is the open part, not the root |
| 4 | Oracle, 12c+ syntax | `SUBSTR` negative offset, `OFFSET .. FETCH NEXT`, `NULLS LAST` | Dialect notes are in `EligibilitySql` and the migration |
| 5 | Table and column names in the mapping table in section 5 | Invented | Rename in `EligibilitySql`, the two JDBC repositories, and the migration |
| 6 | `AuthenticationFilter` sets a `seid` request attribute, uppercased at the boundary | Established pattern | `RequestAttributeAssignmentContext` needs one edit |

**To flatten into the existing `controller` / `service` packages instead of the subtree**, see
section 8. It is a scripted rewrite, not a hand edit, but it is not reversible once merged — decide
before the first commit.

**To change the datasource qualifier**, these are the only six places a template is injected:

```
eligibility/DefaultMandatoryAcceleratedEligibilityService
read/AcceleratedCaseRepository
assign/JdbcCaseSelectionRepository
assign/JdbcCaseInventoryWriteRepository
audit/JdbcAuditEventRepository
config/AcceleratedInfrastructureConfig   (two @Bean methods)
```

---

## 2. Layout

```
accelerated/
├── domain/          value objects and enums — the rules that need no database
├── eligibility/     BE-A. The single authoritative rule set, plus the request-scoped cache
├── read/            BE-B. Lists, pagination, counts, case detail port
├── enforcement/     BE-C. The interception layer, commands, exceptions
├── assign/          BE-D. Write path, selection state machine, emergency unselect
├── audit/           BE-E. Append-only audit trail
├── reconcile/       BE-E. Counts reconciliation harness
├── status/          The one status payload every screen gates on
├── api/             Controllers, DTOs, global error mapping
└── config/          Conditional wiring for the ports ENTITY probably already satisfies
```

### Story to file

| Story | Files |
|---|---|
| BE-A eligibility | `eligibility/*` |
| BE-B lists and counts | `read/*`, `status/*` |
| BE-C enforcement | `enforcement/*`, `api/MandatoryAcceleratedExceptionHandler` |
| BE-D write path | `assign/*`, `db/migration/V2026_09_01_001__*.sql` |
| BE-E audit and reconciliation | `audit/*`, `reconcile/*` |

### Three files carry most of the design

**`eligibility/EligibilitySql.java`** — the predicate fragments, the ordering clause and the column
projection, each written once. Every list, count, status check and in-transaction re-check composes
from these constants. The previous revision proved why this matters: the group query restated the
predicate by hand and silently dropped a rule. Two tests now fail if the compositions come apart.

**`enforcement/MandatoryAcceleratedEnforcementAspect.java`** — one interception point, on service
methods, annotation-driven. Not on controllers and not on URL patterns, because a URL filter is
bypassed the moment someone adds a route.

**`assign/CaseSelection.java`** — the unpick refusal lives on the selection's own state machine
rather than in the unpick controller. Any second unpick path — bulk, admin, a screen added next
year — inherits it automatically.

---

## 3. Endpoints

All under `/api/case-assignment/mandatory-accelerated`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/status?roAssignmentNumber=` | Restriction state and counts. The one call every screen gates on |
| GET | `/ui-state?roAssignmentNumber=` | Which Query sub-tabs to grey, Hold/Skip writability, route. Advisory only |
| GET | `/cases?roAssignmentNumber=&page=&size=` | RO-scoped list |
| GET | `/group/{groupId}/cases?page=&size=` | Group list. The Legacy F2 equivalent |
| GET | `/group/{groupId}/summary` | Priority 99 counts for the employee table |
| GET | `/cases/{tin}/{tinFileSource}/detail` | Review before assign. Delegates to the existing service |
| POST | `/assign` | Assign one accelerated case. 201 |
| POST | `/unpick` | Always refuses for accelerated selections. Present so the refusal is explicit and auditable |
| GET | `/admin/reconcile/{groupId}` | Reconciliation report. Runnable on demand in MTEST |
| POST | `/admin/emergency-unselect` | Disabled by default. See open question 2 |

There is deliberately **no sort parameter** on the list endpoints. Rule 5 makes display order
non-negotiable, so exposing one would let a client request an order the business has ruled out.

### Status

```
GET /api/case-assignment/mandatory-accelerated/status?roAssignmentNumber=2710-3910

200 {
  "roAssignmentNumber": "2710-3910",
  "programType": "GENERAL",
  "restrictionActive": true,
  "queuedCount": 45,
  "listedCount": 45,
  "pendingCount": 0,
  "permittedExceptions": ["QUERY", "ASSIGN_BY_TIN"]
}
```

### Assign

```
POST /api/case-assignment/mandatory-accelerated/assign
{
  "tin": "46-3307286",
  "tinFileSource": "IMF",
  "targetRoAssignmentNumber": "2710-3937",
  "expectedRowVersion": 7
}

201 {
  "selectionId": "…",
  "tin": "463307286",
  "assignedTo": "2710-3937",
  "alignmentRoAssignmentNumber": "2710-3921",
  "reasonDisplayText": "Mandatory Accelerated Case",
  "counts": { "queued": 44, "listed": 46, "groupSummaryPriority99": 46, "pending": 2 },
  "restrictionLifted": false
}
```

The target may be **any** RO in the group. The case here is ZIP-aligned to 3921 and assigned to
3937 — that is the intended use, not an override.

`alignmentRoAssignmentNumber` and `counts` describe **3921**, not 3937. On a rebalancing
assignment the numbers that move belong to the RO the case left. The client updates that header.

### Refusal

```
409 {
  "errorCode": "MANDATORY_ACCELERATED_ACTIVE",
  "message": "Priority 99 inventory must be assigned first.",
  "roAssignmentNumber": "2710-3910",
  "queuedCount": 45,
  "redirect": "/case-assignment/mandatory-accelerated?ro=2710-3910"
}
```

409 rather than 403. A 403 reads as an authorization failure and gets triaged to the security team,
who will spend a day establishing it is not theirs.

Other error codes: `CASE_NO_LONGER_AVAILABLE` (409), `CASE_OUTSIDE_EFFECTIVE_GROUP` (409),
`UNPICK_NOT_PERMITTED_MANDATORY_ACCELERATED` (422 — permanent, so a message suggesting a retry
would be actively misleading).

---

## 4. Wiring it into the existing selection paths

Two steps per path. Take the command as a parameter, add the annotation:

```java
@MandatoryAcceleratedGuarded
@Transactional
public SelectionResult autoSelect(StandardSelectionCommand command) { ... }

@MandatoryAcceleratedGuarded(queueControl = true)
@Transactional
public void setHoldSkipDate(QueueControlCommand command) { ... }
```

The selection method is **declared by the caller, never inferred from the endpoint**. Inference
means a newly added endpoint silently defaults to permitted, and for a control function the safe
default has to be blocked.

Paths to wire: Auto Select, ZIP Code Select, report-driven selection, Hold/Skip write. Query and
Assign by TIN can be annotated too — they will be permitted, and annotating them is what produces
the exception-usage audit record rule 10 depends on.

### Two ports you must implement

Left unimplemented on purpose, so a missing binding fails at startup rather than at runtime:

- **`RevenueOfficerLookup`** — RO program type and group membership. Adapt to whatever already owns
  RO data (`EntEmpService` is the likely candidate). Do not add a second source of truth.
- **`CaseDetailPort`** — case summary, modules, activity, time, name and address. See the risk note
  on the interface.

### Three that are defaulted, and yours wins

`AssignmentContext`, `CaseInventoryWriteRepository` and `CaseSelectionRepository` all ship with a
reference implementation registered `@ConditionalOnMissingBean`. Define your own anywhere in the
application and it takes precedence, with nothing here to delete. If ENTITY already has a Case
Assignment service owning the selection lifecycle, implement the two repositories as thin adapters
onto it — do not run two write paths against the same table.

---

## 5. Two things to check before this compiles cleanly

### The model score

Rank within a single alpha value is driven by a model score calculated against balance. Three
consecutive 99b rows are not interchangeable — the top one is considered more productive.

No screenshot shows that column in a modern payload. Before trusting `DISPLAY_ORDER_BY`:

1. Open dev tools, load any case assignment list, inspect the raw API response.
2. Is there a model score, rank or sequence ordinal field?
3. Is the array already in the right order as received, or is the grid sorting it client-side?

If the field is absent, this is a missing-data defect and no amount of `ORDER BY` fixes it. Adding
the column in the migration is necessary but not sufficient — something has to populate it. Ten
minutes, and it settles the question.

### Table and column mapping

Every name below is invented. Rename in `EligibilitySql`, the two JDBC repositories and the
migration. Section 0 of the migration has a `user_tab_columns` query to run first.

| Used here | Holds |
|---|---|
| `case_inventory` | Queue inventory: priority alpha, grade, ZIP, balance, selection status, row version |
| `case_selection` | Selection records and their lifecycle status |
| `ro_zip_alignment` | RO to ZIP mapping, with an active flag |
| `ro_grade_criteria` | Min and max case grade per RO |
| `revenue_officer` | RO roster with group and program type |
| `v_case_assignment_eligible` | Your existing eligibility view. Deferred to rather than duplicated |
| `ma_audit_event` | New. Append-only audit trail |

---

## 6. Rules encoded here, with the ones easy to build backwards flagged

| # | Rule | Where |
|---|---|---|
| 1 | General Program only. International excluded entirely | `ProgramType`, short-circuited before the DB |
| 2 | Restriction triggers on one or more eligible P99 cases | `AcceleratedCounts.restrictionActive()` |
| 3 | Everything below P99 hidden and unselectable while active | `MandatoryAcceleratedEnforcementAspect` |
| 4 | **Selection order within the set is the manager's choice** | No gate exists. Guarded by a test |
| 5 | **Display order is strictly alpha, 99a first, everywhere** | `EligibilitySql.DISPLAY_ORDER_BY` |
| 6 | **ZIP alignment decides whose count, not who it goes to** | `AcceleratedAssignmentService.assign` |
| 7 | Restriction lifts immediately, no reload | `AssignmentResult.restrictionLifted` |
| 8 | Accelerated selections cannot be unpicked | `CaseSelection.unpick()` |
| 9 | Hold/Skip blocked while active. Write only, not read | `@MandatoryAcceleratedGuarded(queueControl = true)` |
| 10 | Exactly two workarounds: Query, Assign by TIN | `SelectionMethod.isSanctionedWorkaround()` |
| 11 | Query sub-tabs greyed except Priority 99 and Create Query | `QuerySubTab` |
| 12 | Reports restricted to Priority 99 | Aspect, `REPORT_SELECT` branch. Scope still open |
| 13 | Pending unrestricted, list only | `UiRestrictionState.pendingRestricted`, always false |
| 14 | Group Summary employee table unchanged | `LEFT JOIN` in the grouped count query |
| 15 | RO with zero eligible cases behaves normally | `AcceleratedCounts.NONE`, `AcceleratedCasePage.empty()` |

**Rules 4 and 5 look like the same rule and are not.** Display is gated; selection is not. It is
easy to read "priority alpha order starting with 99a" in the requirements document as a selection
gate — it is a display sequence. If it were a selection gate the F6-SORT affordance in the user
guide would be meaningless, since only the top row would ever be selectable. There is a test that
fails if someone adds a next-required-alpha check.

**Rule 6 is counter-intuitive and the group screen depends on it.** A manager can assign any case
to any RO in the group. Validating the target against ZIP alignment would break rebalancing — one
RO holding 55 accelerated cases against another holding 5 — which is the screen's main purpose.

Rule 6 opens the *target*, not the case. A case aligned to another group's RO is out of scope
however permissive the target rule is; `AcceleratedAssignmentService` checks both.

**The Group Summary Priority 99 count does not decrement on selection.** An RO showing 46 still
shows 46 after two assignments, with Pending at 2. It falls only on delivery, because a
selected-but-not-Pending case is still recoverable. Wiring that column to `queued()` instead of
`groupSummaryPriority99()` produces something that looks correct in a demo and is wrong in
production.

---

## 7. Configuration

```properties
# Emergency unselect. Leave false until open question 2 is answered.
entity.case-assignment.accelerated.emergency-unselect.enabled=false
```

No cache configuration. The status cache is request-scoped by design — see
`RestrictionStatusCache`, which explains why a shared cache with explicit invalidation cannot
honour "never TTL" across more than one instance, and what to do instead if the aggregate later
proves too expensive to run once per request.

---

## 8. Compliance with `entity-service`

Checked against the conventions visible in `QueryBuilderController`, `CaseViewService`,
`AbstractViewService`, `ActivityViewCsvFormatter`, `AuthenticationFilter` and the query-builder JPA
entities.

### Matches

| Convention | Where |
|---|---|
| Package root `gov.irs.sbse.os.ts.csp.alsentity.ale` | Every file |
| `NamedParameterJdbcTemplate` + `MapSqlParameterSource`, named binds only | All six repositories and services |
| Secondary datasource via `@Qualifier` | Six injection points, listed in section 1 |
| SEID from the `seid` request attribute, uppercased at the boundary | `RequestAttributeAssignmentContext` |
| springdoc `@Operation` on every endpoint | Both controllers |
| Constructor injection, no field injection | Throughout |
| Complete files with `-- VERIFY` markers on anything transcribed rather than confirmed | Migration, `EligibilitySql` |

### Divergences, each deliberate and each reversible

**1. Feature subtree rather than flat `controller` / `service` packages.** `entity-service` puts
everything in `ale.controller` and `ale.service` — `CaseViewService` and `CsvColumn` sit in the same
directory. Following that literally would drop 40 files into `ale.service`, and the enforcement
aspect would be indistinguishable from a view service by location. So this sits at
`ale.accelerated.*` instead.

That is a judgement call about a 62-file feature, not a rule I found. To flatten:

```bash
# controllers to ale.controller, everything else to ale.service
cd src/main/java/gov/irs/sbse/os/ts/csp/alsentity/ale
grep -rl 'ale\.accelerated' ../../../../../../../.. \
  | xargs sed -i -E 's/ale\.accelerated\.api\.(dto\.)?([A-Za-z]*Controller)/ale.controller.\2/g; s/ale\.accelerated\.[a-z]+\./ale.service./g; s/ale\.accelerated\./ale.service./g'
# then move the files and delete the empty accelerated/ directories
```

Check for name collisions first — `CaseSelection`, `AuditEvent` and `CaseKey` are generic enough to
already exist somewhere in `ale.service`.

**2. SQL as Java constants, not `ResourceUtil.getSql("...")`.** `entity-service` keeps SQL in
`.sql` resource files loaded by name. This module holds it in `EligibilitySql` as composable String
constants instead, because the whole design depends on one predicate being *composed* two ways —
RO-scoped and group-scoped — from shared fragments, and on the compiler failing if a fragment is
dropped. That guarantee is what caught the group-query drift described in `CHANGES.md`. String
concatenation across separate `.sql` files gives back exactly the failure mode the module exists to
prevent. If the house rule is firm, move `CASE_COLUMNS` and `DISPLAY_ORDER_BY` out to `.sql` files
and keep the predicate fragments in Java — that is the split that preserves the property.

**3. Services do not extend `AbstractViewService`.** That base carries `jdbcTemplate`, `streamCsv`,
`escapeCsv` and `exportTemplate` — CSV export machinery this module has no use for, since nothing
here exports. Extending it would inherit a second `JdbcTemplate` alongside the injected one. If it
also carries something this module *should* have — a shared error convention, a logging pattern,
cache access — send it and the services will extend it.

**4. Request-scoped status cache, not the shared Caffeine `entityCache`.** Reasoned through in
`RestrictionStatusCache`: a shared cache with explicit invalidation cannot honour rule 7 across more
than one instance, because a stale `restrictionActive: false` on instance B is a control bypass, not
a stale read. The class documents the conditions under which moving to Caffeine becomes safe.

**5. No JPA.** `entity-service` does use JPA, but only for the query-builder entities (`QueryName`,
`QueryWhereExpression`). Everything touching case data is JDBC, and this module is case data.
