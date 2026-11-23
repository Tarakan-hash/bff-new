package sb.broker.bff.companyprofile;

import sb.broker.bff.companyprofile.dto.BrokerDTO;
import sb.broker.bff.companyprofile.dto.CompanyProfileResponseDTO;
import sb.broker.bff.companyprofile.dto.LegalAddressDTO;
import sb.broker.bff.workflow.api.companyprofile.Broker;
import sb.broker.bff.workflow.api.companyprofile.CompanyProfile;
import sb.broker.bff.workflow.api.companyprofile.LegalAddress;

import java.util.List;

/**
 * Контракт workflow → HTTP-контракт кабинета. {@code null}-списки сохраняются: план различает
 * «не передан» и «пустой».
 */
final class CompanyProfileMapper {

    private CompanyProfileMapper() {
    }

    static CompanyProfileResponseDTO toDto(CompanyProfile profile) {
        return new CompanyProfileResponseDTO(profile.inn(), profile.name(), profile.fullName(), profile.ogrn(),
                profile.ceo(), profile.okved(), profile.gosb(), profile.regionCode(),
                toDto(profile.assignedBroker()), profile.additionalOkveds(), toDto(profile.legalAddresses()));
    }

    private static BrokerDTO toDto(Broker broker) {
        return broker == null ? null : new BrokerDTO(broker.firstName(), broker.lastName(), broker.email());
    }

    private static List<LegalAddressDTO> toDto(List<LegalAddress> addresses) {
        return addresses == null ? null : addresses.stream()
                .map(address -> new LegalAddressDTO(address.fromDate(), address.tillDate(), address.address(),
                        address.isAddressFalse(), address.falseAddressBeginDate(), address.falseAddressEndDate(),
                        address.isMassAddressFTS(), address.massTillDate(), address.source()))
                .toList();
    }
}
