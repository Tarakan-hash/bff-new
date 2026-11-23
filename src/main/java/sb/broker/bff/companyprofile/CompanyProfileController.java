package sb.broker.bff.companyprofile;

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
import sb.broker.bff.companyprofile.dto.CompanyProfileAcceptedDTO;
import sb.broker.bff.companyprofile.dto.CompanyProfileErrorDTO;
import sb.broker.bff.companyprofile.dto.CompanyProfileResponseDTO;
import sb.broker.bff.web.ErrorType;
import sb.broker.bff.web.ServiceException;
import sb.broker.bff.web.openapi.ConsentsRequired;
import sb.broker.bff.web.openapi.ErrorExample;

@Tag(name = "Карточка компании")
@RestController
@RequestMapping("/api/v1/company-profile")
@RequiredArgsConstructor
public class CompanyProfileController {

    private final CompanyProfileService companyProfileService;

    @Operation(summary = "Запустить обновление карточки компании", description = "Карточка приходит следующим GET")
    @ApiResponse(responseCode = "202", description = "Обновление запущено")
    @ConsentsRequired
    @PostMapping
    public ResponseEntity<CompanyProfileAcceptedDTO> initiate() {
        return ResponseEntity.accepted().body(new CompanyProfileAcceptedDTO(companyProfileService.initiate()));
    }

    @Operation(summary = "Карточка компании (SSE)", description = """
            Поток text/event-stream с одним событием, затем закрывается. complete — карточка;
            error — {code, message}: SESSION_NOT_FOUND, COMPANY_NOT_FOUND, WORKFLOW_FAILED, INTERNAL_ERROR, INTERNAL_FALLBACK.
            Допуск проверяется до открытия потока: 401, 500 и consents_required — обычный JSON.""")
    @ApiResponse(responseCode = "200", description = "Поток открыт", content = @Content(
            mediaType = "text/event-stream",
            schema = @Schema(oneOf = {CompanyProfileResponseDTO.class, CompanyProfileErrorDTO.class}),
            examples = {
                    @ExampleObject(name = "complete", value = "event:complete\ndata:{\"inn\":\"7706810730\",\"name\":\"ООО «Пример»\"}"),
                    @ExampleObject(name = "error", value = "event:error\ndata:{\"code\":\"COMPANY_NOT_FOUND\","
                            + "\"message\":\"Компания с указанным ИНН не найдена в системе CRM.\"}")}))
    @ErrorExample(status = "400", message = "error.operation-id-missing", when = "workflowId пустой")
    @ConsentsRequired
    @GetMapping("/{workflowId}")
    public SseEmitter stream(@PathVariable @NotBlank(message = "{error.operation-id-missing}") String workflowId) {
        return companyProfileService.stream(workflowId);
    }

    @Operation(summary = "Идентификатор сессии не передан")
    @ErrorExample(status = "400", message = "error.operation-id-missing", when = "workflowId не передан")
    @GetMapping("/")
    public ResponseEntity<Void> streamWithoutId() {
        throw new ServiceException(ErrorType.OPERATION_ID_MISSING);
    }
}
