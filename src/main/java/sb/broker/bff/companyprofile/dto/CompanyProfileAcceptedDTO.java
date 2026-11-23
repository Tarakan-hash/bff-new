package sb.broker.bff.companyprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Запущено обновление карточки")
public record CompanyProfileAcceptedDTO(
        @Schema(description = "Идентификатор сессии для GET", example = "lk-company-profile-7706810730-6e3d144f")
        String workflowId
) {
}
