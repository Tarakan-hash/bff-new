package sb.broker.bff.policy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import sb.broker.bff.workflow.api.policy.PolicyRisk;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(
        ignoreUnknown = true
)
public record Policy(String insuranceGroup,
                     String policyNumber,
                     String insuranceCompany,
                     BigDecimal totalInsurancePremium,
                     String validFrom,
                     String validTo,
                     String issueDate,
                     Long daysToExpire,
                     Long productId,
                     String insuranceType,
                     String dealType,
                     Integer paymentCount,
                     BigDecimal insuredAmount,
                     List<PolicyRisk> risks) {
}
