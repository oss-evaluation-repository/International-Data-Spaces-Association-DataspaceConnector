package de.fraunhofer.isst.dataspaceconnector.services.resources;

import java.util.List;

import de.fraunhofer.isst.dataspaceconnector.model.Contract;
import de.fraunhofer.isst.dataspaceconnector.model.ContractRule;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Handles the relation between a contract and its rules.
 */
@Service
@NoArgsConstructor
public class ContractRuleLinker extends BaseUniDirectionalLinkerService<Contract, ContractRule,
        ContractService, RuleService> {
    @Override
    protected List<ContractRule> getInternal(final Contract owner) {
        return owner.getRules();
    }
}
