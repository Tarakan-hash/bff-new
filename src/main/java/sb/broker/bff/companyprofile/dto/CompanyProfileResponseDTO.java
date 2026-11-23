package sb.broker.bff.companyprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Карточка компании — данные события {@code complete}.
 */
@Schema(description = "Карточка компании (событие complete)")
public record CompanyProfileResponseDTO(
        @Schema(description = "ИНН", example = "7706810730") String inn,
        @Schema(description = "Наименование", example = "ООО «Страховой брокер Сбербанка»") String name,
        String fullName,
        @Schema(example = "1147746683468") String ogrn,
        @Schema(example = "Касин Олег Александрович") String ceo,
        @Schema(example = "66.21") String okved,
        @Schema(example = "43") String gosb,
        @Schema(example = "76") Integer regionCode,
        @Schema(description = "Закреплённый брокер; null, если связи нет") BrokerDTO assignedBroker,
        @Schema(description = "null — список не передан; пустой — кодов нет") List<String> additionalOkveds,
        @Schema(description = "null — список не передан; пустой — адресов нет") List<LegalAddressDTO> legalAddresses
) {
}
