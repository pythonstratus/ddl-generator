-- Mandatory Accelerated Case Assignment — schema changes
--
-- Oracle syntax, matching the rest of ENTITY. For another platform:
--   * Postgres:   VARCHAR2 -> VARCHAR, NUMBER(19) -> BIGINT, ADD ( ... ) -> one ADD COLUMN each
--   * SQL Server: VARCHAR2 -> NVARCHAR, add a clustered index strategy
--
-- CHANGES IN THIS REVISION
--   * case_selection gains the identity, actor and timestamp columns the JDBC repository writes.
--     The previous revision added only selection_method, reason_code and selected_as_group, which
--     assumed the rest already existed. They may — see the VERIFY block below before running.
--   * qind_status_rank is gone. Display rank is now derived in the ORDER BY from selection_status,
--     so there is no invented column to add and nothing to keep in step with the enum.
--   * Explicit REVOKE on the audit table. "Append-only" was a comment; now it is a grant.
--   * Index on case_selection (tin, tin_file_source) — the unpick path looks selections up by
--     case key on every call and had no index to do it with.

--------------------------------------------------------------------------------
-- 0. VERIFY BEFORE RUNNING
--------------------------------------------------------------------------------
-- Run this first and compare against the ALTER in section 1. Anything already
-- present must be removed from that ALTER or the statement fails outright.
--
--   SELECT column_name, data_type, nullable
--     FROM user_tab_columns
--    WHERE table_name = 'CASE_SELECTION'
--    ORDER BY column_id;
--
-- The three columns most likely to already exist under a different name are
-- selection_id, selected_by and selected_at. If they do, rename them in
-- JdbcCaseSelectionRepository instead of adding duplicates here.

--------------------------------------------------------------------------------
-- 1. Selection record
--------------------------------------------------------------------------------
-- selection_method is an enum column rather than a boolean mandatory_accelerated
-- flag. A boolean answers one question; this also gives the audit trail its
-- exception-path evidence and gives rule 10 — "a workaround does not clear the
-- restriction" — something concrete to assert against.

ALTER TABLE case_selection ADD (
  selection_id       VARCHAR2(36),
  selection_method   VARCHAR2(32),
  reason_code        VARCHAR2(48),
  selected_by        VARCHAR2(64),
  selected_as_group  VARCHAR2(8),
  selected_at        TIMESTAMP
);

-- Backfill for in-flight selections at cutover.
--
-- OPEN QUESTION 5, and it is still open. This assumes cases already Selected or
-- Pending are NOT treated as Mandatory Accelerated retrospectively. If the
-- business wants them included, this backfill changes and so does the count
-- behaviour on day one — an RO mid-selection would see their Priority 99 column
-- jump at cutover. Confirm before running in production.

UPDATE case_selection
   SET selection_id      = COALESCE(selection_id, RAWTOHEX(SYS_GUID())),
       selection_method  = COALESCE(selection_method, 'AUTO_SELECT'),
       reason_code       = COALESCE(reason_code, 'STANDARD_ASSIGNMENT'),
       selected_by       = COALESCE(selected_by, 'PRE-CUTOVER'),
       selected_at       = COALESCE(selected_at, SYSTIMESTAMP)
 WHERE selection_method IS NULL
    OR selection_id IS NULL
    OR reason_code IS NULL
    OR selected_by IS NULL
    OR selected_at IS NULL;

-- selected_as_group is deliberately left nullable. It records the group the
-- actor was *viewing as*, which is unknowable for pre-cutover rows, and a
-- backfilled guess in an audit-adjacent column is worse than a null.

ALTER TABLE case_selection MODIFY (selection_id     VARCHAR2(36) NOT NULL);
ALTER TABLE case_selection MODIFY (selection_method VARCHAR2(32) NOT NULL);
ALTER TABLE case_selection MODIFY (reason_code      VARCHAR2(48) NOT NULL);
ALTER TABLE case_selection MODIFY (selected_by      VARCHAR2(64) NOT NULL);
ALTER TABLE case_selection MODIFY (selected_at      TIMESTAMP    NOT NULL);

ALTER TABLE case_selection ADD CONSTRAINT uq_case_selection_id UNIQUE (selection_id);

-- The unpick path resolves a selection by case key on every call.
CREATE INDEX ix_case_selection_key
  ON case_selection (tin, tin_file_source, selection_status);

--------------------------------------------------------------------------------
-- 2. Optimistic lock token on case inventory
--------------------------------------------------------------------------------
-- Two managers in one group working a shared 45-case list will select the same
-- case. Without this, both writes succeed and one assignment silently overwrites
-- the other.

ALTER TABLE case_inventory ADD (row_version NUMBER(19) DEFAULT 0 NOT NULL);

--------------------------------------------------------------------------------
-- 3. Audit trail
--------------------------------------------------------------------------------
-- Append-only. The REVOKE statements below are the actual mechanism; a comment
-- saying "do not update this table" is not one. A mutable table is not evidence.

CREATE TABLE ma_audit_event (
  event_id              VARCHAR2(36)  NOT NULL,
  event_type            VARCHAR2(48)  NOT NULL,
  actor_user_id         VARCHAR2(64)  NOT NULL,
  actor_identity        VARCHAR2(256) NOT NULL,
  acting_as_group       VARCHAR2(8),
  ro_assignment_number  VARCHAR2(9),
  tin                   VARCHAR2(9),
  tin_file_source       VARCHAR2(8),
  selection_method      VARCHAR2(32),
  outcome               VARCHAR2(32)  NOT NULL,
  detail                VARCHAR2(2000),
  occurred_at           TIMESTAMP     NOT NULL,
  CONSTRAINT pk_ma_audit_event PRIMARY KEY (event_id)
);

CREATE INDEX ix_ma_audit_ro_time    ON ma_audit_event (ro_assignment_number, occurred_at);
CREATE INDEX ix_ma_audit_actor_time ON ma_audit_event (actor_user_id, occurred_at);
CREATE INDEX ix_ma_audit_type_time  ON ma_audit_event (event_type, occurred_at);

-- VERIFY the role name against your environment, then run. The application user
-- gets INSERT and SELECT and nothing else, so an UPDATE or DELETE reaching this
-- table fails at the database rather than depending on nobody having written one.
--
--   GRANT SELECT, INSERT ON ma_audit_event TO ENTITY_APP;
--   REVOKE UPDATE, DELETE ON ma_audit_event FROM ENTITY_APP;

--------------------------------------------------------------------------------
-- 4. Indexes supporting the eligibility predicate
--------------------------------------------------------------------------------
-- The predicate runs on nearly every page load through the status endpoint, once
-- per group render through the grouped count query, and again inside every
-- assignment transaction. It has to be cheap.
--
-- Verify these against real execution plans before UAT rather than trusting the
-- shape. On Exadata the usual finding applies: a forced index on a large driving
-- table can disable Smart Scan and cell offload, so check whether case_inventory
-- is better served by a full scan with the filter offloaded.

CREATE INDEX ix_case_inv_ma_lookup
  ON case_inventory (program_type, priority_alpha, selection_status, zip_code, case_grade);

CREATE INDEX ix_case_inv_key
  ON case_inventory (tin, tin_file_source);

-- Both directions. The RO predicate drives from ro_assignment_number; the group
-- query and the aligned-RO subquery drive from zip_code.
CREATE INDEX ix_ro_zip_alignment
  ON ro_zip_alignment (ro_assignment_number, zip_code, active_flag);

CREATE INDEX ix_ro_zip_reverse
  ON ro_zip_alignment (zip_code, active_flag, ro_assignment_number);

CREATE INDEX ix_ro_grade_criteria
  ON ro_grade_criteria (ro_assignment_number, min_case_grade, max_case_grade);

--------------------------------------------------------------------------------
-- 5. Model score
--------------------------------------------------------------------------------
-- Rank within a single alpha value is driven by a model score calculated against
-- balance, produced by a dedicated table in legacy. Three consecutive 99b rows
-- are NOT interchangeable — the top one is considered more productive.
--
-- If case_inventory has no model_score column, adding it here is necessary but
-- not sufficient: something has to populate it. An empty column produces an
-- ORDER BY that runs and returns rows in an order nobody can defend. Run the
-- payload diagnostic in the README before assuming this ALTER is the whole fix.

ALTER TABLE case_inventory ADD (model_score NUMBER(10));

COMMENT ON COLUMN case_inventory.model_score IS
  'Rank within a priority alpha value. Sourced from the legacy prioritization model. Higher is more productive. NULL sorts last.';

--------------------------------------------------------------------------------
-- 6. Post-migration sanity check
--------------------------------------------------------------------------------
-- Run after deploying. A non-zero count from the second query means the display
-- ordering cannot be trusted and BE-A is not done, whatever the tests say.
--
--   -- Cases in the accelerated band, by status:
--   SELECT selection_status, COUNT(*)
--     FROM case_inventory
--    WHERE priority_alpha IN ('99a','99b','99c','99d','99e')
--    GROUP BY selection_status;
--
--   -- Accelerated cases with no model score:
--   SELECT COUNT(*)
--     FROM case_inventory
--    WHERE priority_alpha IN ('99a','99b','99c','99d','99e')
--      AND model_score IS NULL;
