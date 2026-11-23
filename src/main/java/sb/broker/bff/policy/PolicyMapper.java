package sb.broker.bff.policy;

import sb.broker.bff.policy.dto.Policy;
import sb.broker.bff.policy.dto.PolicyDTO;
import sb.broker.bff.policy.dto.PolicyRiskDTO;

import java.util.List;

/**
 * Контракт workflow → HTTP-контракт кабинета.
 */
final class PolicyMapper {

    private PolicyMapper() {
    }

    static List<PolicyDTO> toDto(List<Policy> policies) {
        return policies.stream().map(PolicyMapper::toDto).toList();
    }

    private static PolicyDTO toDto(Policy policy) {
        return new PolicyDTO(policy.insuranceGroup(), policy.policyNumber(), policy.insuranceCompany(),
                policy.totalInsurancePremium(), policy.validFrom(), policy.validTo(), policy.issueDate(),
                policy.daysToExpire(), policy.productId(), policy.insuranceType(), policy.dealType(), policy.paymentCount(),
                policy.insuredAmount(), policy.risks() == null ? List.of() : policy.risks().stream()
                        .map(risk -> new PolicyRiskDTO(risk.riskName(), risk.insurancePrice()))
                        .toList());
    }
}
