package sb.broker.bff.companyprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Публичное имя брокера")
public record BrokerDTO(
        @Schema(example = "Иван") String firstName,
        @Schema(example = "Петров") String lastName,
        @Schema(example = "test@test.ru") String email
) {
}
