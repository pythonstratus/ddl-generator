# Mandatory Accelerated Case Assignment — Modern ENTITY
## JIRA Backlog

**Source:** Requirements document, 11 screenshots, GM Case Assignment User Guide page 10 (Rev. 04-2018), and the Case Assignment Modernization walkthrough with Sarah on 01 Sep 2026.

**Precedence:** the 01 Sep walkthrough is the most recent and most authoritative source — it is a live side-by-side demonstration of the reactivated function in the development server against current production. Where it differs from the requirements document or the 2018 user guide, the walkthrough wins. Items it settled are marked **[Confirmed 01 Sep]** throughout.

**Sarah's availability:** in on 02 Sep, then out until 24 Sep. Anything still open must be answered on 02 Sep or routed to Eric, Nicole or Steve.
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

*Two counts, not one.* **[Confirmed 01 Sep]** The header carries queued and listed as separate live values, and Sri flagged it explicitly as a requirement to adhere to. Sarah's demonstration: a list of six accelerated cases where one has been assigned shows listed 6, queued 5. Queue membership is derived from the assignment number — the last four digits `7000` denote the queue, so a case leaves the queued count when its assignment number changes to the RO. Listed counts everything shown including Selected and Pending rows and stays flat. The restriction lifts when queued reaches zero.

*Enforcement is set-based, not order-based.* **[Confirmed 01 Sep]** Sarah: the reactivation hides everything below Priority 99 until all 99s are selected, and only once all of them are selected does anything else become visible for assignment. There is no gate on the order of selection within the accelerated set — she selects freely across the list in the demonstration. Display order remains strictly priority alpha, 99a first, which is a separate and non-negotiable rule covered in the companion note on case assignment sorting.

*ZIP alignment determines whose count it is, not who may receive it.* **[Confirmed 01 Sep]** Sarah: "The manager has complete control of all of the cases aligned to the group. They can assign any case to any person. We make it easier for them by aligning by zip code. But this is not a rule. This is a suggestion." So ZIP alignment governs which RO's Priority 99 count a case falls into and therefore whose restriction it drives, but it does not constrain the assignment target. The group-level screen exists precisely so a manager can redistribute — Sarah's example was one RO holding 55 accelerated cases while another held 5.

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

*The Group Summary Priority 99 column does not decrement on selection.* **[Confirmed 01 Sep — this corrects an earlier assumption in this backlog.]** Brian asked directly whether an RO showing 46 would drop to 44 after two assignments. Sarah's answer was no: the column holds at 46 and the Pending column rises to 2. The Priority 99 figure only falls once cases are actually delivered, not when they are selected. Her reason is operational — an emergency back-door exists to unselect a case on a manager's behalf before it reaches Pending, and the count reflects what is still recoverable.

This means there are **two different numbers moving in opposite directions**, and conflating them is the most likely counting bug in this epic:

| Number | Location | On selection |
|---|---|---|
| Queued | Mandatory Accelerated screen header | Decrements |
| Listed | Mandatory Accelerated screen header | Unchanged |
| Priority 99 | Group Summary employee table | Unchanged |
| Pending | Group Summary employee table | Increments |

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
5. Restrict Query sub-tabs to Priority 99 and Create Query **[New — confirmed 01 Sep]**
6. Restrict Reports case selection to Priority 99 **[New — confirmed 01 Sep]**

**Newly confirmed scope from the 01 Sep walkthrough.** Two areas were not in the original requirements document and add work to this story:

*Query sub-tabs are restricted, not blocked.* When the restriction is active, Case Assignment → Query defaults to Priority 99 results, and every sub-tab is greyed out except **Priority 99** and **Create Query**. That means Million Dollar Cases, HINF, Egregious 941, High Priority Cases, National, Local, My Saved Queries, Query Results and National Queue are all disabled. Create Query stays live because it is one of the two sanctioned workarounds — Sarah's phrasing was that it should not be advertised but must exist.

*Reports carry the same restriction.* Where a manager selects cases from a report, only Priority 99 cases may be picked while eligible inventory exists. Sarah noted she has not explored the modern Reports implementation, so scope here is less certain than elsewhere — confirm what modern Reports currently supports before estimating this subtask firmly.

*Pending needs no restriction.* It is a list view only.

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

*An emergency unselect capability exists and is not a user function.* **[Raised 01 Sep]** Sarah described past incidents where a selected-but-not-yet-Pending accelerated case had to be unselected on a manager's behalf, and developers performed it manually in legacy. Managers must never have this ability. Confirm whether modern needs a supported administrative path or whether a controlled data fix remains acceptable — this is the reason the Group Summary Priority 99 count holds until delivery rather than dropping at selection, so the two behaviours are linked.

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

*A lighter implementation was proposed on 01 Sep and is worth costing before building the RO-scoped screen as specified.* The RO case selection screen already has a dropdown defaulting to "Top 200". Sarah's suggestion, refined by Islam, was: on page load, if the RO's Priority 99 count is greater than zero, default that dropdown to Priority 99 and disable it until every accelerated case is selected; if the count is zero, default to Top 200 and behave normally. That reuses the existing screen and filter rather than building a separate RO-scoped view, and Sarah explicitly framed it as less of a redo than the team was expecting.

The group-level screen is still needed as a distinct surface — it is the F2 equivalent and has no existing analogue. But if the dropdown approach holds for the RO path, FE-A reduces materially. **Spike this first**; it is the largest single estimate reduction available in the frontend.

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
5. Grey out Query sub-tabs except Priority 99 and Create Query **[New — confirmed 01 Sep]**
6. Default Query to Priority 99 results while the restriction is active **[New — confirmed 01 Sep]**

**Implementation notes**

*Make the Group Mandatory Accelerated button prominent, not tucked away.* **[Confirmed 01 Sep]** Sarah was direct about this: accelerated work is the first thing a manager needs to deal with every time, so it should not require hunting. Eric's mock places it under Group Summary. Adding it only to the existing tab strip was floated and Sarah accepted it as workable, but stated a preference for the explicit button.

*Group Summary itself does not change.* The employee table lists all employees all the time, restricted or not. Do not filter or reorder it.

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

### Dates and go-live gating

**[Confirmed 01 Sep]** Two dates matter and they are not the same thing:

| Date | Event |
|---|---|
| 08 Sep 2026 | Director of Collection issues the reactivation memo to all of Collection |
| 12 Sep 2026 | Mandatory Accelerated activates in **legacy** production |

Modern ENTITY does not have to ship by 12 September. What was established on the call is stronger in a different way: **modern case assignment cannot go to production at all until this is built.** Brian's position was unambiguous, and Islam confirmed the absence of any gateway layer between Group Summary, Query and Reports in modern today means this is genuinely new architecture rather than a configuration change.

So Mandatory Accelerated is now a launch blocker for modern case assignment, not a fixed-date deliverable. The 3–4 week full-scope estimate is a statement about when modern can launch.

**The practical constraint is Sarah's availability, not the activation date.** She is in on 02 September and then out until 24 September. Every remaining open question in Section 7 needs an answer on 02 September or it waits three weeks — and three weeks is most of the build. Eric, Nicole and Steve are the fallback contacts but Sarah is the source for the business rules.

### Newly added scope

The 01 September walkthrough added Query sub-tab restriction and Reports restriction, neither of which appeared in the original requirements document. Both land in BE-C and FE-B. Reports in particular is loosely specified — Sarah has not reviewed the modern implementation — so treat that subtask as an estimate to firm up rather than a committed number.

Offsetting that, the dropdown-default approach for the RO selection screen may reduce FE-A substantially. Net effect on the totals above is likely neutral to slightly positive, but both need a spike before the numbers are re-committed.

### Legacy code reuse — investigate before building

Sarah raised on the call that Mandatory Accelerated logic was requested for use during modern development precisely because reactivation was anticipated, and asked whether it made it into the modern codebase or was omitted. Samuel and Diane have been working on the legacy side of this function for the past two months and are the best available resource. **Check this before writing BE-A.** If the rules were carried across and are dormant, the eligibility engine estimate drops sharply.

---

## 7. Question Status

The 01 September walkthrough resolved most of what was outstanding. What remains is short, and Sarah is available on 02 September only.

### Resolved

| # | Question | Answer |
|---|---|---|
| 1 | Strict alpha sequencing, or all P99 before lower priority? | **All P99 before lower priority, any order within the set.** Everything below 99 is hidden until every 99 is selected. Display order is still strictly priority alpha. |
| 2 | Enforcement vs inventory targets | Not a conflict. The manager must select all applicable accelerated cases; the group-level screen exists so they can redistribute across ROs rather than overload one. |
| 3 | Can a manager assign to a non-aligned RO? | **Yes.** ZIP alignment is a suggestion, not a rule. The manager has complete control of all cases aligned to the group and may assign any case to any person in it. |
| 4 | Cross-RO satisfaction | Follows from 3. Once assigned, the case leaves the queue and therefore leaves the queued count of whichever RO it was aligned to. |
| 6 | Legacy FIX RO checkbox | Sarah indicated it is not functionality modern needs. Low confidence in the transcription — worth ten seconds of confirmation on 02 Sep. |
| 7 | Role scope | Case assignment is restricted to managers and acting managers, with secretaries occasionally in a supporting role. Treat as a management control. The National Analyst "Viewing as" path is presumably an acting-manager route and still needs enforcement testing. |
| — | Priority 99 count behaviour on Group Summary | **Does not decrement on selection.** Holds until delivery; Pending rises instead. |
| — | Queued vs Listed | **Both required.** Confirmed as a requirement to adhere to, with live divergence as selections are made. |

### Still open — needs Sarah on 02 September

1. **Reports scope.** Sarah has not reviewed the modern Reports implementation. Confirm what case selection from a report currently supports before BE-C subtask 6 is estimated.

2. **Emergency unselect.** Does modern need a supported administrative path to unselect a Mandatory Accelerated case before it reaches Pending, or does a controlled data fix remain acceptable? Managers must never have it either way.

3. **K-Skipped on the accelerated list.** The user guide defines QIND values S, P, H and K. Can K occur on this list given accelerated cases cannot be skipped?

4. **Grade criteria.** Whether the case grade against RO grade rule is exact match, at-or-below, or a lookup table. Not stated in any source so far.

5. **Existing selections at cutover.** Do cases already Selected or Pending when modern launches fall under these rules, or only new selections?

6. **Recalculation cadence.** Is the eligible set computed in real time, on prioritization refresh, or nightly? Affects caching and live count accuracy.

### Still open — for the development team, not Sarah

7. **Legacy code reuse.** Did Mandatory Accelerated logic carry into the modern codebase during initial development? Ask Samuel and Diane. Materially affects BE-A.

8. **Model score calculation in modern.** The alpha ordering within a priority band is driven by model score against balance, computed by a dedicated table in legacy. Santosh was to confirm whether modern reproduces this. Covered in the companion note on case assignment sorting.

9. **Component reuse.** Are the Priority 99 query table and Assign by TIN screen reusable components or one-off pages? Affects FE-A and FE-C.

10. **National Queue volume.** Current record display maximum and expected row count for a full national query. Sizes the deferred work.
