# Mandatory Accelerated Case Assignment — Modern ENTITY
## JIRA Backlog

**Source:** Requirements document, 11 screenshots (Legacy F2 screens, MTEST screens, functional requirements, 10 use scenarios), and GM Case Assignment User Guide page 10, Rev. 04-2018

**Note on the user guide:** it is dated April 2018 and describes legacy behaviour as of that revision. Where it conflicts with the 2026 requirements document, the requirements document is the newer statement of intent but the guide is the better evidence of what legacy actually does. Conflicts are called out below.
**Target:** Modern ENTITY parity with Legacy ENTITY Mandatory Accelerated, reactivated in 2026 Legacy Release 1 (Sept 2026)

Each story carries subtasks. Create them in JIRA so progress stays visible within the larger stories.

---

## 0. Assumptions and Gaps

The earlier DB performance conversations could not be retrieved (chat history search is scoped to the current project, and this project has no prior chats). The architecture below is inferred from the screenshots alone. **Confirm or correct before sprint planning.**

Inferred from the MTEST screenshots:

- Modern ENTITY is a browser-based web application with a persistent top nav (Views, Reports & Queries, Change Access, Case Assignment, End of Month).
- Case Assignment is a tabbed module: Group Summary, Query, Reports, Pending, Hold/Skip, Assign by TIN, Inventory Adjustment.
- Query has its own sub-tab strip including a Priority 99 tab that already returns the column set we need to reuse.
- A prioritization engine already produces Priority Alpha values (99a–99e, 95a–95e, etc.).
- Services are assumed REST/JSON. Paths below are illustrative and should be refitted to your naming convention.

**Still needed:** backend language/framework, database, existing Case Assignment service boundaries, frontend framework and component library, and the RBAC model.

**One open item affects the estimates directly.** Several frontend estimates assume the Priority 99 query table and the Assign by TIN screen are reusable components rather than one-off pages. If they are one-off pages, add roughly 12–16 hours to FE-A and FE-C.

---

## 1. Requirement Summary

Mandatory Accelerated is an **assignment-control function**, not a view. When an RO has eligible Priority 99 inventory, the system must block the manager from assigning lower-priority queue inventory to that RO until the applicable P99 cases have been selected.

| In scope | Out of scope |
|---|---|
| General Program Case Assignment | International Case Assignment |
| Auto Select, ZIP Code Select, all other selection dropdowns | Query (permitted workaround) |
| Group Summary and RO case selection paths | Assign by TIN / Custom Assignment (permitted workaround) |

Two sanctioned exceptions only: **Query** and **Assign by TIN**. Neither forces P99 selection, and neither clears the restriction for other inventory.

---

## 2. Epic

**EPIC — Mandatory Accelerated Case Assignment (Modern ENTITY)**

> Modern ENTITY must replicate Legacy ENTITY Mandatory Accelerated Case Assignment within GM Case Assignment for General Program inventory, enforcing that eligible Priority 99 cases aligned to a Revenue Officer are assigned before any lower-priority queue inventory, and that the restriction cannot be circumvented through alternate screens, searches, sorts, or workflows.

**Acceptance:** all 10 use scenarios pass in MTEST against production-equivalent data, and P99 counts reconcile to source data.

---

## 3. Backend Stories

### BE-A — Mandatory Accelerated eligibility service
**32–42h**

As the ENTITY platform, I need one authoritative service that determines which Priority 99 cases are subject to Mandatory Accelerated for a given Revenue Officer, and exposes the current restriction state, so that every downstream decision uses the same rule set.

**Subtasks**
1. Eligibility rules engine
2. Status endpoint
3. International program exclusion
4. Shared test fixture and rule regression suite

**Rules**
- Priority Alpha in 99a–99e (all five alpha scores)
- Case is in available queue inventory
- RO ZIP-code alignment
- Case grade vs RO grade criteria
- Program type = General Program (International excluded)
- All existing Case Assignment eligibility rules continue to apply

```
GET /api/case-assignment/mandatory-accelerated/status
    ?roAssignmentNumber={n}

200 {
  "roAssignmentNumber": "2710-3910",
  "programType": "GENERAL",
  "restrictionActive": true,
  "queuedCount": 45,
  "listedCount": 45,
  "displaySequenceStart": "99a",
  "permittedExceptions": ["QUERY", "ASSIGN_BY_TIN"]
}
```

**Implementation notes**

*Build two access patterns, not one.* The engine is consumed two ways: set-based ("give me every eligible case for RO X", which needs to be a database query for volume) and predicate-based ("is this one case eligible for RO X", which is called on every assignment attempt and must be cheap). If only the set-based form is built, the enforcement interceptor has to fetch and scan the whole set to validate a single case. Back both forms with the same rule definitions so they cannot drift.

*Do not string-compare Priority Alpha.* Observed values include 99a–99e and lower priorities such as 95a, 95c, 95e. Parse into a tuple of numeric priority and alpha rank, and order on the tuple. String comparison happens to work within a single priority band and breaks across bands.

*ZIP alignment is a large join.* The Group Summary screenshot shows ZIP counts per RO of 114, 504, 59, 27, 40, 253, 20 and 31. An RO maps to many ZIPs, so load the RO's ZIP set once per evaluation and test against it in memory rather than joining per case.

*Confirm the grade rule before coding it.* Cases carry Grade 11, 12 and 13; ROs carry a grade (screenshots show 13, 11 and 9). Whether the rule is exact match, case grade at or below RO grade, or a lookup table is not stated in the requirement document. Guessing here produces wrong enforcement rather than a visible bug.

*Cache with explicit invalidation, never TTL.* Restriction status is read on nearly every Case Assignment page load, so it wants caching. Invalidate on assignment write. A stale "restriction is active" is a minor annoyance; a stale "restriction cleared" lets a manager bypass the control, which is a compliance failure.

*Determine how program type is resolved.* International exclusion needs a reliable General vs International signal. Check whether that lives on the case, the RO, or the assignment number range — observed assignment numbers follow 2710-xxxx and 2501-1405 / 2500-7000 patterns, which may be meaningful.

*Two counts, not one.* The user guide states the box displays "the number of queued (unassigned) accelerated cases and the number of accelerated cases listed" — matching the legacy header `QUEUED: 45  LISTED: 45`. These diverge as soon as assignments begin: queued counts only unassigned inventory and decrements, while listed counts everything shown including Selected and Pending rows and stays flat. The restriction lifts when `queuedCount` reaches zero, not `listedCount`. Carrying a single count will produce a counter that appears stuck.

*Enforcement is set-based, not order-based.* The user guide describes an F6-SORT affordance offering four sorts — case grade, case type, priority level code, and zipcode — on the accelerated list. A user-selectable sort is meaningless under strict 99a-then-99b enforcement, since only the top row would ever be selectable. Read alongside "accelerated cases cannot be skipped," which speaks to the set rather than to internal ordering, the legacy behaviour is that all accelerated cases must be taken before lower-priority inventory, in whatever order the manager chooses. The requirements document phrase "priority alpha order starting with 99a" is therefore best read as the **display** sequence, which reconciles both documents. `displaySequenceStart` is informational; do not build an order-of-selection gate against it without written confirmation from the business owner.

**Acceptance criteria**
- Given an RO with 99a–99e cases aligned by ZIP and grade, all such cases are returned in approved sequence starting at 99a.
- Given a P99 case outside the RO's ZIP alignment, it is excluded from that RO's set.
- Given an International program case, it is excluded regardless of priority; `restrictionActive` is false for International ROs (Scenario 8).
- `restrictionActive` is false when the RO has zero eligible MA cases (Scenario 3).
- The rule set exists once and is consumed by all other stories.

**Critical path.** Everything else depends on this. It does not parallelize; build and test it first.

---

### BE-B — Mandatory Accelerated read endpoints
**28–36h**

As a Group Manager, I need to see Priority 99 inventory scoped to a single RO, scoped to my whole group, summarised as counts, and drillable to case detail.

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

Column set must match the MTEST Auto Selection Priority 99 layout: Priority Alpha, Case Type, Grade, HINF/941, Balance (Case), Taxpayer Name, City, State, Zip, TIN, TIN File Source, Potential Assignment Number, Current Assignment Number (Queue/Hold File), Flag–Queue Pick, Date Assigned Queue File.

Add **QIND** (Queue Indicator) from the legacy accelerated screen. The user guide defines four values: S-Selected, P-Pending, H-Hold and K-Skipped. Also carry **ZIP ASSN TO RO**, the RO number aligned to the case's ZIP code, which the guide lists as a distinct field from the case's own assignment number.

**Implementation notes**

*Sequence in the database, not in application code.* Ordering applied after fetch breaks pagination — page two is sorted independently of page one, producing exactly the arbitrary ordering the requirement document flags as a critical defect. Push ordering into the query and make it part of the index strategy.

*Selected and Pending rows sort to the top, above priority sequence.* The user guide is explicit: "The selected and pending cases will be shown at the beginning of the list with an S-Selected or P-Pending in the QIND column." So the ordering is a two-key sort — QIND status first (S and P above everything else), then approved priority sequence within each group. This is easy to miss and produces a visibly wrong screen against legacy if it is.

*Handle all four QIND values, not just two.* S-Selected and P-Pending are the states in the requirements document, but the guide adds H-Hold and K-Skipped. K-Skipped is worth a question to the business owner, since accelerated cases are supposed to be unskippable — it likely originates from a non-accelerated context, but confirm rather than assume the value never appears on this list.

*ROID is the current IDRS assignment number*, per the guide. Do not conflate it with Potential Assignment Number or with the ZIP-aligned RO.

*Potential and Current Assignment Number are different fields.* Both appear in the MTEST layout and both must be carried. Observed values are Potential 2501-1405 and Current 2500-7000 on the same row; they are not interchangeable.

*Use decimal for balances.* Observed values run to $55,678,160.93. Floating point will produce reconciliation failures in BE-E.

*TIN appears in two formats.* EIN style (46-3307286) and SSN style (452-25-7936) both occur in the data. Masking and display rules almost certainly exist elsewhere in ENTITY — reuse them rather than writing new ones, since PII display rules are a compliance surface.

*A case can align to more than one RO.* ZIP sets overlap between ROs in a group. In the group-scoped list, deduplicate by case and carry the list of aligned ROs on the row, rather than emitting the case once per aligned RO. The legacy F2 header shows a single queued and listed count, so double-counting will be visible immediately.

*Derive counts from the same query as the list.* Group Summary counts must reconcile exactly to the case lists. The reliable way to guarantee that is a count aggregate over the same query rather than a separately maintained count query, which will drift the first time a rule changes.

*Avoid N+1 on Group Summary.* The screenshot shows eight employees per group. Compute all per-RO counts in one grouped query rather than looping ROs.

*Check the case detail dependency early.* Case summary, modules, activity, time and name/address should proxy existing endpoints. If the existing case-detail endpoint requires an assignment context that a queued, unassigned case does not yet have, that is a gap worth finding in week one rather than week three.

**Acceptance criteria**
- RO list is filtered by PROID; group list is the deduplicated union across all ROs in the group, each row carrying its aligned RO.
- Ordering is QIND status first — Selected and Pending rows at the top — then approved prioritization sequence, 99a first, within each group. Pagination does not alter sequence.
- QIND is returned for every row and supports S-Selected, P-Pending, H-Hold and K-Skipped.
- Group list header shows queued (unassigned) and listed counts as separate values (Legacy parity: `QUEUED: 45  LISTED: 45`).
- Counts account for Queue inventory, Group Manager Hold File, and assigned inventory, by priority and grade, and reconcile exactly to the corresponding case list.
- Case summary, modules, activity, time and name/address are reachable for any listed case.

---

### BE-C — Restriction enforcement interceptor
**22–28h**

As the ENTITY platform, I need one interception layer that decides what a manager may do while Mandatory Accelerated is active, so the restriction cannot be bypassed by calling the API directly or navigating differently.

**Subtasks**
1. Service-layer interceptor and structured rejection
2. Apply to Auto Select, ZIP Code Select and all other selection paths
3. Apply to Manager Queue Control (Hold/Skip Date)
4. Exception allowlist for Query and Assign by TIN

```
409 {
  "errorCode": "MANDATORY_ACCELERATED_ACTIVE",
  "message": "Priority 99 inventory must be assigned first.",
  "roAssignmentNumber": "2710-3910",
  "remainingCount": 45,
  "redirect": "/case-assignment/mandatory-accelerated?ro=2710-3910"
}
```

**Implementation notes**

*Intercept at the service layer, not the controller, and not on URL patterns.* A URL-pattern filter is bypassed the moment anyone adds a route. The requirement is explicit that a different screen, search, sort or workflow must not defeat the restriction, which means the check has to sit below every entry point rather than in front of some of them.

*Make the selection method an explicit parameter on the request.* The exception allowlist should read a declared selection method (AUTO_SELECT, ZIP_CODE_SELECT, QUERY, ASSIGN_BY_TIN), not infer permission from which endpoint was called. Inference means any new endpoint silently defaults to permitted, which is the wrong failure direction for a control function.

*Resolve the effective group from the impersonation context.* The screenshots show a National Analyst operating with "Viewing as: Group 271039" in the header. If the interceptor reads the actor's own group rather than the impersonated group, that path bypasses enforcement entirely. This is the most likely security gap in the whole epic.

*Re-check inside the write transaction.* The pre-check is advisory. Two managers assigning the last P99 case at the same moment will both pass the pre-check. The authoritative check belongs inside the assignment transaction in BE-D.

*Return 409, not 403.* A 403 reads as an authorization failure and will be triaged to the wrong team. This is a business-rule rejection, and the body carries the count and redirect the UI needs.

*Block the write, not the read, on Hold/Skip.* Managers should still be able to open the Hold/Skip tab and see current state while the restriction is active. Only the date-setting action is blocked.

*Every rejection is an audit event.* Wire the rejection path into BE-E from the start. For a control function, the record of blocked attempts is most of the evidence that the control works.

**Acceptance criteria**
- Scenario 1: selecting a lower-priority case through any method besides Query or Assign by TIN is rejected with the structured error.
- Scenario 6: changing screen, search, sort or navigation path does not bypass the gate.
- Hold/Skip Date is rejected while the restriction is active and re-enabled automatically when satisfied.
- Query and Assign by TIN allow any priority level and do not force P99 selection.
- Scenario 7: using an exception does not clear the restriction for other inventory and does not decrement the remaining count.
- Enforcement applies to a National Analyst operating under "Viewing as: Group NNNNNN".

---

### BE-D — Mandatory Accelerated assignment write path
**22–28h**

As a Group Manager, I need to assign a Priority 99 case, have it locked against unpick, and have the restriction update or lift accordingly.

**Subtasks**
1. Assignment endpoint with reason-code stamping
2. Schema migration for selection method on the selection record
3. Unpick rejection for Mandatory Accelerated selections
4. Remaining-count recalculation and restriction release

```
POST /api/case-assignment/mandatory-accelerated/assign
{
  "tin": "46-3307286",
  "targetRoAssignmentNumber": "2710-3910",
  "reasonCode": "MANDATORY_ACCELERATED_CASE"
}
```

**Implementation notes**

*Store a selection-method enum, not a boolean.* A `mandatory_accelerated` flag answers only one question. An enum recording how the selection was made (MANDATORY_ACCELERATED, AUTO_SELECT, ZIP_CODE_SELECT, QUERY, ASSIGN_BY_TIN) also gives BE-E the exception-usage audit it needs, and gives Scenario 7 something concrete to assert against.

*One transaction covers everything.* Verify eligibility, verify the case is still in available queue inventory, create the selection, stamp method and reason, recalculate the remaining count, invalidate the status cache. If count recalculation sits outside the transaction, the UI shows stale numbers immediately after the action that changed them.

*Put the unpick block on the selection state machine.* Implementing it as a special case inside the unpick controller means any second unpick path — bulk unpick, an admin tool, a future screen — misses it. The rule is a property of the selection, so it belongs on the selection.

*Store a reason code, render the display string.* Legacy shows "REASON FOR CASE REQUEST: MANDATORY ACCELERATED CASE" as screen text. Persist the code and localise or render at display time.

*Optimistic lock on the case row.* Two managers, same case. One commits, the other receives a specific "case no longer available" error rather than a generic failure or, worse, a duplicate assignment.

*Put alignment validation behind a config flag.* Open Question 3 asks whether a manager may assign a P99 case to an RO it is not ZIP or grade aligned to. Legacy says any case to any RO in the group; the eligibility rules say otherwise. Build the endpoint to accept a target RO and gate the alignment check on configuration, so the answer can change without a code change.

*Confirm migration behaviour for in-flight selections.* Cases already in Selected or Pending status at cutover need a default. Open Question 8 determines whether they also need a backfill to be treated as Mandatory Accelerated retrospectively.

**Acceptance criteria**
- Reason for case request is stamped as "Mandatory Accelerated Case" (Legacy parity).
- From the group-level screen, a case may be assigned to any RO in the group (pending Open Question 3).
- Selection follows the established GM Case Assignment lifecycle and lands in Selected/Pending status (Scenario 4).
- Scenario 4: Mandatory Accelerated selections are rejected for unpick with a distinct error code; ordinary S-Selected cases remain unpickable before P-Pending.
- Scenario 2: the restriction persists until all applicable cases for that RO are selected.
- Scenario 5: after the final selection, normal assignment methods become available with no manual refresh or re-login.
- Assignment is rejected if the case has left available queue inventory.

---

### BE-E — Audit trail and counts reconciliation
**16–20h**

As a compliance stakeholder, I need an audit trail of Mandatory Accelerated activity and assurance that displayed counts match source data.

**Subtasks**
1. Audit logging for assignments, blocked attempts, exception usage and unpick attempts
2. Reconciliation harness comparing counts and case sets against source data

**Implementation notes**

*Append-only.* No updates, no deletes, no soft-delete flag that an admin screen can toggle. If the table supports mutation, the audit trail is not evidence.

*Record the impersonation chain, not just the actor.* "S. VAINER, National Analyst, acting as Group 271039" is the meaningful record. An actor-only log loses the fact that someone was operating outside their own group.

*Log rejections at the same fidelity as successes.* The requirement is a control, so the interesting audit questions are about attempted bypasses: who tried, against which RO, through which selection method, and what the remaining count was at the time.

*Reconcile against production-equivalent data, not a fixture.* A hand-built fixture proves the code agrees with itself. The reconciliation is only meaningful against real prioritization output, which is where the sequencing defect was found in the first place.

*Reconcile three relationships:* Group Summary counts against each RO list, the union of RO lists against the group list, and the group list against source prioritization data. A mismatch in any one localises the fault quickly.

*Make it runnable on demand in MTEST.* This is the artifact to show during review, not a test that ran once in CI. Build it as a callable job with readable output.

**Acceptance criteria**
- Each audit record captures actor, acting-as group where impersonating, RO, case identifier, selection method, timestamp and outcome.
- Scenario 9: P99 counts and presented cases reconcile to the underlying business rules and source data.
- Discrepancies are reported, not silently absorbed.

**Do not cut this.** This is a federal tax system and the control function is exactly what gets examined.

**Backend subtotal: 120–154 hours**

---

## 4. Frontend Stories

### FE-A — Mandatory Accelerated case list component
**22–28h**

As a Group Manager, I need a Priority 99 case list I can view scoped to my whole group or to a single RO.

**Subtasks**
1. Shared list component using the MTEST Priority 99 column layout
2. Group-scoped view (replaces Legacy F2)
3. RO-scoped view filtered by PROID
4. Sequencing guardrails

**Implementation notes**

*One component, parameterized twice.* The group view and RO view differ by data source and by the presence of the aligned-RO column. Everything else — columns, formatting, sequencing, pagination — is identical. The requirement document points at the same MTEST Priority 99 layout for both.

*Fifteen columns need horizontal scroll with a frozen first column.* Both the legacy screen and the MTEST screenshot scroll horizontally. Freeze Priority Alpha or Taxpayer Name so the user keeps their place while scrolling to balance and assignment numbers.

*Sort server-side, always.* Client-side sorting reorders the current page only, which is actively misleading on a paginated list of 45 or 145 records and reproduces the defect this epic is meant to fix.

*Offer the legacy sort set, not arbitrary column sorting.* The user guide describes F6-SORT as "a limited number of sorts" with exactly four options: case grade, case type (TDA/TDI), priority level code, and zipcode. Match that set for parity rather than making all fifteen columns sortable. A constrained list is also easier to index well.

*Re-sorting is a review action, not a preference.* The user may re-sort to scan the data, but navigating away and back returns to approved sequence. Do not persist sort state for these screens.

*Selected and Pending rows pin to the top* regardless of the chosen sort, per the user guide. The QIND column carries S, P, H or K and should be visually distinct enough that a manager can see at a glance which rows are already actioned.

*Show both counts in the header.* Queued (unassigned) and listed are different numbers and managers read them together — queued tells them what is left to do, listed tells them the size of the screen. Showing only one loses the signal.

*F4-VIEW BAL needs no modern equivalent.* Legacy toggles the display between TIN and balance due because the terminal screen is too narrow for both. The modern layout carries fifteen columns with horizontal scroll, so both are present simultaneously. Do not port the toggle.

*The empty state carries meaning.* An RO with zero Mandatory Accelerated cases is the signal that normal assignment methods are available. Say that explicitly rather than showing a blank table.

*Match legacy record-count conventions.* Legacy shows "Record 1 of 45" and "QUEUED: 45  LISTED: 45". Managers read these numbers as a workload signal, so keep the format familiar.

*Format balances as right-aligned currency* to eight figures. Mixed alignment across a column of dollar values makes scanning for the large cases slow, and finding the large cases is the point of the screen.

**Acceptance criteria**
- Columns match the MTEST Auto Selection Priority 99 layout, plus QIND and ZIP ASSN TO RO.
- Selected and Pending rows appear at the top of the list; remaining rows follow approved priority/Alpha sequence, 99a first. Pagination preserves sequence.
- Sort options are limited to case grade, case type, priority level code and zipcode (Legacy F6 parity).
- Group view shows queued and listed as separate counts in the header, and each row's aligned RO.
- RO view lists only cases aligned to that RO.
- Scenario 10: sequencing is the approved methodology, not arbitrary order.
- Scenario 6: re-sorting a column for review does not change enforcement order or permit bypass.

---

### FE-B — Restriction gating and routing
**22–28h**

As a Group Manager, I need the application to consistently detect an active restriction, route me to the right screen, show me what remains, and unlock normal methods when I am done.

**Subtasks**
1. "Group Mandatory Accelerated" button on Group Summary
2. Gate on RO case selection, blocking Auto Select and ZIP Code Select
3. Remaining-count indicator with live decrement and automatic unlock
4. Consistent handling of `MANDATORY_ACCELERATED_ACTIVE` across every assignment path

**Implementation notes**

*One status hook, consumed everywhere.* Scattering `restrictionActive` checks across screens guarantees one of them is missed or drifts. Centralise the fetch and the derived state.

*Hiding controls is not enforcement.* The UI must also handle the server's 409, because the server can reject when the client believed the action was fine — the count changed between page load and click. Both paths must produce the same message.

*The message needs the count and a working route.* "This action is blocked" without a next step becomes a support ticket. Include how many cases remain and a link to the correct screen for that RO or group.

*The counter tracks queued, not listed.* Only the queued (unassigned) count decrements as assignments are made, and the restriction lifts when it reaches zero. Binding the indicator to the listed count produces a number that never moves and an unlock that never fires.

*Refetch status on assignment success rather than polling.* The count changes on a known event. Polling adds load to an endpoint that is already hit on most page loads.

*Keep Query and Assign by TIN visibly available.* These are the two sanctioned routes. If the manager cannot tell which paths remain open, the gate reads as a system fault rather than a control, and the support burden lands on your team.

*Place the Group Mandatory Accelerated button in the Case Assignment sub-tab strip*, per the mock in the requirement document, adjacent to Group Summary.

**Acceptance criteria**
- Scenario 1: any blocked attempt shows a clear message with the remaining count and a link to the applicable screen, for either the RO or the group.
- Query and Assign by TIN remain accessible throughout.
- The counter decrements after each selection without a full page reload; at zero, normal methods become available immediately (Scenario 5).
- No raw error codes or stack traces reach the user.
- The Group Mandatory Accelerated button is hidden or disabled when the group has no eligible inventory.

---

### FE-C — Case review and assignment flow
**18–22h**

As a Group Manager, I need to review a case and then assign it to an RO, from the Mandatory Accelerated list.

**Subtasks**
1. Case review panel (summary, modules, activity, time, name/address)
2. Assign action with RO picker

**Implementation notes**

*Keep case-to-case navigation without returning to the list.* Legacy binds F2, F3, F4 and F5 to First, Prev, Next and Last on the assignment screen, and the example group has 45 cases to work through. Forcing a return to the list between every assignment turns a manageable task into a tedious one.

*Match the legacy RO display format.* Legacy renders the target as "1405-B. O'BRIEN" — assignment number and name together. Managers recognise ROs this way.

*Confirm before commit, and say why.* Mandatory Accelerated selections cannot be unpicked, unlike ordinary selections. That difference must appear in the confirmation step, not be discovered afterwards when the manager tries to reverse it.

*Preserve list position on return.* Scroll position and page number both. On a 45-row list this is the difference between a usable screen and an irritating one.

*After a successful assignment*, refresh the list, decrement the count, and either advance to the next case or return to the list at the same position — decide which with the business users, since it depends on whether they work sequentially or pick selectively.

**Acceptance criteria**
- RO picker is limited to ROs in the manager's group.
- Case information shows TIN, name control, taxpayer name, assignment queue date, balance due and taxpayer grade (Legacy parity).
- Reason displays as "Mandatory Accelerated Case".
- The confirmation step states that the selection cannot be unpicked.
- On success the list refreshes and the remaining count decrements.
- Returning to the list preserves scroll position and pagination state.

---

### FE-D — Disabled states and Group Summary counts
**10–14h**

As a Group Manager, I need controls that are unavailable during a restriction to say so, and Group Summary counts to be accurate.

**Subtasks**
1. Unpick disabled for Mandatory Accelerated selections
2. Hold/Skip disabled while restriction active
3. Group Summary Priority 99 column wired to corrected counts

**Implementation notes**

*Disable with an explanation rather than hiding.* A control that vanishes reads as a bug and generates support traffic. A disabled control with a reason teaches the rule.

*The explanation must be accessible*, not tooltip-only. Screen reader users need the same reason, and this is a federal application.

*Group Summary Priority 99 is an existing column.* The screenshot already shows it in the eight-employee table. This is a data wiring change to the corrected counts from BE-B, not a new column.

*Hold/Skip re-enables automatically.* When the restriction lifts, the manager should not need to reload or re-navigate to regain the control.

**Acceptance criteria**
- Disabled controls carry a reason available on hover, on focus, and to assistive technology.
- Ordinary S-Selected cases retain normal unpick behaviour.
- Hold/Skip controls re-enable automatically once the restriction lifts.
- Priority 99 values on the Group Summary employee table reconcile with the case list for each RO.

**Frontend subtotal: 72–92 hours**

---

### Accessibility

Accessibility sits in the definition of done for FE-A through FE-D: blocked-state messaging available to assistive technology and not conveyed by colour or placement alone, new tables keyboard navigable. This is cheaper than retrofitting.

Formal 508 validation still needs booking as a QA activity. It is not a development story and is not in this estimate.

---

## 5. Deferred — separate epic

These arrived in the same requirements document but do not block Mandatory Accelerated enforcement.

| Story | Estimate |
|---|---|
| DEFECT: Priority 99 sequencing not upheld in modern case assignment (found 08/26/2026) | 4h spike, then 16–24h |
| National Queue: query full national scope, remove CSV export | 12–16h |
| Remove National Queue tab from RO case selection | 2–3h |
| National Queue UI under Query | 8–10h |

**Deferred subtotal: 42–57 hours**

The sequencing defect affects screens that already exist. New Mandatory Accelerated screens are built with correct sequencing from day one under FE-A, so enforcement does not wait on the fix. Run the spike early regardless — the fix may sit in the query layer, the ordering clause, or the prioritization service, and the depth is unknown until someone looks. It likely intersects the earlier DB performance work.

On the National Queue: removing the record display cap on a national-scope query is a performance risk. Confirm expected row volume before committing the estimate.

---

## 6. Timeline

Development hours at 8h/day, with QA running alongside rather than after.

| Scenario | Dev | Production-ready |
|---|---|---|
| **Full scope**, 2 BE + 2 FE | 11–15 days | **~3–4 weeks** |
| **Full scope**, 1 BE + 1 FE | 22–28 days | **~6–7 weeks** |
| **September MVP**, 2 BE + 2 FE | 8–10 days | **~2.5 weeks** |

### Sequencing, full scope with 2 BE + 2 FE

| Phase | Days | Backend | Frontend |
|---|---|---|---|
| 1. Foundation | 1–5 | BE-A (serial, one dev); second dev on migration, fixtures, BE-E scaffolding | Contract agreed, scaffolding on mocks |
| 2. Build out | 6–11 | BE-B, BE-C, BE-D across both devs | FE-A, FE-B in parallel |
| 3. Close out | 12–15 | BE-E, integration | FE-C, FE-D, integration |

### September MVP

BE-A, BE-C, BE-D plus FE-B and the RO-scoped half of FE-A. Roughly 112–144 hours. Delivers RO-level detection, the enforcement gate, assignment, unpick prevention and dynamic release.

**What the MVP costs you:** the group-level Mandatory Accelerated screen. Managers use F2 today to see every P99 case across the group and assign any of them to any RO. Dropping it is a real parity regression against legacy, not a cosmetic one, and needs to be a conscious decision.

### Story size

BE-A at 32–42h is roughly a week of one developer's work with nothing demoable until the end, and it does not split across developers. Creating the listed subtasks in JIRA restores progress visibility without separate story ceremonies.

### Timeline risk

Legacy Release 1 lands September 2026 and Modern ENTITY must support the functionality at implementation. That is this month. Even the September MVP at ~2.5 weeks assumes a start now, four developers, and Open Questions 1–3 answered in the first days. Full scope does not fit September under any staffing.

---

## 7. Open Questions — resolve before sprint planning

Several are contradictions in the source document rather than gaps in reading it.

1. **Strict alpha sequencing, or just P99 before lower priority?** *Likely resolved — confirm in writing.* The user guide describes an F6-SORT with four user-selectable sorts on the accelerated list, which is incompatible with strict 99a-then-99b enforcement, and frames "cannot be skipped" as a property of the set rather than the order. The reading that reconciles both documents is that all accelerated cases must be taken before lower-priority inventory, in any order, with "priority alpha order starting with 99a" describing the display sequence. This is the largest swing factor in BE-C, so get the business owner to confirm rather than proceeding on inference.

1a. **K-Skipped on the accelerated list.** The guide defines QIND values S, P, H and K, where K is skipped — but accelerated cases are described as unskippable. Does K ever appear on this list, and if so, under what circumstances?

1b. **ENTITY Priority Status screen (F3).** The guide documents a separate legacy screen showing case counts at each priority level, which appears to be the legacy home of the requirements document's "Support Priority 99 visibility" bullet — counts across Queue, Group Manager Hold File and assigned inventory, by priority and grade. Modern Group Summary carries some of this (Priority 99, High Priority Queue, Open, Pending, Top, Bottom, 85%). Confirm whether the existing Group Summary satisfies that requirement or whether a modern F3 equivalent is a separate story. Not currently estimated in this backlog.

2. **Enforcement vs inventory targets.** If an RO has 45 eligible P99 cases but their Target Level Inventory Range tops out well below that, must the manager over-assign? How does this interact with Inventory Adjustment?

3. **Group-level alignment override.** Can a manager assign a P99 case to an RO it is not ZIP or grade aligned to? Legacy says any case to any RO in the group, which contradicts the eligibility criteria in BE-A.

4. **Cross-RO satisfaction.** If a case aligned to RO-A is assigned to RO-B from the group screen, does that decrement RO-A's count, RO-B's, or neither?

5. **National Queue volume.** Current record display maximum, and expected row count for a full national query. Sizes the deferred work and its performance risk.

6. **Legacy "FIX RO" checkbox** on the accelerated select screen — in scope for modern, or dropped?

7. **Role scope.** Does the restriction apply only to Group Managers, or also to a National Analyst under "Viewing as: Group NNNNNN"? The screenshots show that path and it is the most likely enforcement gap.

8. **Existing selections at cutover.** Do cases already Selected or Pending when the release ships fall under these rules, or only new selections? Determines whether a backfill is needed.

9. **Recalculation cadence.** Real time, on prioritization refresh, or nightly? Affects caching and the accuracy of the live count.

10. **Grade rule.** Is case grade vs RO grade an exact match, an at-or-below comparison, or a lookup table? Not stated in the requirement document.

11. **Component reuse.** Are the Priority 99 query table and Assign by TIN screen reusable components or one-off pages? Directly affects FE-A and FE-C.

12. **User Guide page 10** — should settle questions 1, 3 and 6.
