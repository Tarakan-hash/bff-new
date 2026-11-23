package sb.broker.bff.calculation.kasko.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import sb.broker.bff.web.StrictIntegerDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;

@Schema(description = "Транспортное средство КАСКО")
public record CreateAccountKaskoVehicleDTO(
        @Schema(example = "XTA00000000000001") @NotNull(message = "{error.preview-request-invalid}") @Size(min = 17, max = 17, message = "{error.preview-request-invalid}") String vin,
        @Schema(example = "А000АА77") String registrationNumber,
        @Schema(description = "UUID категории ТС") @NotNull(message = "{error.preview-request-invalid}") @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", message = "{error.preview-request-invalid}") String category,
        @NotNull(message = "{error.preview-request-invalid}") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer markId,
        @Schema(example = "Lada") @NotBlank(message = "{error.preview-request-invalid}") String mark,
        @NotNull(message = "{error.preview-request-invalid}") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer modelId,
        @Schema(example = "Vesta") @NotBlank(message = "{error.preview-request-invalid}") String model,
        String markPrint,
        String modelPrint,
        @Schema(example = "2024") @NotNull(message = "{error.preview-request-invalid}") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer manufactureYear,
        @Schema(example = "106") @NotNull(message = "{error.preview-request-invalid}") BigDecimal enginePower,
        @Schema(example = "1000") @NotNull(message = "{error.preview-request-invalid}") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer mileage,
        String color,
        Boolean isNew,
        @Schema(description = "Для категории C") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer maxMass,
        @Schema(description = "Для категории D") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer passengerSeatsCount,
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountKaskoVehicleDocumentDTO document,
        @Schema(description = "Без поля — false") Boolean isNotPersonal,
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer region,
        @Schema(description = "UUID цели использования") @NotNull(message = "{error.preview-request-invalid}") @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", message = "{error.preview-request-invalid}") String usePurpose
) {
}
