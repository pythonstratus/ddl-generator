```
You are a senior delivery manager and Jira planning expert. I'm attaching a Jira export (CSV/Excel) of our backlog. Complete the two asks below using only the data in the file.

ASK 1 – DUPLICATE DETECTION
- Compare tickets by their Summary and Description (not just exact text; also catch near-duplicates that describe the same issue in different words, e.g., same bug, same steps to reproduce, same affected screen or letter).
- Output a table: Duplicate Group #, Issue Key, Summary, Status, Priority, Recommended "Keep" ticket, Recommended "Close as Duplicate" tickets, Reason/confidence (High/Medium/Low).
- Exclude duplicates from the Ask 2 plan, but list them separately.

ASK 2 – SCHEDULE AND ESTIMATES
For every remaining open (not Done/Closed) ticket, add:
1) Target Start Date in DD/MM/YYYY
2) Target End Date in DD/MM/YYYY
3) Original Estimate in hours

Team composition:
- 2 Senior Developers (one uses Claude Code with Sonnet 4.6; assume a reasonable productivity gain for that developer and state the % you used)
- 1 Mid-level Full Stack Developer
- 3 QA Testers

Planning rules and evaluation criteria:
1. Capacity: 40 hours/week per FTE (Mon–Fri, 8 hrs/day). Do not overallocate anyone.
2. Priority: schedule Highest/High priority tickets first.
3. Dependencies and risks: respect ticket links, Epic relationships, and logical order (e.g., backend before frontend, dev before QA). Flag risks and blockers.
4. Remaining work starts on 5 October 2026 (05/10/2026) and must finish by 15 December 2026 (15/12/2026). If it doesn't fit, say so and show what slips.
5. Base estimates on the ticket description (complexity, scope, steps to reproduce). Include QA time for each ticket.

OUTPUT
- Table: Issue Key, Summary, Issue Type, Epic, Priority, Assigned Role (Senior Dev 1 / Senior Dev 2 (Claude Code) / Mid Dev / QA 1–3), Original Estimate (hrs), Dev Target Start, Dev Target End, QA Target Start, QA Target End, Dependencies, Risk Notes.
- Capacity summary: total hours per person vs. available hours in the window.
- List of assumptions made.
- Provide the final result as a downloadable Excel file, keeping the original columns and adding the new ones.
```

Tip: attach the Jira CSV together with this prompt so the model can read the actual tickets.
