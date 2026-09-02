package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.api;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.api.dto.AssignRequest;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.api.dto.GroupSummaryRow;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.api.dto.StatusResponse;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign.AcceleratedAssignmentCommand;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign.AcceleratedAssignmentService;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign.AssignmentResult;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.RoAssignmentNumber;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.read.AcceleratedCasePage;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.read.AcceleratedListService;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.read.CaseDetailPort;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.status.RestrictionStateService;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.status.UiRestrictionState;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mandatory Accelerated endpoints.
 *
 * <p>Thin by design. Enforcement lives in the service layer so that every entry point inherits it —
 * including ones added after this controller. Nothing here decides whether an action is permitted.
 *
 * <p>VERIFY: the role names. "Managers and acting managers, secretaries occasionally supporting"
 * was resolved on 01 Sep as the scope, and it is a management control. These two role strings are
 * a guess at how that maps onto ENTITY's security model.
 */
@RestController
@RequestMapping("/api/case-assignment/mandatory-accelerated")
@PreAuthorize("hasAnyRole('GROUP_MANAGER', 'NATIONAL_ANALYST')")
public class MandatoryAcceleratedController {

    private final RestrictionStateService statusService;
    private final AcceleratedListService listService;
    private final AcceleratedAssignmentService assignmentService;
    private final CaseDetailPort caseDetail;

    public MandatoryAcceleratedController(
            RestrictionStateService statusService,
            AcceleratedListService listService,
            AcceleratedAssignmentService assignmentService,
            CaseDetailPort caseDetail) {
        this.statusService = statusService;
        this.listService = listService;
        this.assignmentService = assignmentService;
        this.caseDetail = caseDetail;
    }

    /**
     * {@code GET /status?roAssignmentNumber=2710-3910}
     *
     * <p>The one call every screen gates on. Hit on most page loads, so it is served from the
     * per-request cache and is cheap within a render.
     */
    @Operation(summary = "Restriction state and the four counts for one Revenue Officer")
    @GetMapping("/status")
    public StatusResponse status(@RequestParam String roAssignmentNumber) {
        return StatusResponse.from(
                statusService.statusFor(RoAssignmentNumber.parse(roAssignmentNumber)));
    }

    /**
     * {@code GET /ui-state?roAssignmentNumber=2710-3910}
     *
     * <p>Which Query sub-tabs to grey out, whether Hold/Skip is writable, where to route. Advisory
     * only — the server refuses blocked actions whatever the client does with this.
     */
    @Operation(summary = "Advisory UI state: disabled sub-tabs, writability, route")
    @GetMapping("/ui-state")
    public UiRestrictionState uiState(@RequestParam String roAssignmentNumber) {
        return statusService.uiStateFor(RoAssignmentNumber.parse(roAssignmentNumber));
    }

    /** {@code GET /cases?roAssignmentNumber=2710-3910&page=0&size=50} — the RO-scoped list. */
    @Operation(summary = "Accelerated cases for one Revenue Officer, in display order")
    @GetMapping("/cases")
    public AcceleratedCasePage casesForRo(
            @RequestParam String roAssignmentNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return listService.forRevenueOfficer(
                RoAssignmentNumber.parse(roAssignmentNumber), page, size);
    }

    /**
     * {@code GET /group/{groupId}/cases} — the group list. This is the Legacy F2 equivalent and has
     * no existing analogue in modern, which is why it is the one screen that cannot be reduced to
     * a filter on an existing view.
     *
     * <p>No sort parameter. Rule 5 makes display order non-negotiable, so exposing one would let a
     * client request an order the business has ruled out.
     */
    @Operation(summary = "Every accelerated case across the group, one row per case")
    @GetMapping("/group/{groupId}/cases")
    public AcceleratedCasePage casesForGroup(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return listService.forGroup(groupId, page, size);
    }

    /** {@code GET /group/{groupId}/summary} — Priority 99 counts for the employee table. */
    @Operation(summary = "Priority 99 and Pending counts per Revenue Officer")
    @GetMapping("/group/{groupId}/summary")
    public List<GroupSummaryRow> groupSummary(@PathVariable String groupId) {
        return listService.groupSummaryCounts(groupId).entrySet().stream()
                .map(entry -> new GroupSummaryRow(
                        entry.getKey().toString(),
                        entry.getValue().groupSummaryPriority99(),
                        entry.getValue().pending(),
                        entry.getValue().queued(),
                        entry.getValue().restrictionActive()))
                .toList();
    }

    /**
     * {@code GET /cases/{tin}/{tinFileSource}/detail} — review before assign.
     *
     * <p>Delegates to the existing case detail service. See {@link CaseDetailPort} for the open
     * risk: a queued case has no assignment context, and if the existing endpoint needs one this
     * returns nothing for exactly the cases this screen is about.
     */
    @Operation(summary = "Case summary, modules, activity, time, name and address")
    @GetMapping("/cases/{tin}/{tinFileSource}/detail")
    public Map<String, Object> caseDetail(
            @PathVariable String tin, @PathVariable String tinFileSource) {
        return caseDetail.caseDetail(new CaseKey(tin, tinFileSource));
    }

    /** {@code POST /assign} — assign one accelerated case. */
    @Operation(summary = "Assign one accelerated case to any Revenue Officer in the group")
    @PostMapping("/assign")
    public ResponseEntity<AssignmentResult> assign(@Valid @RequestBody AssignRequest request) {
        var command = new AcceleratedAssignmentCommand(
                new CaseKey(request.tin(), request.tinFileSource()),
                RoAssignmentNumber.parse(request.targetRoAssignmentNumber()),
                request.expectedRowVersion());
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.assign(command));
    }

    /**
     * {@code POST /unpick} — present so the refusal is explicit and auditable rather than the
     * action simply being absent. Always refuses for accelerated selections.
     */
    @Operation(summary = "Unpick a selection. Always refused for Mandatory Accelerated")
    @PostMapping("/unpick")
    public ResponseEntity<Void> unpick(
            @RequestParam String tin, @RequestParam String tinFileSource) {
        assignmentService.unpick(new CaseKey(tin, tinFileSource));
        return ResponseEntity.noContent().build();
    }
}
