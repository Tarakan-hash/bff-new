package sb.broker.bff.companyprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Юридический адрес (Глобас). Даты — ISO-8601")
public record LegalAddressDTO(
        @Schema(example = "2020-01-15T00:00:00Z") String fromDate,
        String tillDate,
        @Schema(example = "г. Москва") String address,
        @Schema(description = "Признак недостоверности", example = "false") Boolean isAddressFalse,
        String falseAddressBeginDate,
        String falseAddressEndDate,
        @Schema(description = "Признак массового адреса ФНС", example = "false") Boolean isMassAddressFTS,
        String massTillDate,
        @Schema(description = "Источник адреса; всегда GLOBAS", example = "GLOBAS") String source
) {
}
