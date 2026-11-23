package sb.broker.bff.companyprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Данные события {@code error} карточки компании.
 */
@Schema(description = "Ошибка карточки компании (событие error)")
public record CompanyProfileErrorDTO(
        @Schema(description = "SESSION_NOT_FOUND, COMPANY_NOT_FOUND, WORKFLOW_FAILED, INTERNAL_ERROR, INTERNAL_FALLBACK",
                example = "COMPANY_NOT_FOUND") String code,
        @Schema(example = "Компания с указанным ИНН не найдена в системе CRM.") String message
) {
}
