# Case Assignment — Adjacent Topics
## Items raised alongside Mandatory Accelerated that are separate work

**Source:** Case Assignment Modernization walkthrough with Sarah, 01 Sep 2026, plus the Mandatory Accelerated requirements document.

These surfaced during the Mandatory Accelerated discussion but are not part of that epic. Several are pre-existing defects in modern case assignment. Topic 1 is the most serious thing in this document and is arguably more urgent than Mandatory Accelerated itself, because it affects every screen rather than one function.

---

## 1. Priority Alpha Ordering — systemic defect

### What was said

Sarah, looking at a live modern case assignment screen during the call:

> "Do you see how these are not in priority alpha order? There's a 103B mixed in. There's 99E higher than a 99A everywhere in case assignment. Literally every screen, no matter where you're looking."

And on the rule itself:

> "The master rule is priority alpha order. It doesn't matter what you're looking at... Priority alpha is king everywhere in case assignment. There should never be a screen or a situation, even in query results, where this is not the rule."

### Why this is severe

This is not a display preference. Priority alpha order is how the business decides which case a revenue officer works next. A screen that presents cases out of order causes the wrong case to be worked, and it does so silently — nothing about the screen signals that the order is wrong.

The scope is every screen in case assignment, not just Priority 99. Sarah observed a 103B misplaced and a 99E ranked above a 99A on the same view.

### The ordering rule in full

Two levels, both mandatory:

**Level 1 — priority band and alpha.** 99a, then 99b, then 99c, 99d, 99e, then 101 and below. Strictly.

**Level 2 — within a single alpha value.** This is the part most likely to be missed. Sarah:

> "If you look, do you see right here I have 99B, B, B... They are not all the same. The top 99B is considered to be more productive than the one under it. So there's model scores underneath that are driving the alpha part. So the exact order of display is essential."

Three cases all showing 99b are **not** interchangeable. They carry an underlying model score that establishes a strict ranking, and that ranking must be preserved in display. Sorting by anything else destroys it.

### The model score

> "It is based on model scores. It's a calculation of model score against the balance. And then we rank it in a specific, very specific order."

An entire table in legacy performs this calculation. Samuel and Diane have been working on this area for the past two months and are the best available resource. Eric suggested asking Samuel how portable the legacy calculation is to the modern environment.

**Open:** Santosh was to confirm whether modern reproduces the model score calculation at all, or only carries the alpha letter without the underlying rank. If only the letter was carried across, the within-alpha ordering cannot be reproduced without porting the calculation, and that is a materially larger piece of work than a sort fix.

### Likely root cause

Santosh, on the call:

> "Since we are loading all the data, it kind of, you know, decides on the... what the sorting needs to work."

That describes sorting applied client-side after the full result set is loaded. If so, it is also why ordering breaks across pagination — each page sorts independently of the others. The fix is to push ordering into the query and treat it as part of the index strategy, not to adjust the client-side comparator.

Santosh noted a ticket already exists and described the mechanism as multi-column sorting needing fine-tuning. **That framing is probably too small.** Multi-column sorting that ranks correctly within an alpha value requires the model score to be available as a sortable field, which is a data question before it is a UI question.

### Recommended actions

1. **Spike (4h):** determine whether modern holds the model score or only the alpha letter. This answer determines everything downstream.
2. **Confirm** whether sorting is applied client-side or in the query.
3. **Audit** every case assignment surface for ordering compliance — group summary, RO selection, all query result views, reports, pending, hold/skip. Sarah's claim is that the defect appears everywhere; verify the extent before sizing.
4. **Regression test** asserting order against a fixture with multiple cases sharing an alpha value. A fixture with one case per alpha will pass while the real defect remains.

**Estimate:** cannot be given responsibly until step 1 returns. If the model score is present and it is purely an ordering fix, 16–24h. If the calculation must be ported from legacy, materially more.

---

## 2. Sort options must be constrained

### What was said

> "There's like 30 sorts in Case View, while here we've got like eight. Because these are the only ones that don't break priority alpha order. Any sort, any condition that breaks this is not allowed."

And explicitly:

> "We don't sort by balance, we don't sort by assignment number, nothing else."

### The requirement

Case assignment must expose only sorts that are compatible with priority alpha order. A user-facing sort that reorders across priority bands is not a feature to be offered and disabled later — it must not exist in case assignment at all.

The user guide documents the legacy accelerated screen sort set as four options: case grade, case type, priority level code, zipcode. Sarah refers to roughly eight across case assignment generally. The exact permitted set should be taken from legacy rather than inferred.

This constraint also explains a line in the Mandatory Accelerated requirements document about certain query strings not being allowed in case assignment — the reason is the same, those constructions break priority alpha order.

### Recommended actions

1. Enumerate the permitted sort set from legacy. Roughly eight, exact list to be confirmed.
2. Compare against what modern currently offers.
3. Remove any sort in modern that can break priority alpha order, including balance and assignment number if present.
4. Apply the same constraint to query builder output settings — the Create Query screen exposes First Sort, Second Sort and Third Sort dropdowns, and these need the same restriction.

**Estimate:** 8–12h once the permitted list is confirmed, assuming removal rather than reimplementation.

---

## 3. National Queue restructure

Carried from the Mandatory Accelerated requirements document. Not discussed on the 01 September call.

- Remove the National Queue tab from RO case selection. The record display maximum and the need for CSV export make it unusable for actual case selection.
- Retain it under Case Assignment → Query only.
- When used there, query against the entire national queue rather than a capped set.
- CSV export is not permitted on this tab. Case selection has to stay in the application.

**Open:** what is the current record display maximum, and what row count should a full national query expect? Removing a cap on a national-scope query is a performance risk and needs sizing against the earlier DB performance work.

**Estimate:** 22–29h across backend and frontend, contingent on the volume answer.

---

## 4. Emergency unselect capability

### What was said

> "In the past we have had an emergency where we needed to manually go in and unselect a case for a manager... That is not something they can do. That is not a function. We don't want to give them that function. But in an emergency, we need to be able to unselect it for them somehow. The developers used to do that for us when this used to be activated in the past."

### Why it belongs on this list

It is operational rather than functional, and it is the reason for a behaviour that otherwise looks like a bug: the Group Summary Priority 99 count holds at its original value when cases are selected rather than decrementing, because a selected case is still recoverable until it reaches Pending.

### The question

Does modern need a supported administrative path, or does a controlled data fix remain acceptable as it was in legacy? Managers must never have this capability either way — Sarah was firm on that.

If a supported path is wanted, it needs its own story covering authorisation, audit and the effect on counts. Do not let it become an undocumented database procedure by default.

---

## 5. Legacy code reuse — investigation

### What was said

Sarah:

> "It's recorded in some of our very early reports requirements calls where we asked for the code to be used for development of this because this was a function we might need in the future. If you use legacy code when developing this then it should be somewhere in your guts, right? Or does it just completely omit it?"

### Why it matters

If Mandatory Accelerated logic was carried into the modern codebase during initial development and simply left dormant, the eligibility engine is a reactivation rather than a build. That is the difference between roughly 32–42 hours and a fraction of it.

The same question applies to the model score calculation in topic 1.

**Action:** ask Samuel and Diane. This is a short conversation with a potentially large payoff, and it should happen before BE-A starts.

---

## 6. Architecture note — the missing gateway layer

Not a defect, but the structural finding that explains the Mandatory Accelerated estimate.

Islam, on the call:

> "Right now it's technically there's no gateway between group summary, queries, reports etc. All of those, they have dependencies and they work on how the logic that was dealt in mod. So one of the things that we need to do is have that layer of kind of gateway between what we're seeing and what we want to see."

Modern case assignment has no central point at which a restriction can be applied across screens. Each surface fetches and renders independently. Mandatory Accelerated is the first requirement to need cross-screen enforcement, which is why it reads as disproportionately large for what it does functionally.

Worth recognising that this layer, once built, is reusable. Any future requirement that gates what a manager may see or select across multiple screens will land on it. That argues for building it properly rather than as a set of per-screen conditionals, even under time pressure.

---

## 7. Summary of recommended sequencing

| Priority | Item | Blocking? |
|---|---|---|
| 1 | Model score spike (topic 1, step 1) | Blocks any credible ordering estimate |
| 2 | Legacy code reuse enquiry (topic 5) | Could materially reduce the Mandatory Accelerated build |
| 3 | Priority alpha ordering fix (topic 1) | Systemic correctness defect, affects every screen |
| 4 | Sort constraint (topic 2) | Related to 3, cheaper, do together |
| 5 | Emergency unselect decision (topic 4) | Needs a business answer, not development time |
| 6 | National Queue (topic 3) | Independent, can follow |

Items 1 and 2 are enquiries, not development. Both should happen on 02 September while Sarah is still available, alongside the outstanding Mandatory Accelerated questions.
