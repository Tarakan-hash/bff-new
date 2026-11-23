package sb.broker.bff.calculation.osago;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import sb.broker.bff.calculation.dto.AcceptedPreviewDTO;
import sb.broker.bff.calculation.osago.dto.CreateAccountOsagoPreviewRequestDTO;
import sb.broker.bff.calculation.osago.dto.CreateAccountOsagoSpecialVehicleRequestDTO;
import sb.broker.bff.calculation.osago.dto.OsagoPreviewResponseDTO;
import sb.broker.bff.web.ErrorType;
import sb.broker.bff.web.Outcome;
import sb.broker.bff.web.ServiceException;
import sb.broker.bff.web.dto.ErrorDTO;
import sb.broker.bff.web.openapi.ConsentsRequired;
import sb.broker.bff.web.openapi.ErrorExample;
import sb.broker.bff.web.openapi.OutcomeExample;

@Tag(name = "Предрасчёты")
@RestController
@RequestMapping("/api/v1/calculation/osago")
@RequiredArgsConstructor
public class OsagoCalculationController {

    private final OsagoCalculationService calculations;

    @Operation(summary = "Создать предрасчёт ОСАГО", description = """
            Запускает workflow и ждёт «ворота» (допуск, расчёт или холодная копия, лид) не дольше 60 секунд.
            Предложения — следующим GET. Повтор после 204 запускает новый расчёт.""")
    @ApiResponse(responseCode = "202", description = "Расчёт идёт или найдена холодная копия")
    @ApiResponse(responseCode = "204", description = "За 60 секунд ворота не опубликованы")
    @OutcomeExample(Outcome.PREVIEW_GOSB_NOT_FOUND)
    @ErrorExample(status = "400", message = "error.preview-request-invalid", when = "Тело не принято, переданы email или consents")
    @ConsentsRequired
    @PostMapping
    public ResponseEntity<AcceptedPreviewDTO> preview(@Valid @RequestBody CreateAccountOsagoPreviewRequestDTO request) {
        return calculations.preview(request)
                .map(workflowId -> ResponseEntity.accepted().body(new AcceptedPreviewDTO(workflowId)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Создать предрасчёт ОСАГО спецтехники", description = """
            Тот же workflow и GET, что у обычного ОСАГО. Ждёт «ворота» не дольше 60 секунд.""")
    @ApiResponse(responseCode = "202", description = "Расчёт идёт или найдена холодная копия")
    @ApiResponse(responseCode = "204", description = "За 60 секунд ворота не опубликованы")
    @OutcomeExample(Outcome.PREVIEW_GOSB_NOT_FOUND)
    @ErrorExample(status = "400", message = "error.preview-request-invalid", when = "Тело не принято, переданы email или consents")
    @ConsentsRequired
    @PostMapping("/special-vehicle")
    public ResponseEntity<AcceptedPreviewDTO> previewSpecialVehicle(@Valid @RequestBody CreateAccountOsagoSpecialVehicleRequestDTO request) {
        return calculations.preview(request)
                .map(workflowId -> ResponseEntity.accepted().body(new AcceptedPreviewDTO(workflowId)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Предрасчёт ОСАГО (SSE)", description = """
            Поток text/event-stream с одним событием для обычного ОСАГО и спецтехники. complete — предложения без пересчёта
            полей; error — «Расчёт не найден» или «Внутренняя ошибка сервиса».""")
    @ApiResponse(responseCode = "200", description = "Поток открыт", content = @Content(
            mediaType = "text/event-stream",
            schema = @Schema(oneOf = {OsagoPreviewResponseDTO.class, ErrorDTO.class}),
            examples = {
                    @ExampleObject(name = "complete", value = "event:complete\ndata:{\"calculationId\":\"calc-1\",\"offers\":[]}"),
                    @ExampleObject(name = "error", value = "event:error\ndata:{\"message\":\"Расчёт не найден\"}")}))
    @ErrorExample(status = "400", message = "error.preview-id-missing", when = "workflowId пустой")
    @ConsentsRequired
    @GetMapping("/{workflowId}")
    public SseEmitter stream(@PathVariable @NotBlank(message = "{error.preview-id-missing}") String workflowId) {
        return calculations.stream(workflowId);
    }

    @Operation(summary = "Идентификатор предрасчёта не передан")
    @ErrorExample(status = "400", message = "error.preview-id-missing", when = "workflowId не передан")
    @GetMapping("/")
    public ResponseEntity<Void> streamWithoutId() {
        throw new ServiceException(ErrorType.PREVIEW_ID_MISSING);
    }
}
