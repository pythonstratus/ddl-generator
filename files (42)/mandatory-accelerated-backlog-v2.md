# Mandatory Accelerated Case Assignment — Modern ENTITY
## JIRA Backlog

**Scope:** Mandatory Accelerated only. Priority alpha sorting, National Queue and other adjacent items are covered in the companion note *Case Assignment — Adjacent Topics*.

**Sources:** Requirements document; 11 screenshots; GM Case Assignment User Guide p.10 Rev. 04-2018; **Case Assignment Modernization walkthrough with Sarah, 01 Sep 2026**.

**Precedence:** the 01 September walkthrough is authoritative. It was a live side-by-side of the reactivated function in development against current production. Where it differs from the requirements document or the 2018 user guide, it wins. Rules it established are marked **[S]** below.

**Sarah's availability:** in on 02 Sep, out until 24 Sep. Fallback contacts Eric, Nicole, Steve.

---

## 1. Confirmed Business Rules

The reference set. Everything in the stories below implements these.

| # | Rule | Source |
|---|---|---|
| 1 | Applies to **General Program** case assignment only. International is excluded entirely and its current programming is unchanged. | Req doc |
| 2 | The restriction triggers when a Revenue Officer has one or more eligible Priority 99 cases in queue inventory. | Req doc |
| 3 | While active, everything below Priority 99 is **hidden and unselectable** for that RO until every eligible P99 case is selected. | **[S]** |
| 4 | Order of selection within the accelerated set is **the manager's choice**. There is no 99a-before-99b gate. | **[S]** |
| 5 | Display order is strictly priority alpha, 99a first, on every screen. Non-negotiable. | **[S]** |
| 6 | ZIP alignment determines **whose Priority 99 count a case belongs to**. It does **not** constrain who it may be assigned to — a manager may assign any case to any RO in the group. Alignment is a suggestion, not a rule. | **[S]** |
| 7 | Once the last eligible P99 case is selected, normal assignment methods become available immediately, with no reload or re-login. | **[S]** |
| 8 | A Mandatory Accelerated selection **cannot be unpicked**. Ordinary S-Selected cases remain unpickable before P-Pending. | **[S]** |
| 9 | Hold/Skip and other Manager Queue Control functions are **not permitted** while the restriction is active. | **[S]** |
| 10 | Two sanctioned workarounds only: **Query** and **Assign by TIN**. Both permit any priority level. Neither clears the restriction for other inventory. | **[S]** |
| 11 | Under Query, all sub-tabs are greyed out except **Priority 99** and **Create Query**, and the view defaults to Priority 99 results. | **[S]** |
| 12 | Reports carry the same restriction — only Priority 99 cases may be selected from report results while eligible inventory exists. | **[S]** |
| 13 | Pending is a list view only and carries no restriction. | **[S]** |
| 14 | The Group Summary employee table itself does not change. It lists all employees at all times. | **[S]** |
| 15 | An RO with zero eligible P99 cases behaves entirely normally — no restriction, all methods available. | **[S]** |

### The four counts

The most likely source of defects in this epic. Two fall, two hold.

| Number | Where | On selection |
|---|---|---|
| **Queued** | Mandatory Accelerated screen header | **Decrements** |
| **Listed** | Mandatory Accelerated screen header | Unchanged |
| **Priority 99** | Group Summary employee table | **Unchanged** |
| **Pending** | Group Summary employee table | **Increments** |

Queue membership derives from the assignment number — the last four digits `7000` denote the queue, so a case leaves the queued count when its assignment number changes to the RO.

The Group Summary Priority 99 column holds until cases are actually **delivered**, not when they are selected. Sarah's reason is operational: an emergency back-door exists to unselect a case on a manager's behalf before it reaches Pending, and the count reflects what is still recoverable. Brian confirmed this explicitly — 46 stays 46 with Pending rising to 2, it does not become 44.

---

## 2. Assumptions and Open Architecture Items

Inferred from screenshots; confirm before sprint planning.

- Modern ENTITY is a browser-based web app. Case Assignment is a tabbed module: Group Summary, Query, Reports, Pending, Hold/Skip, Assign by TIN, Inventory Adjustment.
- Query has sub-tabs: Million Dollar Cases, Priority 99, HINF, Egregious 941, High Priority Cases, National, Local, My Saved Queries, Create Query, Query Results, National Queue.
- A Priority 99 query already exists returning the column set to reuse.
- Services assumed REST/JSON. Paths below are illustrative.

**Still needed:** backend language/framework, database, existing Case Assignment service boundaries, frontend framework and component library, RBAC model.

**Structural finding [S].** Islam noted on the call that modern has no gateway layer between Group Summary, Query and Reports — each surface fetches and renders independently. Mandatory Accelerated is the first requirement needing cross-screen enforcement, which is why it is larger than it looks functionally. That layer is reusable once built, which argues for building it properly rather than as per-screen conditionals.

**Investigate before starting BE-A.** Sarah raised that Mandatory Accelerated logic was requested for use during modern development precisely because reactivation was anticipated. If it was carried across and left dormant, BE-A is a reactivation rather than a build. Ask Samuel and Diane — they have been on the legacy side of this for two months.

---

## 3. Epic

**EPIC — Mandatory Accelerated Case Assignment (Modern ENTITY)**

> Prevent a Group Manager from seeing or selecting any case below Priority 99 for a Revenue Officer who has eligible Priority 99 inventory, until all of that inventory has been selected. Provide RO-level and group-level screens for making those selections, and preserve the two sanctioned workarounds.

**Acceptance:** all 15 business rules above hold, all 10 use scenarios from the requirements document pass in MTEST against production-equivalent data, and counts reconcile to source.

---

## 4. Backend Stories

### BE-A — Mandatory Accelerated eligibility service
**32–42h**

One authoritative service determining which Priority 99 cases are subject to Mandatory Accelerated for a given Revenue Officer, and exposing current restriction state.

**Subtasks**
1. Eligibility rules engine
2. Status endpoint
3. International program exclusion
4. Shared test fixture and rule regression suite

**Rules:** Priority Alpha 99a–99e · case in available queue inventory · RO ZIP alignment · case grade against RO grade criteria · General Program only · all existing Case Assignment eligibility rules continue to apply.

```
GET /api/case-assignment/mandatory-accelerated/status?roAssignmentNumber={n}

200 {
  "roAssignmentNumber": "2710-3910",
  "programType": "GENERAL",
  "restrictionActive": true,
  "queuedCount": 45,
  "listedCount": 45,
  "permittedExceptions": ["QUERY", "ASSIGN_BY_TIN"]
}
```

**Implementation notes**

*Build two access patterns.* Set-based ("all eligible cases for RO X", a query) and predicate-based ("is this one case eligible", called on every assignment attempt and must be cheap). If only the set-based form exists, the interceptor fetches the whole set to validate one case. Back both with the same rule definitions.

*Do not string-compare Priority Alpha.* Parse into numeric priority plus alpha rank and order on the tuple. Lexical comparison puts `101b` and `103b` above `99a` because `1` sorts before `9`.

*No sequencing gate.* Rule 4 — order within the set is free. Do not return a "next required" field; it would invite the UI to build a gate that must not exist.

*ZIP alignment is for attribution, not restriction.* Rule 6. It determines whose count a case falls into, and therefore whose restriction it drives. It does not limit the assignment target. Keep these two concerns separate in the code or BE-D will inherit the wrong constraint.

*Cache with explicit invalidation, never TTL.* Status is read on nearly every page load. Invalidate on assignment write. A stale "restriction cleared" is a control bypass.

*Confirm how program type resolves.* Case field, RO field, or assignment number range — observed patterns are 2710-xxxx and 2501-1405 / 2500-7000.

**Acceptance criteria**
- All 99a–99e cases that are queue-available and aligned to the RO are returned, ordered 99a first.
- A P99 case outside the RO's ZIP alignment is excluded from that RO's set.
- International cases are excluded regardless of priority; `restrictionActive` is false for International ROs.
- `restrictionActive` is false when the RO has zero eligible cases.
- `queuedCount` counts unassigned inventory only; `listedCount` counts everything shown.
- The rule set exists once and is consumed by every other story.

**Critical path.** Does not parallelize. Build and test first.

---

### BE-B — Mandatory Accelerated read endpoints
**28–36h**

Priority 99 inventory scoped to a single RO, scoped to the group, summarised as counts, and drillable to case detail.

**Subtasks**
1. RO-scoped case list
2. Group-scoped case list
3. Per-RO Priority 99 counts for Group Summary
4. Case detail passthrough

```
GET /api/case-assignment/mandatory-accelerated/cases?roAssignmentNumber={n}&page=&size=&sort=
GET /api/case-assignment/mandatory-accelerated/group/{groupId}/cases
GET /api/case-assignment/mandatory-accelerated/group/{groupId}/summary
```

**Columns:** the MTEST Auto Selection Priority 99 layout — Priority Alpha, Case Type, Grade, HINF/941, Balance (Case), Taxpayer Name, City, State, Zip, TIN, TIN File Source, Potential Assignment Number, Current Assignment Number (Queue/Hold File), Flag–Queue Pick, Date Assigned Queue File — plus **QIND** (S-Selected, P-Pending, H-Hold, K-Skipped) and **ZIP ASSN TO RO**.

**Implementation notes**

*Sequence in the database, not after fetch.* Ordering applied post-fetch sorts each page independently, so page two restarts its own sequence. This is the defect reported on 08/26/2026 — do not reintroduce it here.

*Selected and Pending rows pin to the top,* above priority sequence. Two-key sort: QIND status first, then priority alpha within each group.

*The Group Summary Priority 99 count does not decrement on selection.* See the four-counts table. Deriving it from "queue-available only" produces the wrong number — it must include selected-but-undelivered cases.

*Derive counts from the same query as the list* using a count aggregate, not a separate hand-maintained count query, or they drift.

*Use decimal for balances.* Values run past $55,000,000. Floating point breaks BE-E reconciliation.

*A case can align to two ROs* through overlapping ZIP sets. Deduplicate by case in the group list and carry the aligned ROs on the row.

*Avoid N+1 on Group Summary.* Roughly eight employees per group — one grouped query, not a loop.

*Check case detail early.* If the existing case-detail endpoint needs an assignment context a queued case does not yet have, that gap is better found in week one.

**Acceptance criteria**
- RO list filtered by PROID; group list is the deduplicated union across the group, each row carrying its aligned RO.
- Ordering is QIND status first, then priority alpha 99a-first. Pagination does not alter sequence.
- Group list header shows queued and listed as separate values.
- Group Summary Priority 99 counts hold on selection and reflect Queue, GM Hold File and selected-undelivered inventory.
- Case summary, modules, activity, time and name/address are reachable for any listed case.

---

### BE-C — Restriction enforcement interceptor
**32–42h**

One interception layer deciding what a manager may do while the restriction is active, so it cannot be bypassed by calling the API directly or navigating differently.

**Subtasks**
1. Service-layer interceptor and structured rejection
2. Apply to Auto Select, ZIP Code Select and all other selection paths
3. Apply to Manager Queue Control (Hold/Skip Date)
4. Exception allowlist for Query and Assign by TIN
5. **Restrict Query sub-tabs to Priority 99 and Create Query [S]**
6. **Restrict Reports case selection to Priority 99 [S]**

```
409 {
  "errorCode": "MANDATORY_ACCELERATED_ACTIVE",
  "message": "Priority 99 inventory must be assigned first.",
  "roAssignmentNumber": "2710-3910",
  "queuedCount": 45,
  "redirect": "/case-assignment/mandatory-accelerated?ro=2710-3910"
}
```

**Implementation notes**

*Intercept at the service layer, not the controller, and not on URL patterns.* A URL-pattern filter is bypassed the moment anyone adds a route.

*Make selection method an explicit request parameter* (AUTO_SELECT, ZIP_CODE_SELECT, QUERY, ASSIGN_BY_TIN), not inferred from the endpoint. Inference means a new endpoint silently defaults to permitted — the wrong failure direction for a control.

*Resolve the effective group from the impersonation context.* Screenshots show "Viewing as: Group 271039". Reading the actor's own group instead lets a National Analyst bypass enforcement entirely. Most likely security gap in the epic.

*Query sub-tab restriction is state, not routing.* Rule 11 — Million Dollar Cases, HINF, Egregious 941, High Priority Cases, National, Local, My Saved Queries, Query Results and National Queue all disabled; Priority 99 and Create Query stay live. Create Query must remain enabled because it is one of the two sanctioned workarounds. Sarah's framing: it should not be advertised, but it must exist.

*Reports scope is uncertain.* Sarah has not reviewed the modern Reports implementation. Confirm what report-based case selection currently supports before committing subtask 6 — the estimate range above assumes it exists in some form.

*Block the write, not the read, on Hold/Skip.* The tab stays viewable.

*Return 409, not 403.* A 403 reads as an authorization failure and gets triaged to the wrong team.

*Re-check inside the write transaction.* The pre-check is advisory; two managers can both pass it.

*Every rejection is an audit event.* Wire into BE-E from the start.

**Acceptance criteria**
- Selecting a lower-priority case through any method besides Query or Assign by TIN is refused with the structured error.
- Changing screen, search, sort or navigation path does not bypass the gate.
- Hold/Skip Date is refused while active and re-enabled automatically when satisfied; the tab remains readable.
- Query and Assign by TIN permit any priority level and do not force P99 selection.
- Under Query, only Priority 99 and Create Query are enabled; the view defaults to Priority 99 results.
- Report results permit selection of Priority 99 cases only while eligible inventory exists.
- Pending is unrestricted.
- Using an exception does not clear the restriction or decrement the queued count.
- Enforcement applies to a National Analyst operating under "Viewing as: Group NNNNNN".

---

### BE-D — Mandatory Accelerated assignment write path
**20–26h**

Assign a Priority 99 case, lock it against unpick, update or lift the restriction.

**Subtasks**
1. Assignment endpoint with reason-code stamping
2. Schema migration for selection method on the selection record
3. Unpick rejection for Mandatory Accelerated selections
4. Queued-count recalculation and restriction release

```
POST /api/case-assignment/mandatory-accelerated/assign
{
  "tin": "46-3307286",
  "targetRoAssignmentNumber": "2710-3937",
  "reasonCode": "MANDATORY_ACCELERATED_CASE"
}
```

**Implementation notes**

*Any RO in the group is a valid target.* Rule 6. The case in the example above may be ZIP-aligned to 3921 and assigned to 3937 — that is the intended use, not an override. Sarah's stated purpose is rebalancing where one RO holds 55 accelerated cases and another holds 5. Do not validate the target against ZIP or grade alignment.

*Store a selection-method enum, not a boolean.* An enum also gives BE-E the exception-usage audit and gives rule 10 something concrete to assert against.

*One transaction:* verify eligibility, verify still queue-available, create selection, stamp method and reason, recalculate queued count, invalidate status cache. Recalculation outside the transaction shows stale numbers immediately after the action that changed them.

*Put the unpick block on the selection state machine,* not in the unpick controller. Any second unpick path — bulk, admin, future screen — otherwise misses it.

*An emergency unselect capability exists and is not a user function.* Sarah described past incidents where a selected-but-not-Pending case had to be unselected on a manager's behalf, done manually by developers in legacy. Managers must never have it. Confirm whether modern needs a supported administrative path or whether a controlled data fix remains acceptable. This is linked to why the Group Summary count holds until delivery.

*Optimistic lock on the case row.* Two managers, same case — one wins, the other gets a specific "no longer available" error.

*Confirm migration behaviour for in-flight selections* already Selected or Pending at cutover.

**Acceptance criteria**
- Reason stamped as "Mandatory Accelerated Case".
- A case may be assigned to any RO in the manager's group regardless of ZIP alignment, including an RO with zero accelerated inventory of its own.
- Selection follows the established GM Case Assignment lifecycle to Selected then Pending.
- Mandatory Accelerated selections are refused for unpick; ordinary S-Selected cases remain unpickable.
- The restriction persists until all applicable cases for that RO are selected, then lifts immediately without reload.
- Queued count decrements; Group Summary Priority 99 count does not.
- Assignment is refused if the case has left available queue inventory.

---

### BE-E — Audit trail and counts reconciliation
**16–20h**

**Subtasks**
1. Audit logging for assignments, blocked attempts, exception usage and unpick attempts
2. Reconciliation harness comparing counts and case sets against source data

**Implementation notes**

*Append-only.* No updates, no deletes, no admin-toggleable soft delete. A mutable table is not evidence.

*Record the impersonation chain,* not just the actor. "S. Vainer, National Analyst, acting as Group 271039" is the meaningful record.

*Log rejections at the same fidelity as successes.* For a control function, attempted bypasses are the interesting audit question.

*Reconcile against production-equivalent data,* not a hand-built fixture. A fixture only proves the code agrees with itself.

*Reconcile three relationships:* Group Summary counts against each RO list, the union of RO lists against the group list, and the group list against source prioritization data.

*Make it runnable on demand in MTEST.* This is the artifact shown at review.

**Acceptance criteria**
- Each record captures actor, acting-as group, RO, case identifier, selection method, timestamp and outcome.
- Counts and presented cases reconcile to the underlying Priority 99 business rules and source data.
- Discrepancies are reported, not silently absorbed.

**Do not cut this.** Federal tax system; the control function is what gets examined.

**Backend subtotal: 128–166 hours**

---

## 5. Frontend Stories

### FE-A — Mandatory Accelerated case list
**16–28h — spike the lighter option first**

**Subtasks**
1. Shared list component using the MTEST Priority 99 column layout
2. Group-scoped view (the F2 equivalent)
3. RO-scoped view
4. Sequencing and sort guardrails

**Implementation notes**

*Spike the dropdown approach before building a separate RO screen.* Sarah proposed, and Islam refined: the RO case selection screen already has a dropdown defaulting to "Top 200". On page load, if the RO's Priority 99 count is greater than zero, default that dropdown to Priority 99 and **disable it** until every accelerated case is selected; if zero, default to Top 200 and behave normally. Sarah framed this as considerably less work than a full rebuild. If it holds, the RO half of this story largely disappears and the estimate lands at the bottom of the range.

The group-level screen is still needed as a distinct surface — it has no existing analogue.

*One component, parameterized.* Group and RO views differ by data source and by the aligned-RO column.

*Fifteen-plus columns need horizontal scroll with a frozen first column.* Both legacy and MTEST scroll horizontally.

*Sort server-side, always.* Client-side sorting reorders the current page only and reproduces the defect this work must not repeat. See the companion sorting guide.

*Offer the legacy sort set, not arbitrary column sorting* — case grade, case type, priority level code, zipcode on the accelerated screen.

*Selected and Pending rows pin to the top* regardless of chosen sort. QIND should be visually distinct enough to scan.

*Show both counts in the header.* Queued and listed are different numbers and managers read them together.

*Empty state carries meaning.* Zero accelerated cases is the signal that normal methods are available — say so rather than rendering a blank table.

*No modern equivalent of F4-VIEW BAL.* Legacy toggles TIN against balance because the terminal is too narrow for both. The modern layout carries both.

**Acceptance criteria**
- Columns match the MTEST Priority 99 layout plus QIND and ZIP ASSN TO RO.
- Selected and Pending rows at the top; remaining rows in priority alpha sequence, 99a first. Pagination preserves sequence.
- Sort options limited to the permitted legacy set; sorting does not permit bypass.
- Group view shows queued and listed separately, and each row's aligned RO.
- RO view shows only cases aligned to that RO.
- An RO with zero accelerated cases shows a clear empty state.

---

### FE-B — Restriction gating and routing
**28–36h**

**Subtasks**
1. "Group Mandatory Accelerated" button on Group Summary
2. Gate on RO case selection, blocking Auto Select and ZIP Code Select
3. Queued-count indicator with live decrement and automatic unlock
4. Consistent handling of `MANDATORY_ACCELERATED_ACTIVE` across every assignment path
5. **Grey out Query sub-tabs except Priority 99 and Create Query [S]**
6. **Default Query to Priority 99 results while active [S]**

**Implementation notes**

*Make the Group Mandatory Accelerated button prominent.* Sarah was direct: accelerated work is the first thing a manager deals with every time and should not require hunting. Eric's mock places it under Group Summary. Adding it only to the tab strip was floated and accepted as workable, but the explicit button is preferred.

*Group Summary itself does not change.* Rule 14 — it lists all employees at all times. Do not filter or reorder it.

*One status hook, consumed everywhere.* Scattered `restrictionActive` checks drift.

*Hiding controls is not enforcement.* Handle the server 409 too — the count can change between page load and click.

*The counter tracks queued, not listed.* Only queued decrements and only queued reaching zero lifts the restriction. Binding to listed produces a number that never moves and an unlock that never fires.

*The message needs the count and a working route.* "Blocked" without a next step is a support ticket.

*Refetch status on assignment success rather than polling.*

*Keep Query and Assign by TIN visibly available.* If a manager cannot see which routes remain open, the gate reads as a system fault.

**Acceptance criteria**
- Any blocked attempt shows a clear message with the queued count and a link to the applicable screen.
- Auto Select and ZIP Code Select are blocked; Query and Assign by TIN remain accessible.
- Under Query, only Priority 99 and Create Query are enabled; the view defaults to Priority 99 results; all restore when the restriction lifts.
- The counter decrements after each selection without reload; at zero, normal methods become available immediately.
- Group Summary employee table is unchanged in content and ordering.
- No raw error codes reach the user.

---

### FE-C — Case review and assignment flow
**18–22h**

**Subtasks**
1. Case review panel (summary, modules, activity, time, name/address)
2. Assign action with RO picker

**Implementation notes**

*Keep case-to-case navigation without returning to the list.* Legacy binds First, Prev, Next and Last to function keys on the assignment screen, and a group can hold 45+ accelerated cases. Forcing a return to the list between each assignment makes routine work tedious.

*The RO picker offers every RO in the group,* including those with zero accelerated inventory. Rule 6 — this is the redistribution path, not an exception. Display as assignment number and name, matching the legacy "1405-B. O'BRIEN" format.

*Everything except the RO is pre-populated.* Sarah's demonstration: the only field she changed on the assign screen was the target RO. Reason shows as "Mandatory Accelerated Case" and is not editable — unlike Assign by TIN, which offers reason options.

*Confirm before commit, and say why.* Rule 8 — these selections cannot be undone. That must appear in the confirmation, not be discovered when a later unpick is refused.

*Preserve list position on return.* Scroll position and page number.

**Acceptance criteria**
- RO picker limited to the manager's group; any RO selectable regardless of alignment.
- Case information shows TIN, name control, taxpayer name, assignment queue date, balance due and taxpayer grade.
- Reason displays as "Mandatory Accelerated Case" and is not editable.
- The confirmation states the selection cannot be unpicked.
- On success the list refreshes and the queued count decrements.
- Returning to the list preserves scroll position and pagination state.

---

### FE-D — Disabled states and Group Summary counts
**10–14h**

**Subtasks**
1. Unpick disabled for Mandatory Accelerated selections
2. Hold/Skip disabled while restriction active
3. Group Summary Priority 99 and Pending columns wired to correct counts

**Implementation notes**

*Disable with an explanation rather than hiding.* A control that vanishes reads as a bug; a disabled control with a reason teaches the rule. Sarah's phrasing on unpick was that it needs to be greyed out.

*The explanation must be accessible,* not tooltip-only.

*Priority 99 and Pending are existing columns.* This is data wiring, not new columns — and the Priority 99 value must **hold** on selection while Pending rises. Verify against the four-counts table before calling it done.

*Hold/Skip re-enables automatically* when the restriction lifts, without reload.

**Acceptance criteria**
- Disabled controls carry a reason on hover, on focus, and to assistive technology.
- Ordinary S-Selected cases retain normal unpick behaviour.
- Hold/Skip re-enables automatically once satisfied.
- After two selections for an RO showing 46, Group Summary reads Priority 99 = 46 and Pending = 2.

**Frontend subtotal: 72–100 hours**

---

### Accessibility

In the definition of done for FE-A through FE-D: blocked-state messaging available to assistive technology and not conveyed by colour or placement alone, new tables keyboard navigable. Cheaper than retrofitting. Formal 508 validation is a separate QA booking, not in this estimate.

---

## 6. Timeline

Development hours at 8h/day, QA running alongside rather than after.

| Scenario | Dev | Production-ready |
|---|---|---|
| **Full scope**, 2 BE + 2 FE | 12–16 days | **~3–4 weeks** |
| **Full scope**, 1 BE + 1 FE | 25–33 days | **~7–8 weeks** |
| **Reduced first release**, 2 BE + 2 FE | 10–12 days | **~3 weeks** |

### Sequencing, full scope with 2 BE + 2 FE

| Phase | Days | Backend | Frontend |
|---|---|---|---|
| 1 | 1–5 | BE-A (serial, one dev); second dev on migration, fixtures, BE-E scaffolding | Contract agreed; FE-A spike on the dropdown approach; scaffolding on mocks |
| 2 | 6–12 | BE-B, BE-C, BE-D across both devs | FE-A, FE-B in parallel |
| 3 | 13–16 | BE-E, integration | FE-C, FE-D, integration |

### Reduced first release

BE-A, BE-C, BE-D plus FE-B and the RO half of FE-A. Roughly 128–168 hours. Delivers detection, enforcement across all selection paths and Query, assignment, unpick prevention and dynamic release.

**What it defers:** the group-level Mandatory Accelerated screen. Managers use F2 today to see every accelerated case across the group and redistribute across ROs — Sarah demonstrated this as a core workflow for balancing an RO holding 55 cases against one holding 5. Dropping it is a real parity regression, not a cosmetic one.

### Dates and go-live gating

| Date | Event |
|---|---|
| 08 Sep 2026 | Director of Collection issues the reactivation memo to all of Collection |
| 12 Sep 2026 | Mandatory Accelerated activates in **legacy** production |

Modern does not have to ship by 12 September. What was established on the call is different and stronger: **modern case assignment cannot go to production at all until this is built.** So this is a launch blocker, and the estimate above is a statement about when modern can launch.

**The practical constraint is Sarah's availability.** In on 02 September, out until 24 September. Everything in Section 7 needs an answer on 02 September or waits three weeks — and three weeks is most of the build.

### Scope movement since the previous version

Added by the 01 September walkthrough: Query sub-tab restriction and Reports restriction, neither in the original requirements document, both landing in BE-C and FE-B. Reports is loosely specified and its estimate should be firmed up.

Potentially removed: the dropdown-default approach may cut the RO half of FE-A substantially. Spike it in phase 1.

---

## 7. Question Status

### Resolved on 01 September

| Question | Answer |
|---|---|
| Strict alpha sequencing, or all P99 first? | All P99 before lower priority; **any order within the set** |
| Can a manager assign to a non-aligned RO? | **Yes** — alignment is a suggestion, not a rule |
| Cross-RO satisfaction | Follows from the above; an assigned case leaves the queue and its aligned RO's queued count |
| Enforcement vs inventory targets | Not a conflict — the group screen exists to redistribute |
| Priority 99 count on selection | **Does not decrement**; holds until delivery, Pending rises |
| Queued vs Listed | **Both required**, confirmed as a requirement to adhere to |
| Role scope | Managers and acting managers; secretaries occasionally supporting. Treat as a management control |
| Legacy FIX RO checkbox | Indicated as not needed in modern — low transcription confidence, worth ten seconds to confirm |

### Open — needs Sarah on 02 September

1. **Reports scope.** What does modern report-based case selection currently support? Sizes BE-C subtask 6.
2. **Emergency unselect.** Supported administrative path, or controlled data fix as in legacy? Managers never get it either way.
3. **K-Skipped.** Can it appear on the accelerated list, given accelerated cases cannot be skipped?
4. **Grade criteria.** Exact match, at-or-below, or lookup table? Not stated in any source.
5. **Existing selections at cutover.** Do cases already Selected or Pending fall under these rules?
6. **Recalculation cadence.** Real time, on prioritization refresh, or nightly?

### Open — for the development team

7. **Legacy code reuse.** Did Mandatory Accelerated logic carry into modern and sit dormant? Ask Samuel and Diane. Materially affects BE-A.
8. **Component reuse.** Are the Priority 99 query table and Assign by TIN screen reusable components or one-off pages? Affects FE-A and FE-C.
9. **Dropdown approach viability.** Spike FE-A phase 1.
