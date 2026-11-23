package sb.broker.bff.policy;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import sb.broker.bff.policy.dto.AcceptedDTO;
import sb.broker.bff.policy.dto.PolicyDTO;
import sb.broker.bff.web.openapi.ConsentsRequired;

@Tag(name = "Полисы")
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @Operation(summary = "Запустить синхронизацию полисов",
            description = "Свежая копия и уже идущая синхронизация — тоже 202. Список приходит следующим GET")
    @ApiResponse(responseCode = "202", description = "Синхронизация запущена или уже идёт")
    @ConsentsRequired
    @PostMapping
    public ResponseEntity<AcceptedDTO> importPolicies() {
        policyService.importPolicies();
        return ResponseEntity.accepted().body(AcceptedDTO.ACCEPTED);
    }

    @Operation(summary = "Список полисов (SSE)", description = """
            Поток text/event-stream с одним событием, затем закрывается. complete — массив полисов (в том числе пустой);
            error — {message}: «Нет полисов для отображения» или «Внутренняя ошибка сервиса».
            Допуск и запуск синхронизации — до открытия потока: 401, 500 и consents_required — обычный JSON.""")
    @ApiResponse(responseCode = "200", description = "Поток открыт", content = @Content(
            mediaType = "text/event-stream",
            array = @ArraySchema(schema = @Schema(implementation = PolicyDTO.class)),
            examples = {
                    @ExampleObject(name = "complete", value = "event:complete\ndata:[]"),
                    @ExampleObject(name = "error", value = "event:error\ndata:{\"message\":\"Нет полисов для отображения\"}")}))
    @ConsentsRequired
    @GetMapping
    public SseEmitter policies() {
        return policyService.stream();
    }
}
