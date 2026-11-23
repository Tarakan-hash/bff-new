package sb.broker.bff.riskprofile;

import sb.broker.bff.riskprofile.dto.InsuranceRecommendationDTO;
import sb.broker.bff.riskprofile.dto.NonInsuranceRecommendationDTO;
import sb.broker.bff.riskprofile.dto.RecommendationGroupDTO;
import sb.broker.bff.riskprofile.dto.RecommendationsDTO;
import sb.broker.bff.riskprofile.dto.RiskDTO;
import sb.broker.bff.riskprofile.dto.RiskGroupDTO;
import sb.broker.bff.riskprofile.dto.RiskProfileDTO;
import sb.broker.bff.riskprofile.dto.ServiceRecommendationDTO;
import sb.broker.bff.workflow.api.riskprofile.RecommendationGroup;
import sb.broker.bff.workflow.api.riskprofile.Recommendations;
import sb.broker.bff.workflow.api.riskprofile.RiskProfile;

import java.util.List;
import java.util.function.Function;

/**
 * Контракт workflow → HTTP-контракт кабинета. Отсутствующие списки — пустые, как требует план.
 */
final class RiskProfileMapper {

    private RiskProfileMapper() {
    }

    static RiskProfileDTO toDto(RiskProfile profile) {
        return new RiskProfileDTO(profile.inn(), profile.companyName(), profile.okved(), profile.calculatedAt(),
                map(profile.riskGroups(), group -> new RiskGroupDTO(group.code(), group.name(),
                        map(group.risks(), risk -> new RiskDTO(risk.code(), risk.name(), risk.description(),
                                risk.linkedProduct())))),
                toDto(profile.recommendations()));
    }

    private static RecommendationsDTO toDto(Recommendations recommendations) {
        if (recommendations == null) {
            return new RecommendationsDTO(List.of(), List.of(), List.of());
        }
        return new RecommendationsDTO(
                map(recommendations.insurance(), r -> new InsuranceRecommendationDTO(r.code(), r.name(), toDto(r.group()))),
                map(recommendations.service(), r -> new ServiceRecommendationDTO(r.code(), r.name(), r.description())),
                map(recommendations.nonInsurance(), r -> new NonInsuranceRecommendationDTO(r.code(), r.name(),
                        r.description(), toDto(r.group()))));
    }

    private static RecommendationGroupDTO toDto(RecommendationGroup group) {
        return group == null ? null : new RecommendationGroupDTO(group.code(), group.name(), group.priority());
    }

    private static <S, T> List<T> map(List<S> source, Function<S, T> mapper) {
        return source == null ? List.of() : source.stream().map(mapper).toList();
    }
}
