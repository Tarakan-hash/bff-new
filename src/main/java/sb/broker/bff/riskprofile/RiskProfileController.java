package sb.broker.bff.riskprofile;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import sb.broker.bff.riskprofile.dto.RiskProfileAcceptedDTO;
import sb.broker.bff.riskprofile.dto.RiskProfileDTO;
import sb.broker.bff.web.ErrorType;
import sb.broker.bff.web.ServiceException;
import sb.broker.bff.web.dto.ErrorDTO;
import sb.broker.bff.web.openapi.ConsentsRequired;
import sb.broker.bff.web.openapi.ErrorExample;

@Tag(name = "Риск-профиль")
@RestController
@RequestMapping("/api/v1/risk-profile-recommendations")
@RequiredArgsConstructor
public class RiskProfileController {

    private final RiskProfileService riskProfileService;

    @Operation(summary = "Запустить расчёт риск-профиля и рекомендаций",
            description = "Пока расчёт этого ИНН идёт, повтор возвращает тот же workflowId. Профиль — следующим GET")
    @ApiResponse(responseCode = "202", description = "Расчёт запущен или уже идёт")
    @ConsentsRequired
    @PostMapping
    public ResponseEntity<RiskProfileAcceptedDTO> initiate() {
        return ResponseEntity.accepted().body(new RiskProfileAcceptedDTO(riskProfileService.initiate()));
    }

    @Operation(summary = "Риск-профиль и рекомендации (SSE)", description = """
            Поток text/event-stream с одним событием, затем закрывается. complete — профиль;
            error — {message}: текст исхода расчёта или «Внутренняя ошибка сервиса».
            Допуск проверяется до открытия потока: 401, 500 и consents_required — обычный JSON.""")
    @ApiResponse(responseCode = "200", description = "Поток открыт", content = @Content(
            mediaType = "text/event-stream",
            schema = @Schema(oneOf = {RiskProfileDTO.class, ErrorDTO.class}),
            examples = {
                    @ExampleObject(name = "complete", value = "event:complete\ndata:{\"inn\":\"7707083893\",\"companyName\":\"Пример\"}"),
                    @ExampleObject(name = "error", value = "event:error\ndata:{\"message\":\"Нет риск-профиля для отображения\"}")}))
    @ErrorExample(status = "400", message = "error.operation-id-missing", when = "workflowId пустой")
    @ConsentsRequired
    @GetMapping("/{workflowId}")
    public SseEmitter stream(@PathVariable @NotBlank(message = "{error.operation-id-missing}") String workflowId) {
        return riskProfileService.stream(workflowId);
    }

    @Operation(summary = "Идентификатор сессии не передан")
    @ErrorExample(status = "400", message = "error.operation-id-missing", when = "workflowId не передан")
    @GetMapping("/")
    public ResponseEntity<Void> streamWithoutId() {
        throw new ServiceException(ErrorType.OPERATION_ID_MISSING);
    }
}
