package sb.broker.bff.calculation;

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
import sb.broker.bff.calculation.dto.SelectAccountOfferRequestDTO;
import sb.broker.bff.web.openapi.ConsentsRequired;
import sb.broker.bff.web.openapi.ErrorExample;


@Tag(name = "Предрасчёты")
@RestController
@RequestMapping("/api/v1/calculation/offer")
@RequiredArgsConstructor
public class CalculationOfferController {

    private final AccountCalculationService calculations;

    @Operation(summary = "Выбрать предложение предрасчёта", description = """
            Записывает выбор в лид предрасчёта. Новый расчёт не запускается. Повтор того же предложения второе событие
            CRM не пишет; другое предложение в течение 24 часов заменяет комментарий лида.""")
    @ApiResponse(responseCode = "200", description = "Выбор записан")
    @ApiResponse(responseCode = "204", description = "За 60 секунд итога нет или предложения ещё не готовы; фронт повторяет запрос")
    @ErrorExample(status = "400", message = "error.calculation-operation-id-missing", when = "workflowId не передан")
    @ErrorExample(status = "400", message = "error.offer-id-missing", when = "offerId не передан")
    @ErrorExample(status = "400", message = "error.offer-not-found", when = "Предложения нет в расчёте")
    @ErrorExample(status = "400", message = "error.offer-not-selectable", when = "Предложение не success")
    @ErrorExample(status = "404", message = "error.offer-selection-unavailable",
            when = "Расчёт не найден, чужой, закрыт или завершился отказом до предложений")
    @ConsentsRequired
    @PostMapping
    public ResponseEntity<Void> select(@Valid @RequestBody SelectAccountOfferRequestDTO request) {
        return calculations.select(request.workflowId(), request.offerId())
                .map(accepted -> ResponseEntity.ok().<Void>build())
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
