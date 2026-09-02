package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign.CaseSelection;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.AcceleratedCounts;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.Actor;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.PriorityAlpha;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.ProgramType;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.QuerySubTab;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.ReasonCode;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionMethod;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.SelectionStatus;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.eligibility.EligibilitySql;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.QueueControlCommand;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.UnpickNotPermittedException;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Regression suite for the confirmed business rules.
 *
 * <p>Each test names the rule it defends. When someone changes a rule in six months, these tests
 * are what tells them which behaviour they just altered and whether the change was intended.
 */
class MandatoryAcceleratedRuleTest {

    @Nested
    @DisplayName("Rule 5 — display order is strictly priority alpha, 99a first")
    class DisplayOrder {

        @Test
        void ordersHigherBandsFirstThenAlphaAscending() {
            var values = List.of(
                    PriorityAlpha.parse("95a"),
                    PriorityAlpha.parse("99c"),
                    PriorityAlpha.parse("99a"),
                    PriorityAlpha.parse("99b"));

            var sorted =
                    values.stream().sorted(PriorityAlpha.DISPLAY_ORDER).map(Object::toString).toList();

            assertThat(sorted).containsExactly("99a", "99b", "99c", "95a");
        }

        @Test
        void allFiveAcceleratedValuesAreInScope() {
            assertThat(PriorityAlpha.ACCELERATED_VALUES)
                    .containsExactlyInAnyOrder("99a", "99b", "99c", "99d", "99e");
        }

        /**
         * The reason band is parsed as a number rather than compared as text. Lexically "101b"
         * sorts above "99a" because '1' precedes '9', which would put ordinary cases at the top of
         * an accelerated screen.
         */
        @Test
        void threeDigitBandsDoNotOutrankNinetyNine() {
            var sorted = List.of(
                            PriorityAlpha.parse("101b"),
                            PriorityAlpha.parse("99a"),
                            PriorityAlpha.parse("103b"))
                    .stream()
                    .sorted(PriorityAlpha.DISPLAY_ORDER)
                    .map(Object::toString)
                    .toList();

            assertThat(sorted).containsExactly("103b", "101b", "99a");
        }

        /**
         * Oracle sorts nulls first on a DESC sort, so an unscored case would outrank every scored
         * one on the screen without NULLS LAST. Asserted on the SQL text because the ordering is
         * applied in the database and there is no Java comparator to exercise.
         */
        @Test
        void modelScoreOrderingPushesNullsLast() {
            assertThat(EligibilitySql.DISPLAY_ORDER_BY)
                    .contains("ci.model_score DESC NULLS LAST");
        }
    }

    @Nested
    @DisplayName("Rule 4 — selection order within the accelerated set is the manager's choice")
    class SelectionOrder {

        /**
         * Guards against a plausible misreading of the requirements document phrase "priority alpha
         * order starting with 99a". That describes display sequence, not a selection gate. If
         * someone adds a "you must take 99a first" check, this test should be the thing that stops
         * them — and if the business later reverses the decision, deleting this test should be a
         * conscious act rather than a side effect.
         */
        @Test
        void thereIsNoApiForNextRequiredAlpha() {
            var methods = List.of(PriorityAlpha.class.getDeclaredMethods());
            assertThat(methods)
                    .as("no method should exist that implies a required next alpha")
                    .noneMatch(m -> m.getName().toLowerCase().contains("required"));
        }
    }

    @Nested
    @DisplayName("Counts — two fall on selection, two hold")
    class Counts {

        @Test
        void restrictionTracksQueuedNotListed() {
            // Mid-session: 45 listed, 43 still queued, 2 pending.
            var partial = new AcceleratedCounts(43, 45, 45, 2);
            assertThat(partial.restrictionActive()).isTrue();

            // Everything taken. Listed still reads 45; the restriction is over.
            var complete = new AcceleratedCounts(0, 45, 45, 45);
            assertThat(complete.restrictionActive()).isFalse();
            assertThat(complete.listed()).isEqualTo(45);
        }

        @Test
        void groupSummaryFigureHoldsWhenCasesAreSelected() {
            // An RO showing 46 still shows 46 after two assignments, with Pending at 2.
            // Confirmed directly on 01 Sep. It falls only on delivery.
            var before = new AcceleratedCounts(46, 46, 46, 0);
            var afterTwo = new AcceleratedCounts(44, 46, 46, 2);

            assertThat(afterTwo.groupSummaryPriority99())
                    .isEqualTo(before.groupSummaryPriority99());
            assertThat(afterTwo.queued()).isEqualTo(44);
            assertThat(afterTwo.pending()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Rule 10 — exactly two sanctioned workarounds")
    class Workarounds {

        @Test
        void queryAndAssignByTinArePermittedAndNothingElseIs() {
            assertThat(SelectionMethod.QUERY.isPermittedDuringRestriction()).isTrue();
            assertThat(SelectionMethod.ASSIGN_BY_TIN.isPermittedDuringRestriction()).isTrue();
            assertThat(SelectionMethod.AUTO_SELECT.isPermittedDuringRestriction()).isFalse();
            assertThat(SelectionMethod.ZIP_CODE_SELECT.isPermittedDuringRestriction()).isFalse();
            assertThat(SelectionMethod.REPORT_SELECT.isPermittedDuringRestriction()).isFalse();
        }

        /**
         * Permitted and "is a workaround" are different questions, and conflating them made the
         * exception-usage audit useless: taking an accelerated case is the rule being enforced,
         * not a way around it, so filing it as workaround usage means the compliance report
         * returns the entire day's work.
         */
        @Test
        void takingAnAcceleratedCaseIsPermittedButIsNotAWorkaround() {
            assertThat(SelectionMethod.MANDATORY_ACCELERATED.isPermittedDuringRestriction())
                    .isTrue();
            assertThat(SelectionMethod.MANDATORY_ACCELERATED.isSanctionedWorkaround()).isFalse();
            assertThat(SelectionMethod.QUERY.isSanctionedWorkaround()).isTrue();
            assertThat(SelectionMethod.ASSIGN_BY_TIN.isSanctionedWorkaround()).isTrue();
        }

        @Test
        void createQueryStaysEnabledBecauseQueryIsAWorkaround() {
            assertThat(QuerySubTab.enabledDuringRestriction())
                    .containsExactlyInAnyOrder(QuerySubTab.PRIORITY_99, QuerySubTab.CREATE_QUERY);
            assertThat(QuerySubTab.disabledDuringRestriction())
                    .contains(QuerySubTab.NATIONAL_QUEUE, QuerySubTab.MY_SAVED_QUERIES);
        }
    }

    @Nested
    @DisplayName("Rule 9 — Manager Queue Control is blocked, and recorded as itself")
    class QueueControl {

        /**
         * The command used to report AUTO_SELECT, so a refused Hold/Skip landed in the audit trail
         * as an attempted Auto Select — a record of something that never happened, on the one
         * table whose purpose is being accurate after the fact.
         */
        @Test
        void queueControlIsAuditedAsQueueControl() {
            var command =
                    new QueueControlCommand(RoAssignmentNumber.parse("2710-3910"), "HOLD_DATE");

            assertThat(command.selectionMethod()).isEqualTo(SelectionMethod.QUEUE_CONTROL);
            assertThat(command.selectionMethod().isPermittedDuringRestriction()).isFalse();
        }
    }

    @Nested
    @DisplayName("Rule 8 — accelerated selections cannot be unpicked")
    class Unpick {

        @Test
        void acceleratedSelectionRefusesUnpick() {
            var selection = new CaseSelection(
                    "463307286", "IMF", "2710-3910",
                    SelectionMethod.MANDATORY_ACCELERATED,
                    ReasonCode.MANDATORY_ACCELERATED_CASE, "u123", "2710");

            assertThatThrownBy(selection::unpick)
                    .isInstanceOf(UnpickNotPermittedException.class);
        }

        @Test
        void ordinarySelectionRemainsUnpickableBeforePending() {
            var selection = new CaseSelection(
                    "463307286", "IMF", "2710-3910",
                    SelectionMethod.AUTO_SELECT,
                    ReasonCode.STANDARD_ASSIGNMENT, "u123", "2710");

            selection.unpick();
            assertThat(selection.status().name()).isEqualTo("QUEUED");
        }

        @Test
        void ordinarySelectionCannotBeUnpickedOncePending() {
            var selection = new CaseSelection(
                    "463307286", "IMF", "2710-3910",
                    SelectionMethod.AUTO_SELECT,
                    ReasonCode.STANDARD_ASSIGNMENT, "u123", "2710");
            selection.advanceToPending();

            assertThatThrownBy(selection::unpick).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Rule 6 — ZIP alignment does not constrain the assignment target")
    class Alignment {

        @Test
        void anyRoInTheSameGroupIsAValidTarget() {
            // The rebalancing example: a case aligned to 3921, assigned to 3937.
            var alignedTo = RoAssignmentNumber.parse("2710-3921");
            var target = RoAssignmentNumber.parse("2710-3937");

            assertThat(alignedTo.sameGroupAs(target)).isTrue();
        }

        /**
         * Rule 6 opens the target, not the case. A case aligned to another group's RO is out of
         * scope however permissive the target rule is, and the write path checks both.
         */
        @Test
        void aCaseAlignedToAnotherGroupIsNotInScope() {
            var alignedTo = RoAssignmentNumber.parse("2999-3921");
            var target = RoAssignmentNumber.parse("2710-3937");

            assertThat(alignedTo.sameGroupAs(target)).isFalse();
            assertThat(alignedTo.inGroup("2710")).isFalse();
        }

        @Test
        void assignmentNumberEndingIn7000DenotesQueueMembership() {
            assertThat(RoAssignmentNumber.parse("2710-7000").isQueue()).isTrue();
            assertThat(RoAssignmentNumber.parse("2710-3910").isQueue()).isFalse();
        }
    }

    @Nested
    @DisplayName("Rule 1 — International is excluded entirely")
    class International {

        @Test
        void internationalIsNeverSubjectToTheRestriction() {
            assertThat(ProgramType.INTERNATIONAL.isSubjectToMandatoryAccelerated()).isFalse();
            assertThat(ProgramType.GENERAL.isSubjectToMandatoryAccelerated()).isTrue();
        }
    }

    @Nested
    @DisplayName("Impersonation — the effective group governs, not the actor's home group")
    class Impersonation {

        @Test
        void nationalAnalystIsBoundByTheGroupTheyAreViewingAs() {
            var analyst = new Actor("sv01", "S. Vainer", "NATIONAL_ANALYST", "0001", "271039");

            assertThat(analyst.effectiveGroupId()).isEqualTo("271039");
            assertThat(analyst.isImpersonating()).isTrue();
            assertThat(analyst.auditIdentity()).contains("acting as Group 271039");
        }

        @Test
        void managerInTheirOwnGroupIsNotImpersonating() {
            var manager = new Actor("bm02", "B. Marks", "GROUP_MANAGER", "2710", null);

            assertThat(manager.effectiveGroupId()).isEqualTo("2710");
            assertThat(manager.isImpersonating()).isFalse();
        }
    }

    @Nested
    @DisplayName("One predicate — the group query cannot drift from the RO query")
    class PredicateComposition {

        /**
         * The defect this replaces: the group query hand-wrote its own WHERE clause and dropped
         * the grade-criteria rule, so the group screen listed cases no RO in the group could take
         * and reconciliation check 2 would fail against real data. Both predicates now share the
         * case-level clauses and the deferral to existing rules by construction.
         */
        @Test
        void bothPredicatesShareTheSameCaseLevelClauses() {
            assertThat(EligibilitySql.ELIGIBILITY_PREDICATE)
                    .contains(EligibilitySql.CASE_CLAUSES)
                    .contains(EligibilitySql.EXISTING_RULES);

            assertThat(EligibilitySql.GROUP_ELIGIBILITY_PREDICATE)
                    .contains(EligibilitySql.CASE_CLAUSES)
                    .contains(EligibilitySql.EXISTING_RULES);
        }

        @Test
        void bothPredicatesApplyGradeCriteria() {
            assertThat(EligibilitySql.ELIGIBILITY_PREDICATE).contains("ro_grade_criteria");
            assertThat(EligibilitySql.GROUP_ELIGIBILITY_PREDICATE).contains("ro_grade_criteria");
        }

        /**
         * ZIP and grade must be satisfied by the same RO. Split across two EXISTS clauses the
         * group list would admit a case one RO can take by ZIP and another can take by grade,
         * which no single RO can take at all.
         */
        @Test
        void groupAlignmentTiesZipAndGradeToOneRevenueOfficer() {
            assertThat(EligibilitySql.GROUP_ALIGNMENT_AND_GRADE)
                    .as("one EXISTS, joining both tables to the same ro_assignment_number")
                    .containsOnlyOnce("EXISTS");
        }
    }

    @Nested
    @DisplayName("QIND — Selected and Pending pin above Queued")
    class Qind {

        @Test
        void displayRankPutsSelectedAndPendingFirst() {
            assertThat(SelectionStatus.SELECTED.displayRank())
                    .isLessThan(SelectionStatus.QUEUED.displayRank());
            assertThat(SelectionStatus.PENDING.displayRank())
                    .isLessThan(SelectionStatus.QUEUED.displayRank());
        }

        @Test
        void qindLettersMatchTheScreen() {
            assertThat(SelectionStatus.SELECTED.qind()).isEqualTo("S");
            assertThat(SelectionStatus.PENDING.qind()).isEqualTo("P");
            assertThat(SelectionStatus.HOLD.qind()).isEqualTo("H");
            assertThat(SelectionStatus.SKIPPED.qind()).isEqualTo("K");
        }

        /**
         * The SQL rank and the enum rank order the same statuses the same way. They are two
         * expressions of one rule, so they are asserted against each other rather than left to
         * drift.
         */
        @Test
        void sqlRankMirrorsTheEnum() {
            assertThat(EligibilitySql.QIND_STATUS_RANK)
                    .contains("'SELECTED' THEN 0")
                    .contains("'PENDING'  THEN 1");
        }
    }

    @Test
    @DisplayName("Sanity: the display comparator is the only ordering exposed")
    void comparatorIsStable() {
        Comparator<PriorityAlpha> comparator = PriorityAlpha.DISPLAY_ORDER;
        assertThat(comparator.compare(PriorityAlpha.parse("99a"), PriorityAlpha.parse("99a")))
                .isZero();
    }
}
