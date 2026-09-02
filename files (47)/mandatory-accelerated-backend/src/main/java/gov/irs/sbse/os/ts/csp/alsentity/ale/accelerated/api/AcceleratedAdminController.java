package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.api;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign.EmergencyUnselectService;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.domain.CaseKey;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.reconcile.ReconciliationReport;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.reconcile.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative endpoints. Not reachable by Group Managers under any role configuration — the
 * emergency unselect in particular must never become a manager function.
 */
@RestController
@RequestMapping("/api/case-assignment/mandatory-accelerated/admin")
@PreAuthorize("hasRole('CASE_ASSIGNMENT_ADMIN')")
public class AcceleratedAdminController {

    private final ReconciliationService reconciliation;
    private final EmergencyUnselectService emergencyUnselect;

    public AcceleratedAdminController(
            ReconciliationService reconciliation, EmergencyUnselectService emergencyUnselect) {
        this.reconciliation = reconciliation;
        this.emergencyUnselect = emergencyUnselect;
    }

    /**
     * {@code GET /admin/reconcile/{groupId}}
     *
     * <p>Runnable on demand in MTEST. This is the artifact to show at review when someone asks
     * whether the counts can be trusted. Read {@code checksSkipped} on the report before treating
     * a green result as complete.
     */
    @Operation(summary = "Reconcile counts and case sets for one group")
    @GetMapping("/reconcile/{groupId}")
    public ReconciliationReport reconcile(@PathVariable String groupId) {
        return reconciliation.reconcile(groupId);
    }

    /**
     * {@code POST /admin/emergency-unselect}
     *
     * <p>Disabled by default. Leave it disabled until the business owner confirms modern wants a
     * supported administrative path rather than the controlled data fix legacy used.
     */
    @Operation(summary = "Emergency unselect. Disabled by default. Never a manager function")
    @PostMapping("/emergency-unselect")
    public ResponseEntity<Void> emergencyUnselect(
            @RequestParam String tin,
            @RequestParam String tinFileSource,
            @RequestParam String justification) {
        emergencyUnselect.unselect(new CaseKey(tin, tinFileSource), justification);
        return ResponseEntity.noContent().build();
    }
}
