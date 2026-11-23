package sb.broker.bff.calculation.osago.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import sb.broker.bff.web.StrictIntegerDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;

@Schema(description = "Транспортное средство ОСАГО")
public record CreateAccountOsagoVehicleDTO(
        @Schema(example = "XTA00000000000001") @NotNull(message = "{error.preview-request-invalid}") @Size(min = 17, max = 17, message = "{error.preview-request-invalid}") String vin,
        String registrationNumber,
        @NotNull(message = "{error.preview-request-invalid}") @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", message = "{error.preview-request-invalid}") String category,
        @NotNull(message = "{error.preview-request-invalid}") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer markId,
        @NotBlank(message = "{error.preview-request-invalid}") String mark,
        @NotNull(message = "{error.preview-request-invalid}") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer modelId,
        @NotBlank(message = "{error.preview-request-invalid}") String model,
        String markPrint,
        String modelPrint,
        @NotNull(message = "{error.preview-request-invalid}") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer manufactureYear,
        @NotNull(message = "{error.preview-request-invalid}") BigDecimal enginePower,
        String color,
        Boolean isNew,
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer maxMass,
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer passengerSeatsCount,
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountOsagoVehicleDocumentDTO document,
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer region,
        @NotNull(message = "{error.preview-request-invalid}") @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", message = "{error.preview-request-invalid}") String usePurpose,
        Boolean isTrailerAllowed,
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountOsagoOwnerDTO owner,
        @NotNull(message = "{error.preview-request-invalid}") Boolean ownerIsInsurant,
        @Valid CreateAccountOsagoPreviousContractDTO previousContract
) {
}
