package sb.broker.bff.riskprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Группа риска")
public record RiskGroupDTO(
        @Schema(description = "Код группы", example = "property") String code,
        @Schema(example = "Имущество") String name,
        @Schema(description = "Пустой, если рисков нет") List<RiskDTO> risks
) {
}
