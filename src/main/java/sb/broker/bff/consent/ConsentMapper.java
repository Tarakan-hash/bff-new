package sb.broker.bff.consent;

import sb.broker.bff.consent.dto.UserConsentDTO;
import sb.broker.bff.workflow.api.consent.UserConsent;

import java.util.List;

/**
 * Контракт workflow → HTTP-контракт кабинета.
 */
final class ConsentMapper {

    private ConsentMapper() {
    }

    static List<UserConsentDTO> toDto(List<UserConsent> consents) {
        return consents.stream()
                .map(consent -> new UserConsentDTO(consent.title(), consent.type(), consent.status(),
                        consent.required(), consent.guid(), consent.date()))
                .toList();
    }
}
