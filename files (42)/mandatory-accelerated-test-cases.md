# Mandatory Accelerated Case Assignment — Test Case Document
## Modern ENTITY | GM Case Assignment

**Traces to:** Requirements document (Functional Requirements and 10 Case Use Scenarios), GM Case Assignment User Guide p.10 Rev. 04-2018, and the Mandatory Accelerated backlog (BE-A–BE-E, FE-A–FE-D).

**Environment:** MTEST with production-equivalent prioritization data.

---

## 1. How to Use This Document

Mandatory Accelerated is an assignment **control**, not a display feature. The most important tests in this document are the ones in Section 7, where the tester actively tries to defeat the restriction. A build that passes Sections 1–6 and fails Section 7 has not implemented the requirement.

Each case gives a **Fail if** line. Use it. It exists so two testers running the same case reach the same verdict rather than recording "works as expected" against different behaviour.

**Severity guidance for defects raised against this document:**

| Severity | Definition |
|---|---|
| Critical | The restriction can be bypassed, or a case is assigned that should not have been. Any Section 7 failure is Critical by default. |
| High | Enforcement is correct but a manager cannot complete required work, or counts do not reconcile. |
| Medium | Display, sequencing or messaging is wrong but enforcement holds. |
| Low | Cosmetic, formatting, or wording. |

**Do not close a Critical defect on the basis of a UI fix alone.** If a bypass was found through the interface, confirm the fix at the service layer as well — see TC-701 and TC-702.

---

## 2. Test Data Prerequisites

**This is the part that will delay testing if it is not prepared first.** None of the enforcement cases can run without ROs carrying specific Priority 99 inventory. Raise data setup as its own task before test execution begins.

| ID | Data required | Used by |
|---|---|---|
| TD-1 | General Program RO with multiple P99 cases spanning alpha 99a–99e, all ZIP and grade aligned | Most of Sections 1–7 |
| TD-2 | General Program RO with zero eligible P99 cases | TC-102, TC-305, TC-802 |
| TD-3 | General Program RO with exactly one eligible P99 case | TC-306, TC-307 |
| TD-4 | International Program RO holding P99 cases | TC-105, TC-801 |
| TD-5 | P99 case outside the target RO's ZIP alignment | TC-103 |
| TD-6 | P99 case whose grade fails the RO grade criteria | TC-104 |
| TD-7 | Group of 6–8 ROs with mixed P99 inventory, at least two with none | TC-201, TC-601, TC-602 |
| TD-8 | Cases already in S-Selected and P-Pending status | TC-203, TC-406 |
| TD-9 | Cases in H-Hold and K-Skipped status | TC-209 |
| TD-10 | One P99 case aligned to two ROs via overlapping ZIP sets | TC-213 |
| TD-11 | P99 case with balance above $50,000,000 | TC-210 |
| TD-12 | P99 cases carrying both TIN formats (EIN nn-nnnnnnn and SSN nnn-nn-nnnn) | TC-211 |
| TD-13 | Group with more than one page of P99 cases (40+) | TC-205 |
| TD-14 | National Analyst account able to operate as "Viewing as: Group NNNNNN" | TC-704, TC-709 |
| TD-15 | Two tester accounts with manager rights over the same group, for concurrency | TC-409 |

**Record the exact case identifiers used** for each TD in a shared sheet before execution. Several cases become irreversible once run — a Mandatory Accelerated selection cannot be unpicked — so data will be consumed as testing proceeds and will need refreshing between cycles.

---

## 3. Blocked and Unblocked Cases

The 01 September walkthrough with Sarah unblocked most of what was outstanding.

**Now unblocked — execute these:**

| Case | Confirmed behaviour |
|---|---|
| TC-306a | All Priority 99 cases must be selected before anything lower is visible, but **order within the accelerated set is the manager's choice**. Execute the loose-reading expectation. |
| TC-402a | A manager **may** assign a case to an RO it is not ZIP aligned to. ZIP alignment is a suggestion, not a rule. |

**Still blocked:**

| Case | Blocked by |
|---|---|
| TC-104 | Grade rule — whether case grade against RO grade is exact match, at-or-below, or a lookup |
| TC-209 (K-Skipped portion) | Whether K-Skipped can appear on the accelerated list at all |
| TC-507, TC-508 | Modern Reports implementation scope, not yet reviewed by the business |

Mark blocked cases Blocked, not Failed. Sarah is available 02 September and then out until 24 September — anything unanswered by end of 02 September stays blocked for three weeks.

---

## 4. Section 1 — Eligibility and Identification (TC-100)

### TC-101 — All Priority 99 alpha scores are identified
*Traces to: Identify applicable P99 inventory · BE-A · Data: TD-1 · Priority: Critical*

1. Open Case Assignment → Group Summary for the test group.
2. Open the Mandatory Accelerated list for the TD-1 RO.
3. Compare the listed cases against the source prioritization data for that RO.

**Expected:** every case with priority alpha 99a, 99b, 99c, 99d and 99e that is queue-available and aligned to the RO appears in the list.
**Fail if:** any alpha score is missing from the list, or any non-P99 case appears in it.

### TC-102 — RO with no eligible Priority 99 inventory
*Traces to: Scenario 3 · BE-A · Data: TD-2 · Priority: Critical*

1. Select the TD-2 RO from Case Assignment.

**Expected:** no restriction is applied. Auto Select and ZIP Code Select are both available.
**Fail if:** any restriction message appears, or any normal assignment method is blocked.

### TC-103 — Case outside ZIP alignment is excluded
*Traces to: Determine applicability by RO · BE-A · Data: TD-5 · Priority: High*

1. Open the Mandatory Accelerated list for the RO the TD-5 case is *not* aligned to.

**Expected:** the TD-5 case does not appear, and does not count toward that RO's queued count.
**Fail if:** the case appears in the RO-scoped list, or inflates the RO's queued count.

### TC-104 — Grade criteria are applied
*Traces to: Determine applicability by RO · BE-A · Data: TD-6 · Priority: High · **Blocked by OQ-10***

1. Confirm the agreed grade rule with the business owner before executing.
2. Open the Mandatory Accelerated list for the RO whose grade fails against the TD-6 case.

**Expected:** the case is excluded or included strictly according to the confirmed rule.
**Fail if:** behaviour differs from the confirmed rule. Do not accept a plausible-looking result without the rule in writing.

### TC-105 — International Program is excluded
*Traces to: Scenario 8 · BE-A · Data: TD-4 · Priority: Critical*

1. Select the TD-4 International RO from Case Assignment.
2. Attempt a normal assignment through Auto Select.

**Expected:** no Mandatory Accelerated restriction is applied. Assignment proceeds using current International programming, unchanged.
**Fail if:** any restriction, message, or Mandatory Accelerated screen appears for an International RO.

### TC-106 — Assigned cases are not treated as available
*Traces to: BE-A · Data: TD-1 · Priority: High*

1. Note the queued count for the TD-1 RO.
2. Assign one Mandatory Accelerated case.
3. Re-open the list.

**Expected:** the assigned case no longer counts as queued (unassigned) inventory.
**Fail if:** the queued count is unchanged, or the case still presents as available for selection.

---

## 5. Section 2 — Display and Sequencing (TC-200)

### TC-201 — Group Mandatory Accelerated list content
*Traces to: Display MA inventory · FE-A · Data: TD-7 · Priority: High*

1. From Group Summary, click **Group Mandatory Accelerated**.

**Expected:** the list shows every eligible P99 case across all ROs in the group, each row carrying its aligned RO. Columns match the MTEST Priority 99 layout plus QIND and ZIP ASSN TO RO.
**Fail if:** any column from the agreed layout is missing, or cases belonging to ROs in the group are absent.

### TC-202 — RO-scoped list is filtered by PROID
*Traces to: Enforce P99 assignment first · FE-A · Data: TD-7 · Priority: High*

1. Select a single RO with Mandatory Accelerated inventory.

**Expected:** only cases aligned to that RO are listed.
**Fail if:** cases aligned to a different RO in the group appear in the RO-scoped view.

### TC-203 — Selected and Pending rows sort to the top
*Traces to: User Guide p.10 · BE-B, FE-A · Data: TD-8 · Priority: Medium*

1. Open a Mandatory Accelerated list containing S-Selected and P-Pending cases.

**Expected:** S and P rows appear at the beginning of the list, above unactioned cases, with the status shown in the QIND column.
**Fail if:** actioned rows are interleaved with queued rows by priority.

### TC-204 — Priority sequence within remaining rows
*Traces to: Scenario 10 · BE-B, FE-A · Data: TD-1 · Priority: Critical*

1. Open the Mandatory Accelerated list.
2. Read the priority alpha column from top to bottom, ignoring pinned S and P rows.

**Expected:** rows follow the approved prioritization sequence, 99a first, then 99b through 99e.
**Fail if:** ordering is arbitrary, insertion-ordered, or sorted lexically in a way that misplaces any alpha band. This is the defect reported on 08/26/2026 — check it specifically.

### TC-205 — Pagination preserves sequence
*Traces to: Scenario 10 · BE-B · Data: TD-13 · Priority: Critical*

1. Open a list of 40+ cases.
2. Record the last row of page 1 and the first row of page 2.

**Expected:** the sequence continues unbroken across the page boundary.
**Fail if:** page 2 restarts at 99a, or the boundary rows are out of order relative to each other. This indicates sorting is being applied after fetch rather than in the query.

### TC-206 — Sort options match legacy
*Traces to: User Guide F6-SORT · FE-A · Data: TD-1 · Priority: Low*

1. Open the sort control on the Mandatory Accelerated list.

**Expected:** exactly four options — case grade, case type, priority level code, zipcode.
**Fail if:** the option set differs from legacy without a documented decision.

### TC-207 — Sort state does not persist
*Traces to: FE-A · Data: TD-1 · Priority: Medium*

1. Sort the list by zipcode.
2. Navigate away to Group Summary and return to the list.

**Expected:** the list returns to approved priority sequence.
**Fail if:** the zipcode sort persists across navigation.

### TC-208 — Queued and Listed counts are separate
*Traces to: User Guide p.10 · BE-A, FE-A · Data: TD-1 · Priority: High*

1. Note both counts in the header.
2. Assign one Mandatory Accelerated case.
3. Re-read both counts.

**Expected:** queued (unassigned) decreases by one; listed is unchanged.
**Fail if:** both move together, only one number is displayed, or queued fails to decrement.

### TC-209 — QIND displays all defined values
*Traces to: User Guide p.10 · BE-B, FE-A · Data: TD-9 · Priority: Medium · **Partially blocked by OQ-1a***

1. Open a list containing cases in each available status.

**Expected:** QIND renders S-Selected, P-Pending and H-Hold correctly. Confirm with the business owner whether K-Skipped can occur here before treating its absence as a defect.
**Fail if:** any status renders blank, raw, or as the wrong letter.

### TC-210 — Large balance precision
*Traces to: BE-B · Data: TD-11 · Priority: High*

1. Locate the TD-11 case and compare the displayed balance against source.

**Expected:** the value matches to the cent, right-aligned, formatted as currency.
**Fail if:** the value is rounded, truncated, shown in scientific notation, or differs from source in the final digits — this indicates floating-point storage and will break TC-603.

### TC-211 — Both TIN formats
*Traces to: BE-B · Data: TD-12 · Priority: Medium*

1. Compare displayed TINs against source for both an EIN-format and SSN-format case.

**Expected:** both render in the correct format and follow the same masking rules used elsewhere in ENTITY.
**Fail if:** either format is mangled, or masking differs from other ENTITY screens.

### TC-212 — Empty state
*Traces to: FE-A · Data: TD-2 · Priority: Medium*

1. Open the Mandatory Accelerated screen for an RO with no eligible cases.

**Expected:** a clear message stating there is no Mandatory Accelerated inventory and that normal assignment methods are available.
**Fail if:** a blank table renders with no explanation.

### TC-213 — Case aligned to two ROs appears once
*Traces to: BE-B · Data: TD-10 · Priority: High*

1. Open the group-level Mandatory Accelerated list.
2. Search for the TD-10 case.

**Expected:** the case appears once, carrying both aligned ROs. Group queued and listed counts each include it once.
**Fail if:** the case is duplicated, or the header counts are inflated by double-counting.

---

## 6. Section 3 — Enforcement (TC-300)

### TC-301 — Auto Select is blocked
*Traces to: Scenario 1 · BE-C, FE-B · Data: TD-1 · Priority: Critical*

1. Select the TD-1 RO.
2. Attempt to assign a lower-priority case via Auto Select.

**Expected:** the assignment is refused. A message states Priority 99 inventory must be assigned first, gives the queued count, and links to the applicable Mandatory Accelerated screen.
**Fail if:** the assignment completes, or the refusal carries no count and no route onward.

### TC-302 — ZIP Code Select is blocked
*Traces to: Scenario 1, Scenario 6 · BE-C · Data: TD-1 · Priority: Critical*

1. Repeat TC-301 using ZIP Code Select.

**Expected:** identical refusal behaviour.
**Fail if:** ZIP Code Select succeeds where Auto Select was blocked. Inconsistency between selection paths is the specific failure the requirement calls out.

### TC-303 — Every other selection dropdown is blocked
*Traces to: Prevent circumvention · BE-C · Data: TD-1 · Priority: Critical*

1. Enumerate every assignment method available under Case Assignment other than Query and Assign by TIN.
2. Attempt a lower-priority assignment through each in turn.

**Expected:** all are refused with consistent messaging.
**Fail if:** any single path succeeds. Record which one — this indicates enforcement sits at the controller rather than the service layer.

### TC-304 — Restriction persists across multiple cases
*Traces to: Scenario 2 · BE-D · Data: TD-1 · Priority: Critical*

1. Assign one Mandatory Accelerated case, leaving others outstanding.
2. Attempt a lower-priority assignment via Auto Select.

**Expected:** still refused. The queued count in the message reflects the reduced figure.
**Fail if:** the restriction lifts after the first selection rather than the last.

### TC-305 — No restriction where none applies
*Traces to: Scenario 3 · BE-A · Data: TD-2 · Priority: Critical*

1. Assign a lower-priority case to the TD-2 RO via Auto Select.

**Expected:** assignment completes normally.
**Fail if:** any restriction is applied to an RO with no eligible inventory.

### TC-306 — Normal methods unlock after the final selection
*Traces to: Scenario 5 · BE-D, FE-B · Data: TD-3 · Priority: Critical*

1. Assign the RO's single Mandatory Accelerated case.
2. Immediately attempt a lower-priority assignment via Auto Select.

**Expected:** the assignment proceeds. No page reload, re-navigation or re-login is required.
**Fail if:** the restriction persists after the last case is taken, or lifts only after a manual refresh.

### TC-306a — Order of selection within the accelerated set
*Traces to: Enforcement principle · BE-C · Data: TD-1 · Priority: Critical · **Unblocked 01 Sep***

1. From a list spanning 99a–99e, select a 99c case before any 99a case.

**Expected:** selection succeeds. Order within the accelerated set is the manager's choice. The gate is on the set, not the sequence — everything below Priority 99 stays hidden until every 99 is selected.
**Fail if:** the selection is refused, or the user is forced to 99a first. Over-enforcement here blocks legitimate work.
**Note:** display order must still be strict priority alpha, 99a first. That is TC-204 and is unaffected by this case.

### TC-307 — Unlock without session refresh
*Traces to: Scenario 5 · FE-B · Data: TD-3 · Priority: High*

1. Complete TC-306 without navigating away or reloading.

**Expected:** the on-screen counter reaches zero and blocked controls become available in place.
**Fail if:** the counter stalls, or controls remain disabled until reload.

### TC-308 — Hold/Skip Date is blocked
*Traces to: Prevent Manager Queue Control · BE-C · Data: TD-1 · Priority: High*

1. With the restriction active, open Hold/Skip and attempt to set a hold or skip date.

**Expected:** the action is refused with an explanation.
**Fail if:** the date is accepted and persisted.

### TC-309 — Hold/Skip remains readable
*Traces to: BE-C · Data: TD-1 · Priority: Medium*

1. With the restriction active, open the Hold/Skip tab.

**Expected:** existing hold and skip state is visible. Only the write action is blocked.
**Fail if:** the tab is inaccessible or renders empty.

### TC-310 — Hold/Skip re-enables automatically
*Traces to: BE-C, FE-D · Data: TD-3 · Priority: Medium*

1. Satisfy the restriction per TC-306.
2. Return to Hold/Skip.

**Expected:** controls are enabled without reload.
**Fail if:** the block persists after the restriction is satisfied.

---

## 7. Section 4 — Assignment and Lifecycle (TC-400)

### TC-401 — Assign from the RO-scoped screen
*Traces to: BE-D, FE-C · Data: TD-1 · Priority: Critical*

1. Open the RO Mandatory Accelerated screen, select a case, confirm.

**Expected:** the case is assigned to that RO and moves to S-Selected.
**Fail if:** assignment fails, or the case lands in an unexpected status.

### TC-402 — Assign from the group screen
*Traces to: Determine applicability by RO · BE-D, FE-C · Data: TD-7 · Priority: High*

1. From the group list, select a case and assign it to an RO in the group.

**Expected:** the RO picker offers only ROs in the manager's group, displayed as assignment number and name. Assignment succeeds.
**Fail if:** ROs outside the group are offered, or the picker is unpopulated.

### TC-402a — Assignment to a non-aligned RO
*Traces to: Legacy F2 behaviour · BE-D · Data: TD-5, TD-7 · Priority: Critical · **Unblocked 01 Sep***

1. From the group list, pick a case aligned by ZIP to RO-A.
2. Assign it to RO-B instead.

**Expected:** the assignment succeeds. ZIP alignment is a suggestion, not a rule — the manager has complete control of all cases aligned to the group and may assign any case to any person in it.
**Fail if:** the assignment is refused on alignment grounds. This is the primary purpose of the group screen and blocking it defeats the feature.

### TC-402b — Redistribution to an RO with no accelerated inventory
*Traces to: BE-D · Data: TD-2, TD-7 · Priority: High*

1. Identify an RO in the group showing zero Priority 99 cases.
2. From the group list, assign an accelerated case aligned to a different RO to this one.

**Expected:** the assignment succeeds. This is the stated purpose of the screen — balancing where one RO holds 55 accelerated cases and another holds 5.
**Fail if:** refused because the target RO has no accelerated inventory of its own.

### TC-403 — Reason code stamped
*Traces to: Legacy parity · BE-D · Data: TD-1 · Priority: Medium*

1. Assign a Mandatory Accelerated case and inspect the resulting record.

**Expected:** reason for case request reads "Mandatory Accelerated Case".
**Fail if:** the reason is blank, generic, or matches a normal selection.

### TC-404 — Lifecycle progression
*Traces to: Maintain assignment status · BE-D · Data: TD-1 · Priority: High*

1. Assign a case and follow it through the normal GM Case Assignment cycle.

**Expected:** the case shows S-Selected, then P-Pending, and is delivered through the normal assignment process.
**Fail if:** the case bypasses a state, stalls, or fails to deliver.

### TC-405 — Unpick is refused
*Traces to: Scenario 4 · BE-D, FE-D · Data: TD-1 · Priority: Critical*

1. Assign a Mandatory Accelerated case.
2. Attempt to unpick it before it reaches P-Pending.

**Expected:** refused, with an explanation. The case does not return to queue inventory.
**Fail if:** the unpick succeeds. This is the clearest single failure of the requirement.

### TC-406 — Ordinary unpick still works
*Traces to: Scenario 4 · BE-D · Data: TD-2, TD-8 · Priority: High*

1. On an RO with no Mandatory Accelerated inventory, select a case normally and unpick it.

**Expected:** unpick succeeds as it does today.
**Fail if:** the block has been applied indiscriminately to all selections. This is a regression, not a fix.

### TC-407 — Irreversibility is stated before commit
*Traces to: FE-C · Data: TD-1 · Priority: Medium*

1. Begin a Mandatory Accelerated assignment and read the confirmation step.

**Expected:** the confirmation states that the selection cannot be undone.
**Fail if:** the manager learns of irreversibility only when a later unpick is refused.

### TC-408 — Counter decrements live
*Traces to: Update remaining inventory dynamically · BE-D, FE-B · Data: TD-1 · Priority: High*

1. Note the queued count, assign one case, observe.

**Expected:** the count decreases by one without a manual refresh.
**Fail if:** it lags, requires reload, or decrements by the wrong amount.

### TC-409 — Concurrent assignment of the same case
*Traces to: BE-D · Data: TD-15 · Priority: High*

1. Two testers open the same Mandatory Accelerated case simultaneously.
2. Both confirm assignment within a few seconds of each other.

**Expected:** one succeeds. The other receives a specific message that the case is no longer available.
**Fail if:** both succeed, the case is assigned twice, or the loser receives a generic or unhandled error.

### TC-410 — Case review before selection
*Traces to: Support case review · BE-B, FE-C · Data: TD-1 · Priority: Medium*

1. From the list, open a case and review summary, modules, activity, time and name/address.

**Expected:** all five are reachable and match what is shown elsewhere in GM Case Assignment.
**Fail if:** any is missing or shows different data from the equivalent screen.

### TC-411 — List position preserved
*Traces to: FE-C · Data: TD-13 · Priority: Low*

1. Scroll to page 2, open a case, return to the list.

**Expected:** the list reopens at page 2 at the previous scroll position.
**Fail if:** it resets to page 1.

---

## 8. Section 5 — Permitted Exceptions (TC-500)

### TC-501 — Query permits any priority
*Traces to: Preserve approved exceptions · BE-C · Data: TD-1 · Priority: Critical*

1. With the restriction active, use Case Assignment → Query to select a lower-priority case.

**Expected:** the selection succeeds. Query does not force Priority 99 selection and allows any priority level.
**Fail if:** Query is blocked. Over-enforcement here breaks a sanctioned managerial process.

### TC-502 — Assign by TIN permits any priority
*Traces to: Preserve approved exceptions · BE-C · Data: TD-1 · Priority: Critical*

1. Repeat TC-501 using Assign by TIN.

**Expected:** succeeds, same as Query.
**Fail if:** blocked.

### TC-503 — Exception use does not clear the restriction
*Traces to: Scenario 7 · BE-C · Data: TD-1 · Priority: Critical*

1. Use Query to assign a lower-priority case per TC-501.
2. Return to Auto Select and attempt another lower-priority assignment.

**Expected:** Auto Select is still refused. The restriction remains in force for all other inventory.
**Fail if:** using an exception has switched the restriction off. This turns Query into a universal bypass and is the highest-value defect in this section.

### TC-504 — Exception use does not decrement the queued count
*Traces to: Scenario 7 · BE-D · Data: TD-1 · Priority: High*

1. Note the queued count, assign a non-P99 case via Query, re-read the count.

**Expected:** unchanged.
**Fail if:** the count drops. The restriction would then clear early.

### TC-505 — Sanctioned routes remain visible
*Traces to: FE-B · Data: TD-1 · Priority: Medium*

1. With the restriction active, review the Case Assignment tab strip.

**Expected:** Query and Assign by TIN are clearly available and not styled as blocked.
**Fail if:** every tab appears disabled, leaving the manager with no visible way forward.

### TC-506 — Query sub-tabs are restricted
*Traces to: Confirmed 01 Sep · BE-C, FE-B · Data: TD-1 · Priority: High*

1. With the restriction active, open Case Assignment → Query.

**Expected:** the view defaults to Priority 99 results. Only **Priority 99** and **Create Query** are enabled. Million Dollar Cases, HINF, Egregious 941, High Priority Cases, National, Local, My Saved Queries, Query Results and National Queue are all greyed out.
**Fail if:** any restricted sub-tab is reachable, or Create Query is disabled — Create Query is one of the two sanctioned workarounds and must stay live.

### TC-507 — Reports restriction
*Traces to: Confirmed 01 Sep · BE-C · Data: TD-1 · Priority: High · **Partially blocked***

1. Confirm with the business what modern Reports currently supports for case selection.
2. With the restriction active, run a report and attempt to select a case below Priority 99 from the results.

**Expected:** only Priority 99 cases may be picked while eligible inventory exists.
**Fail if:** a lower-priority case can be selected from a report. Report results are a plausible bypass route and should be tested even if the restriction is descoped.

### TC-508 — Pending tab is unrestricted
*Traces to: Confirmed 01 Sep · Data: TD-1 · Priority: Low*

1. With the restriction active, open the Pending tab.

**Expected:** fully viewable. Pending is a list only and carries no restriction.
**Fail if:** it is greyed out or blocked.

### TC-509 — Query sub-tabs restore when the restriction lifts
*Traces to: FE-B · Data: TD-3 · Priority: Medium*

1. Satisfy the restriction per TC-306.
2. Return to Query.

**Expected:** all sub-tabs are enabled again without reload.
**Fail if:** any remains greyed out.

---

## 9. Section 6 — Counts and Reconciliation (TC-600)

### TC-601 — Group Summary reconciles to RO lists
*Traces to: Support P99 visibility · BE-B · Data: TD-7 · Priority: High*

1. Record the Priority 99 value for each RO on Group Summary.
2. Open each RO's Mandatory Accelerated list and count the rows.

**Expected:** the figures match for every RO.
**Fail if:** any RO differs. Record which, and by how much.

### TC-602 — RO lists reconcile to the group list
*Traces to: BE-B · Data: TD-7, TD-10 · Priority: High*

1. Sum the distinct cases across all RO-scoped lists.
2. Compare against the group-level list.

**Expected:** identical sets. A case aligned to two ROs is counted once at group level.
**Fail if:** the group total is the arithmetic sum including duplicates.

### TC-603 — Counts reconcile to source data
*Traces to: Scenario 9 · BE-E · Data: TD-7 · Priority: Critical*

1. Run the reconciliation harness against production-equivalent data.

**Expected:** presented counts and case sets match the underlying Priority 99 business rules and source data, with no unexplained variance.
**Fail if:** any discrepancy is reported, or the harness absorbs differences silently rather than surfacing them.

### TC-604 — Group Summary counts after selection
*Traces to: BE-B, FE-D · Data: TD-7 · Priority: Critical*

**This case was previously written backwards. The behaviour below is confirmed from the 01 Sep walkthrough and is counter-intuitive — read it before executing.**

1. Note an RO's Priority 99 and Pending values on Group Summary. Assume 46 and 0.
2. Assign two Mandatory Accelerated cases for that RO.
3. Return to Group Summary.

**Expected:** Priority 99 still reads **46**. Pending reads **2**. The Priority 99 figure does not fall on selection — it holds until the cases are actually delivered, because an emergency back-door exists to unselect a case before it reaches Pending and the count reflects what is still recoverable.
**Fail if:** Priority 99 reads 44. That is the intuitive behaviour and the wrong one. Raise it as a defect rather than accepting it.

### TC-605 — Queued and Group Summary counts move independently
*Traces to: BE-A, BE-B · Data: TD-1 · Priority: High*

1. Record four numbers before assigning: queued and listed on the Mandatory Accelerated screen, Priority 99 and Pending on Group Summary.
2. Assign one case.
3. Record all four again.

**Expected:** queued decreases by one, listed unchanged, Priority 99 unchanged, Pending increases by one.
**Fail if:** any of the four moves in the wrong direction. Two counts fall and two hold, and conflating them is the most likely counting defect in this build.

---

## 10. Section 7 — Bypass and Security (TC-700)

**Treat every failure in this section as Critical.** These are the cases that determine whether the control actually holds.

### TC-701 — Direct service call
*Traces to: BE-C · Data: TD-1 · Priority: Critical*

1. Capture the request the UI issues for an Auto Select assignment.
2. With the restriction active, replay it directly against the service, bypassing the interface.

**Expected:** refused at the service layer with the structured Mandatory Accelerated error.
**Fail if:** the assignment succeeds. This means enforcement lives only in the UI, and the requirement is not met regardless of how the screens behave.

### TC-702 — Direct navigation to a blocked screen
*Traces to: Prevent circumvention · FE-B, BE-C · Data: TD-1 · Priority: Critical*

1. Note the URL of the Auto Select screen for an unrestricted RO.
2. Edit the URL to target the restricted RO and navigate to it directly.

**Expected:** the gate applies. The manager is routed to the Mandatory Accelerated screen.
**Fail if:** the Auto Select screen renders and permits selection.

### TC-703 — Browser back after a block
*Traces to: FE-B · Data: TD-1 · Priority: High*

1. Trigger a block per TC-301.
2. Use the browser back button and retry the assignment.

**Expected:** still refused.
**Fail if:** cached client state allows the second attempt through.

### TC-704 — National Analyst impersonation
*Traces to: BE-C · Data: TD-14 · Priority: Critical*

1. Sign in as a National Analyst.
2. Set "Viewing as: Group NNNNNN" for a group with restricted ROs.
3. Attempt a lower-priority assignment via Auto Select.

**Expected:** the restriction applies to the impersonated group, exactly as it would for that group's own manager.
**Fail if:** the assignment succeeds. This is the most likely enforcement gap in the build — the interceptor reading the actor's own group rather than the impersonated one. Test it deliberately and early.

### TC-705 — Sorting does not bypass
*Traces to: Scenario 6 · BE-C, FE-A · Data: TD-1 · Priority: Critical*

1. On a restricted RO, re-sort inventory lists by every available option and attempt assignment after each.

**Expected:** refused every time.
**Fail if:** any sort order permits a lower-priority selection.

### TC-706 — Search does not bypass
*Traces to: Scenario 6 · BE-C · Data: TD-1 · Priority: Critical*

1. Use any search or filter available in Case Assignment to locate a lower-priority case, then attempt assignment.

**Expected:** refused.
**Fail if:** reaching the case by a different route permits assignment.

### TC-707 — Session refresh does not clear the restriction
*Traces to: BE-A · Data: TD-1 · Priority: High*

1. Trigger a block, log out, log back in, and retry.

**Expected:** still refused.
**Fail if:** the restriction resets with the session, indicating it is held in session state rather than derived from inventory.

### TC-708 — Blocked attempts are audited
*Traces to: BE-E · Data: TD-1 · Priority: High*

1. Trigger several blocks across different methods.
2. Inspect the audit records.

**Expected:** every rejection is recorded with actor, RO, selection method, timestamp, and the queued count at the time.
**Fail if:** only successful assignments are logged. For a control function, the rejections are the evidence.

### TC-709 — Audit captures the impersonation chain
*Traces to: BE-E · Data: TD-14 · Priority: High*

1. Perform TC-704, then inspect the audit record.

**Expected:** the record names both the actor and the group being acted for.
**Fail if:** it records only the actor, losing the fact that they were operating outside their own group.

---

## 11. Section 8 — Regression (TC-800)

These confirm the change has not broken what already worked.

### TC-801 — International Case Assignment unchanged
*Data: TD-4 · Priority: Critical*
Full International assignment cycle behaves exactly as before the release.
**Fail if:** any behavioural difference is observed.

### TC-802 — Unrestricted ROs unaffected
*Data: TD-2 · Priority: Critical*
Auto Select, ZIP Code Select and all normal methods work as before for ROs with no eligible inventory.
**Fail if:** any method is slower, blocked, or altered.

### TC-803 — Query functionality unchanged
*Data: TD-2 · Priority: High*
Existing query building, saved queries, sorting and results behave as before.
**Fail if:** any existing query capability is lost.

### TC-804 — Assign by TIN unchanged
*Data: TD-2 · Priority: High*
Custom assignment behaves as before.

### TC-805 — Inventory Adjustment unchanged
*Data: TD-7 · Priority: High*
Target level ranges, adjustment percentage, reason and submit all behave as before.
**Fail if:** Mandatory Accelerated interferes with inventory adjustment for an unrestricted RO.

### TC-806 — Group Summary columns unchanged
*Data: TD-7 · Priority: Medium*
High Priority Queue, Open, Pending, Top, Bottom, 85%, Inventory Adjustment and Warning all show what they showed before.
**Fail if:** any existing column is altered by the Priority 99 change.

---

## 12. Traceability

| Requirement scenario | Test cases |
|---|---|
| 1 — P99 available | TC-301, TC-302, TC-303 |
| 2 — Multiple P99 cases | TC-304 |
| 3 — No applicable P99 | TC-102, TC-305 |
| 4 — P99 selected | TC-404, TC-405, TC-406, TC-407 |
| 5 — Restriction completed | TC-306, TC-307, TC-310 |
| 6 — Alternate method attempted | TC-302, TC-705, TC-706 |
| 7 — Exception used | TC-503, TC-504 |
| 8 — International | TC-105, TC-801 |
| 9 — Counts and data | TC-601, TC-602, TC-603, TC-604 |
| 10 — Priority sequencing | TC-204, TC-205 |

| Story | Test cases |
|---|---|
| BE-A eligibility service | TC-101–TC-106, TC-208, TC-305, TC-707 |
| BE-B read endpoints | TC-201–TC-213, TC-410, TC-601–TC-604 |
| BE-C enforcement interceptor | TC-301–TC-310, TC-501–TC-503, TC-701–TC-707 |
| BE-D assignment write path | TC-401–TC-409, TC-504 |
| BE-E audit and reconciliation | TC-603, TC-708, TC-709 |
| FE-A list component | TC-201–TC-213, TC-705 |
| FE-B gating and routing | TC-301, TC-303, TC-307, TC-408, TC-505, TC-702, TC-703 |
| FE-C review and assign | TC-402, TC-407, TC-410, TC-411 |
| FE-D disabled states | TC-310, TC-405, TC-601 |

---

## 13. Exit Criteria

Before this epic is accepted:

- All Critical cases pass, with no open Critical defects.
- Every case in Section 7 passes. A Section 7 failure blocks release regardless of the rest.
- TC-603 reconciliation runs clean against production-equivalent data.
- All Blocked cases have been unblocked by written answers to their open questions and then executed.
- Section 8 regression passes in full.
- Formal 508 accessibility validation is complete — booked separately, not covered by this document.
