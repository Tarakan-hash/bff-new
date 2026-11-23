package sb.broker.bff.consent;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sb.broker.bff.consent.dto.ConsentDecisionDTO;
import sb.broker.bff.consent.dto.ConsentSubmissionDTO;
import sb.broker.bff.consent.dto.UserConsentDTO;
import sb.broker.bff.web.openapi.ConsentsRequired;
import sb.broker.bff.web.openapi.ErrorExample;
import sb.broker.bff.workflow.api.consent.ConsentType;

@Tag(name = "Согласия")
@RestController
@RequestMapping("/api/v1/consents")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    @Operation(summary = "Текущие согласия пользователя",
            description = "Отсутствие обязательного согласия метод не закрывает: по ответу фронт показывает поп-ап")
    @ApiResponse(responseCode = "200", description = "Согласия CRM")
    @GetMapping
    public List<UserConsentDTO> consents() {
        return consentService.consents();
    }

    @Operation(summary = "Файл согласия", description = "Доступен без обязательного согласия: нужен в поп-апе")
    @ApiResponse(responseCode = "200", description = "Файл; Content-Type из CRM или application/octet-stream",
            content = @Content(mediaType = "application/octet-stream"))
    @ErrorExample(status = "400", message = "error.invalid-consent-type", when = "Неизвестный тип согласия. Допустимы: PDN, CSI, ASSIGNMENT, NEWSLETTER")
    @ErrorExample(status = "404", message = "error.consent-file-not-found", when = "Файла нет")
    @GetMapping("/{type}/file")
    public ResponseEntity<byte[]> file(@PathVariable ConsentType type) {
        ConsentFile file = consentService.file(type);
        return ResponseEntity.ok().contentType(file.contentType()).body(file.content());
    }

    @Operation(summary = "Передать в CRM отмеченные согласия",
            description = "Доступен без обязательного согласия. Принимает массив решений или объект с массивом в поле data. В CRM уходят только consent = true")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            schema = @Schema(oneOf = {ConsentDecisionDTO[].class, ConsentSubmissionDTO.class})))
    @ApiResponse(responseCode = "200", description = "CRM приняла факты; тела нет")
    @ErrorExample(status = "400", message = "error.pdn-consent-required", when = "У PDN нет consent = true")
    @ErrorExample(status = "400", message = "error.invalid-consent-decisions", when = "Тип не указан или повторён")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Тело не разобрано")
    @PostMapping
    public ResponseEntity<Void> submit(@Valid @RequestBody ConsentSubmissionDTO submission) {
        consentService.submit(submission.data());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Отозвать согласие", description = "Отзыв уже отозванного согласия — тоже 200")
    @ApiResponse(responseCode = "200", description = "Согласие отозвано; тела нет")
    @ErrorExample(status = "400", message = "error.invalid-consent-type", when = "Неизвестный тип согласия. Допустимы: PDN, CSI, ASSIGNMENT, NEWSLETTER")
    @ConsentsRequired
    @PostMapping("/{type}/recall")
    public ResponseEntity<Void> recall(@PathVariable ConsentType type) {
        consentService.recall(type);
        return ResponseEntity.ok().build();
    }
}
