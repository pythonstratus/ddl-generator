I'll check current details on Claude's federal availability so the compliance claims in your justification are accurate.Here are two drafts you can paste in, plus supporting notes. I verified the current compliance and procurement facts so nothing in your package gets flagged as inaccurate.

---

## Business Justification (paste-ready)

> **Requested capability:** Claude for Government (including Claude Code) for the [SYSTEM NAME] sustainment and modernization effort.
>
> **Problem statement.** [SYSTEM NAME] has been in continuous operation for approximately 40 years. Its codebase — roughly [X] lines of [COBOL/FORTRAN/MUMPS/PL-I/Assembler/etc.] across [Y] modules — carries decades of accumulated business rules that exist only in the source code. Original developers have retired, formal design documentation is either missing or no longer reflects the deployed system, and institutional knowledge of the system now rests with [N] staff. This creates three concrete risks: (1) inability to safely modify the system in response to statutory or policy change; (2) mission continuity exposure if remaining subject-matter experts depart; and (3) inability to scope or price a modernization effort, because the current-state requirements are unknown.
>
> **Proposed use.** Claude Code is an AI coding assistant that operates directly against a source repository. Staff developers will use it to:
> - **Comprehend** legacy modules — trace call chains, data flows, copybook/record layouts, and control logic across files that exceed what a human can hold in working memory.
> - **Document** current-state behavior — generate module-level and system-level technical documentation, data dictionaries, interface catalogs, and extracted business-rule inventories, which staff then review and validate.
> - **De-risk modernization** — produce candidate decomposition strategies, identify dead code and duplicated logic, map legacy constructs to modern equivalents, and draft characterization tests that establish a behavioral baseline before any refactoring occurs.
>
> **Human-in-the-loop control.** Claude is used as an analysis and drafting aid. It does not commit code, deploy, or access production systems. All output is reviewed and approved by [AGENCY] developers under existing SDLC, change-control, and code-review procedures. No system change is made on AI output alone.
>
> **Security and compliance posture.** Claude for Government operates inside a FedRAMP High–authorized boundary (authorization held through Palantir Federal Cloud Service – Supporting Services, independently assessed by Schellman Compliance, LLC), logically isolated from commercial tenants. It is authorized for CUI and FIPS 199 High-impact data. Agency conversations and uploaded content are not used to train Anthropic's models by default. Encryption is applied in transit and at rest. SSO, SCIM provisioning, admin controls, and audit logging are available for accountability and oversight. The full ATO package is available for our ISSO's review through Anthropic's Trust Center.
>
> **Alignment.** This request supports [AGENCY]'s IT modernization objectives under [PLAN/STRATEGY NAME], addresses GAO and IG findings regarding aging legacy systems and technical-debt risk, and is consistent with current OMB guidance directing agencies to adopt AI to improve mission delivery while managing risk.
>
> **Procurement.** Claude for Government is available directly from Anthropic or through Carahsoft on a GSA vehicle. A limited-time federal program provides Claude for Government at $1 per agency for unlimited seats through August 2026, making this a near-zero-cost evaluation.

---

## Brief ROI Statement (paste-ready)

> Reverse-engineering undocumented legacy code is the single largest cost driver in modernization, and it is currently performed manually at contractor rates of approximately $[RATE]/hour. Our team estimates [X] hours per module to document current-state behavior; across [Y] modules, that is roughly $[TOTAL] in analysis labor before a single line is modernized.
>
> In comparable code-comprehension and documentation tasks, AI-assisted analysis reduces this effort substantially — our conservative planning assumption is a [40–60]% reduction in analyst hours, yielding an estimated $[SAVINGS] in avoided labor on the documentation phase alone. Against a license cost of $1 per agency through August 2026, the effort pays for itself in the first module.
>
> Beyond direct labor, the return comes from risk reduction. Legacy modernization programs most often fail or overrun because current-state requirements were never fully known at the time of scoping. Producing a validated business-rule inventory and behavioral test baseline before committing to a modernization contract materially improves our ability to write an accurate SOW, price the work, and avoid the rework and schedule slip that drive cost growth on programs of this type. A single avoided re-scope on a [$SIZE] modernization effort exceeds the entire cost of this capability by orders of magnitude.
>
> Secondary returns include reduced onboarding time for new developers on an unfamiliar codebase, and capture of retiring-workforce knowledge into durable written documentation before it is lost.

---

## Notes on filling these in

**Numbers to gather before you submit.** Reviewers will push on the ROI figures, so use your own: lines of code, module count, your current contractor blended rate (or GS-equivalent fully-loaded rate), and how many hours your team has historically spent documenting one module. If you have no baseline, say so and propose measuring it during the pilot — that's a stronger answer than a made-up percentage.

**Make the savings claim conservative.** A 40% figure you can defend beats an 80% figure that invites scrutiny. Frame it as a planning assumption to be validated, not a guarantee.

**Verify two things yourself.** Confirm the exact OMB memo your agency's AI governance office currently cites, and pull the specific GAO or IG report number that covers your system or system class. Both make the justification much harder to dismiss, but you want the current citation, not mine.

**Have ready for follow-up questions.** Your CIO/CISO will likely ask: whether source code leaves the boundary (it stays within the FedRAMP High environment), whether output is auto-committed (no — it goes through your existing code review), whether the data is retained or trained on (not used for training by default; retention is configurable), and who your AI use-case inventory POC is if your agency maintains one.

**On Claude Code specifically.** Claude Code and Claude Cowork became available through Claude for Government Desktop inside the FedRAMP High environment in mid-2026. If your reviewer knows only the commercial version of Claude, calling out that the agentic coding tool is available inside the authorized boundary — not just the chat interface — is worth stating explicitly.
