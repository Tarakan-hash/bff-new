package sb.broker.bff.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Данные текущего пользователя из проверенного JWT")
public record ProfileInfoDTO(
        @Schema(description = "Идентификатор пользователя: claim sub") String id,
        @Schema(description = "Email из настроенного email claim") String email,
        @Schema(description = "Claim firstName; null, если непустой строки в токене нет") String firstName,
        @Schema(description = "Claim lastName; null, если непустой строки в токене нет") String lastName,
        @Schema(description = "Claim secondName; null, если непустой строки в токене нет") String secondName,
        @Schema(description = "Claim inn; null, если непустой строки в токене нет") String inn,
        @Schema(description = "Claims phone; null, если непустой строки в токене нет") String phone
) {
}
