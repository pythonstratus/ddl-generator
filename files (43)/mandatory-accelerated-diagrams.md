# Mandatory Accelerated Case Assignment — Diagrams

Mermaid source. Renders in Confluence, JIRA (with the Mermaid app), GitHub, GitLab, and mermaid.live.

**Part 1** is written for Group Managers and other end users — plain language, no system terms.
**Part 2** is for developers and testers.

---

# PART 1 — For End Users

## 1.1 What happens when you assign cases

```mermaid
flowchart TD
    Start(["You open Case Assignment"]) --> GS["Group Summary<br/>All your revenue officers listed as usual"]
    GS --> Choice{"How do you want to work?"}

    Choice -->|"Pick one revenue officer"| RO{"Does this officer have<br/>Priority 99 cases waiting?"}
    Choice -->|"Group Mandatory Accelerated button"| GRP["See every Priority 99 case<br/>across your whole group"]

    RO -->|"No, the count is zero"| NORMAL["Normal case assignment<br/>Auto Select, ZIP Code Select,<br/>everything works as usual"]
    RO -->|"Yes"| MA["Mandatory Accelerated screen<br/>Only Priority 99 cases are shown"]

    MA --> PICK["Select cases<br/>in whatever order you prefer"]
    PICK --> DONE{"Have all Priority 99<br/>cases been selected?"}
    DONE -->|"Not yet"| MA
    DONE -->|"Yes"| NORMAL

    GRP --> REDIST["Assign any case<br/>to any officer in your group"]
    REDIST --> DONE

    NORMAL --> End(["Carry on as normal"])

    classDef blocked fill:#fde8e8,stroke:#c62828,color:#000
    classDef open fill:#e8f5e9,stroke:#2e7d32,color:#000
    class MA,PICK blocked
    class NORMAL,End open
```

**The rule in one sentence:** if an officer has Priority 99 cases, you must select all of them before you can assign anything else to that officer.

**Three things worth knowing:**

- You choose the order. There is no requirement to take 99a before 99b.
- Once you select a Priority 99 case, you cannot undo it. Ordinary selections can still be undone before they reach Pending.
- An officer with no Priority 99 cases is completely unaffected. Nothing changes for them.

## 1.2 What you can and cannot do while Priority 99 cases are waiting

```mermaid
flowchart LR
    R{"An officer has Priority 99<br/>cases still waiting"}

    R --> B["Not available<br/>until they are all selected"]
    R --> A["Still available"]

    B --> B1["Auto Select"]
    B --> B2["ZIP Code Select"]
    B --> B3["Hold and Skip dates"]
    B --> B4["Million Dollar Cases"]
    B --> B5["HINF, Egregious 941,<br/>High Priority Cases"]
    B --> B6["National, Local,<br/>My Saved Queries"]

    A --> A1["Priority 99 query"]
    A --> A2["Create Query"]
    A --> A3["Assign by TIN"]
    A --> A4["Pending — viewing only"]
    A --> A5["Group Mandatory Accelerated"]

    classDef blocked fill:#fde8e8,stroke:#c62828,color:#000
    classDef open fill:#e8f5e9,stroke:#2e7d32,color:#000
    class B,B1,B2,B3,B4,B5,B6 blocked
    class A,A1,A2,A3,A4,A5 open
```

**If you have an emergency,** Create Query and Assign by TIN both still work and let you pick any priority. Using them does not switch the restriction off for anything else.

## 1.3 Balancing work across your group

```mermaid
flowchart LR
    subgraph G["Your group, before"]
        RO1["Officer A<br/>55 Priority 99 cases"]
        RO2["Officer B<br/>5 Priority 99 cases"]
    end

    BTN{{"Group Mandatory Accelerated<br/>shows every case in the group"}}

    subgraph G2["After redistributing"]
        RO3["Officer A<br/>fewer cases"]
        RO4["Officer B<br/>more cases"]
    end

    G --> BTN --> G2
```

Cases are lined up against officers by ZIP code, but that is a suggestion rather than a rule. From the group screen you can assign any case to any officer in your group, including one who has no Priority 99 cases of their own.

---

# PART 2 — For Developers and Testers

## 2.1 Enforcement decision flow

The logic the interceptor implements. Every assignment request passes through this.

```mermaid
flowchart TD
    REQ(["Assignment request"]) --> IMP["Resolve effective group<br/>from impersonation context<br/>NOT the actor's own group"]
    IMP --> INTL{"Program type is<br/>International?"}

    INTL -->|"Yes"| A1(["ALLOW<br/>Mandatory Accelerated does not apply"])
    INTL -->|"No"| METHOD{"Declared selection method"}

    METHOD -->|"QUERY or ASSIGN_BY_TIN"| A2(["ALLOW<br/>Sanctioned workaround<br/>Restriction stays active<br/>queuedCount unchanged"])
    METHOD -->|"AUTO_SELECT, ZIP_CODE_SELECT,<br/>or any other method"| ELIG{"Eligible Priority 99 cases<br/>still queued for this RO?"}

    ELIG -->|"No, queuedCount = 0"| A3(["ALLOW<br/>Normal assignment"])
    ELIG -->|"Yes"| TARGET{"Is the requested case<br/>one of those Priority 99 cases?"}

    TARGET -->|"Yes"| A4(["ALLOW<br/>Proceed to write path"])
    TARGET -->|"No"| REJ(["REJECT — HTTP 409<br/>MANDATORY_ACCELERATED_ACTIVE<br/>Return queuedCount and redirect"])

    classDef allow fill:#e8f5e9,stroke:#2e7d32,color:#000
    classDef reject fill:#fde8e8,stroke:#c62828,color:#000
    class A1,A2,A3,A4 allow
    class REJ reject
```

**Notes for implementation**

- The impersonation step is first for a reason. A National Analyst operating as "Viewing as: Group NNNNNN" must inherit that group's restrictions. Reading the actor's own group is the most likely bypass in the build.
- Selection method is a declared request parameter, not inferred from the endpoint. Inference means a new endpoint silently defaults to permitted.
- There is no branch testing whether the case is 99a versus 99b. Order within the accelerated set is unrestricted.
- This check runs in the service layer, so every entry point inherits it.

## 2.2 Case state lifecycle

```mermaid
stateDiagram-v2
    [*] --> Queued: case enters group queue
    Queued --> Selected: manager selects (S)
    Selected --> Pending: moves to P
    Pending --> Delivered: delivered to RO
    Delivered --> [*]

    Selected --> Queued: unpick — ORDINARY ONLY

    note right of Selected
        Mandatory Accelerated selections
        CANNOT be unpicked.
        Ordinary S-Selected cases can be,
        before reaching P-Pending.
    end note

    note left of Queued
        Queue membership derives from the
        assignment number. Last four digits
        7000 denote the queue.
    end note

    note right of Pending
        Group Summary Priority 99 count
        holds until DELIVERY, not selection.
        An emergency back-door can unselect
        before Pending is reached.
    end note
```

## 2.3 How the four counts move

The likeliest source of defects. Two fall, two hold.

```mermaid
flowchart LR
    Q1["QUEUED<br/>Mandatory Accelerated header<br/>46"] -->|"manager selects 2"| Q2["44<br/>DECREASES"]
    L1["LISTED<br/>Mandatory Accelerated header<br/>46"] -->|"manager selects 2"| L2["46<br/>unchanged"]
    P1["PRIORITY 99<br/>Group Summary column<br/>46"] -->|"manager selects 2"| P2["46<br/>unchanged"]
    N1["PENDING<br/>Group Summary column<br/>0"] -->|"manager selects 2"| N2["2<br/>INCREASES"]

    classDef moves fill:#fff4e5,stroke:#e65100,stroke-width:3px,color:#000
    classDef holds fill:#eceff1,stroke:#546e7a,stroke-width:2px,color:#000
    classDef start fill:#e3f2fd,stroke:#1565c0,color:#000
    class Q2,N2 moves
    class L2,P2 holds
    class Q1,L1,P1,N1 start
```

The restriction lifts when **Queued** reaches zero. Binding release logic to Listed produces a counter that never moves and an unlock that never fires. Binding the Group Summary column to queue-available inventory produces 44 where the business expects 46.

## 2.4 Assignment sequence

```mermaid
sequenceDiagram
    autonumber
    participant UI as Manager UI
    participant API as Case Assignment API
    participant GATE as Enforcement Interceptor
    participant ELIG as Eligibility Service
    participant DB as Database

    UI->>API: GET /mandatory-accelerated/status?ro=2710-3910
    API->>ELIG: evaluate(RO)
    ELIG->>DB: query eligible Priority 99 for RO
    DB-->>ELIG: 45 cases
    ELIG-->>API: restrictionActive, queued 45, listed 45
    API-->>UI: status

    Note over UI: Block Auto Select and ZIP Select<br/>Grey Query sub-tabs except<br/>Priority 99 and Create Query<br/>Route to Mandatory Accelerated screen

    UI->>API: POST /mandatory-accelerated/assign
    API->>GATE: check(actor, effectiveGroup, case, method)
    GATE->>ELIG: isEligible(case, RO)?

    alt Case is eligible Priority 99
        ELIG-->>GATE: true
        GATE-->>API: permitted
        API->>DB: BEGIN
        API->>DB: verify still queue-available
        API->>DB: create selection, stamp method and reason
        API->>DB: recalculate queued count
        API->>DB: COMMIT
        API->>API: invalidate status cache
        API-->>UI: 201, queued 44
    else Case is lower priority
        ELIG-->>GATE: false
        GATE-->>API: rejected
        API->>DB: write audit record for blocked attempt
        API-->>UI: 409 MANDATORY_ACCELERATED_ACTIVE
        Note over UI: Show message with count<br/>and link to the correct screen
    end
```

The re-check inside the transaction is deliberate. The status call at the start is advisory — two managers can both pass it and race for the last case.

## 2.5 Screen access map

What each surface does while the restriction is active. Useful for building test coverage.

```mermaid
flowchart TD
    CA["Case Assignment"]

    CA --> GS["Group Summary<br/>UNCHANGED<br/>lists all employees always"]
    CA --> Q["Query"]
    CA --> RPT["Reports<br/>Priority 99 selectable only"]
    CA --> PEND["Pending<br/>UNRESTRICTED, list only"]
    CA --> HS["Hold / Skip<br/>readable, writes BLOCKED"]
    CA --> ABT["Assign by TIN<br/>UNRESTRICTED, any priority"]
    CA --> INV["Inventory Adjustment"]

    GS --> BTN["Group Mandatory Accelerated button<br/>NEW"]
    BTN --> GMA["Group accelerated list<br/>assign any case to any RO in group"]

    GS --> PICKRO["Select an RO"]
    PICKRO --> GATE{"queuedCount > 0?"}
    GATE -->|"Yes"| ROMA["RO accelerated screen<br/>Auto Select and ZIP Select BLOCKED"]
    GATE -->|"No"| NORM["Normal RO selection<br/>all methods available"]

    Q --> Q1["Priority 99 — ENABLED, default view"]
    Q --> Q2["Create Query — ENABLED"]
    Q --> Q3["All other sub-tabs — GREYED OUT"]

    classDef blocked fill:#fde8e8,stroke:#c62828,color:#000
    classDef open fill:#e8f5e9,stroke:#2e7d32,color:#000
    classDef neutral fill:#eceff1,stroke:#546e7a,color:#000
    class ROMA,Q3,HS blocked
    class Q1,Q2,ABT,PEND,NORM,GMA open
    class GS,INV neutral
```

**Open item:** Reports scope is not confirmed. Sarah has not reviewed the modern Reports implementation, so the restriction shown above is the stated intent rather than verified behaviour.

---

## Traceability

| Diagram | Covers | Test cases |
|---|---|---|
| 2.1 Enforcement decision | BE-C | TC-301 to TC-303, TC-501 to TC-503, TC-701 to TC-707 |
| 2.2 State lifecycle | BE-D | TC-404 to TC-406 |
| 2.3 Count behaviour | BE-A, BE-B, FE-D | TC-208, TC-604, TC-605 |
| 2.4 Assignment sequence | BE-C, BE-D | TC-401, TC-408, TC-409 |
| 2.5 Screen access map | BE-C, FE-B | TC-505 to TC-509 |
