package sb.broker.bff.lead;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sb.broker.bff.lead.dto.AccountLeadRequestDTO;
import sb.broker.bff.web.Messages;
import sb.broker.bff.web.Outcome;
import sb.broker.bff.web.dto.OutcomeDTO;
import sb.broker.bff.web.openapi.ConsentsRequired;
import sb.broker.bff.web.openapi.ErrorExample;
import sb.broker.bff.web.openapi.OutcomeExample;

@Tag(name = "Лиды")
@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;
    private final Messages messages;

    @Operation(summary = "Заявка на лид", description = "Создание и обновление открытого лида для фронта одинаковы")
    @ApiResponse(responseCode = "204", description = "Команда не завершилась за время ожидания; повторный POST — новый прогон")
    @OutcomeExample(Outcome.ACCEPTED)
    @OutcomeExample(Outcome.REGION_NOT_FOUND)
    @OutcomeExample(Outcome.GOSB_NOT_FOUND)
    @OutcomeExample(Outcome.PRODUCT_NOT_FOUND)
    @ErrorExample(status = "400", message = "error.invalid-request",
            when = "Тело не разобрано или нет subtype; у продления нет policyNumber")
    @ErrorExample(status = "422", message = "error.lead-scenario-unavailable",
            when = "Сценарий закрыт, переданы запрещённые поля полиса, у продления нет продукта или productIds содержит null")
    @ConsentsRequired
    @PostMapping
    public ResponseEntity<OutcomeDTO> submit(@Valid @RequestBody AccountLeadRequestDTO request) {
        return leadService.submit(request)
                .map(outcome -> ResponseEntity.status(outcome.getStatus()).body(messages.outcome(outcome)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
