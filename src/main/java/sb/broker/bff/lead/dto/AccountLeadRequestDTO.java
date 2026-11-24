package sb.broker.bff.lead.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import sb.broker.bff.web.StrictLongDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;

/**
 * Заявка на лид из кабинета. ИНН и email берутся из допуска, а не из тела.
 * Поля полиса есть только у продления; выбранные продукты передаются списком.
 */
@Schema(description = "Заявка на лид")
public record AccountLeadRequestDTO(
        @Schema(description = "ФИО контакта", example = "Иванов Иван Иванович",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        //@NotBlank(message = "{error.invalid-request}")
        String fio,
        @Schema(description = "Телефон", example = "+79990000000",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        //@NotBlank(message = "{error.invalid-request}")
        String phone,
        @Schema(description = "Комментарий; пустая строка передаётся как пустой комментарий",
                example = "Нужна консультация по договорам", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String comment,
        @Schema(description = "Сценарий лида: CONTACT_BROKER, POLICY_PROLONGATION, PRODUCT_CATALOG или RECOMMENDATION", example = "CONTACT_BROKER")
        @NotBlank(message = "{error.invalid-request}") String subtype,
        @Schema(description = "Идентификаторы продуктов; прежнее поле productId с одним числом также поддерживается", example = "[10]",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonAlias("productId")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        ArrayList<Long> productIds,
        @Schema(description = "Номер полиса; обязателен и непуст при POLICY_PROLONGATION", example = "КАСКО-2026/014",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String policyNumber,
        @Schema(description = "Группа страхования полиса; только продление, пустая строка допустима", example = "транспорт",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String insuranceGroup,
        @Schema(description = "Страховая компания полиса; только продление, пустая строка допустима", example = "СК Пример",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String insuranceCompany
) {

    /** Передано ли хоть одно поле продукта или полиса. */
    public boolean hasPolicyFields() {
        return policyNumber != null || insuranceGroup != null || insuranceCompany != null;
    }
}
