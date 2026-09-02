package gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.config;

import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign.CaseInventoryWriteRepository;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign.CaseSelectionRepository;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign.JdbcCaseInventoryWriteRepository;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.assign.JdbcCaseSelectionRepository;
import gov.irs.sbse.os.ts.csp.alsentity.ale.accelerated.enforcement.AssignmentContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Wiring for the three ports most likely to be satisfied by something that already exists in
 * ENTITY.
 *
 * <p>Every bean here is {@code @ConditionalOnMissingBean}. Define your own
 * {@code AssignmentContext}, {@code CaseInventoryWriteRepository} or
 * {@code CaseSelectionRepository} anywhere in the application and yours wins, with nothing to
 * delete here. That is deliberate: the defaults exist so the module compiles, starts and can be
 * exercised in MTEST on day one, not because they are the right long-term answer.
 *
 * <p>{@code RevenueOfficerLookup} and {@code CaseDetailPort} are <b>not</b> defaulted. Both need
 * data this module has no business owning — the RO roster and the case detail shape — and a
 * plausible-looking default for either would be worse than a startup failure that names exactly
 * what is missing.
 */
@Configuration
public class AcceleratedInfrastructureConfig {

    /**
     * Reference write path. Replace with a thin adapter onto the existing Case Assignment service
     * if one owns the selection lifecycle — do not run two write paths against the same table.
     */
    @Bean
    @ConditionalOnMissingBean(CaseInventoryWriteRepository.class)
    public CaseInventoryWriteRepository caseInventoryWriteRepository(
            @Qualifier("secondaryNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        return new JdbcCaseInventoryWriteRepository(jdbc);
    }

    /**
     * Reference selection store. Same reasoning: {@code case_selection} is very likely already
     * owned by the existing Case Assignment service.
     */
    @Bean
    @ConditionalOnMissingBean(CaseSelectionRepository.class)
    public CaseSelectionRepository caseSelectionRepository(
            @Qualifier("secondaryNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        return new JdbcCaseSelectionRepository(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean(AssignmentContext.class)
    public AssignmentContext assignmentContext() {
        return new RequestAttributeAssignmentContext();
    }
}
