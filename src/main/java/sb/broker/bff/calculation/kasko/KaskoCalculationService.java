package sb.broker.bff.calculation.kasko;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import sb.broker.bff.calculation.AccountCalculationService;
import sb.broker.bff.calculation.kasko.dto.CreateAccountKaskoPreviewRequestDTO;
import sb.broker.bff.calculation.kasko.dto.CreateAccountKaskoVehicleDTO;
import sb.broker.bff.calculation.kasko.dto.KaskoPreviewOfferDTO;
import sb.broker.bff.calculation.kasko.dto.KaskoPreviewResponseDTO;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.workflow.api.calculation.AccountContacts;
import sb.broker.bff.workflow.api.calculation.AccountKaskoContract;
import sb.broker.bff.workflow.api.calculation.AccountKaskoInsurant;
import sb.broker.bff.workflow.api.calculation.AccountKaskoPreviewRequest;
import sb.broker.bff.workflow.api.calculation.AccountKaskoPreviousContract;
import sb.broker.bff.workflow.api.calculation.AccountKaskoVehicle;
import sb.broker.bff.workflow.api.calculation.AccountVehicleDocument;
import sb.broker.bff.workflow.api.calculation.InitiateAccountKaskoCalculationInput;
import sb.broker.bff.workflow.api.calculation.KaskoPreviewResult;

/**
 * Предрасчёт КАСКО кабинета: тело HTTP → вход workflow, предложения — без пересчёта полей.
 */
@Service
@RequiredArgsConstructor
public class KaskoCalculationService {

    private final AccountCalculationService calculations;

    /**
     * @return идентификатор workflow; пусто — «ворота» не опубликованы за время ожидания
     */
    public Optional<String> preview(CreateAccountKaskoPreviewRequestDTO request) {
        return calculations.start(LkWorkflows.INITIATE_ACCOUNT_KASKO_CALCULATION,
                (email, requestId) -> new InitiateAccountKaskoCalculationInput(email, requestId, toWorkflow(request)));
    }

    public SseEmitter stream(String workflowId) {
        return calculations.stream(LkWorkflows.INITIATE_ACCOUNT_KASKO_CALCULATION, workflowId,
                KaskoPreviewResult::gate, KaskoCalculationService::toResponse);
    }

    private static KaskoPreviewResponseDTO toResponse(KaskoPreviewResult result) {
        return new KaskoPreviewResponseDTO(result.calculationId(), result.offers() == null ? List.of()
                : result.offers().stream()
                .map(offer -> new KaskoPreviewOfferDTO(offer.offerId(), offer.status(), offer.errorMessage(),
                        offer.insuranceCompanyId(), offer.totalCost()))
                .toList());
    }

    private static AccountKaskoPreviewRequest toWorkflow(CreateAccountKaskoPreviewRequestDTO request) {
        CreateAccountKaskoVehicleDTO vehicle = request.vehicle();
        var insurant = request.insurant();
        var contract = request.insuranceContract();
        var previous = request.previousContract();
        return new AccountKaskoPreviewRequest(
                new AccountKaskoInsurant(insurant.companyType(), insurant.inn(), insurant.regionCode(),
                        new AccountContacts(insurant.contacts().surname(), insurant.contacts().name(),
                                insurant.contacts().phone()),
                        insurant.product(), insurant.gosb()),
                new AccountKaskoVehicle(vehicle.vin(), vehicle.registrationNumber(), vehicle.category(), vehicle.markId(),
                        vehicle.mark(), vehicle.modelId(), vehicle.model(), vehicle.markPrint(), vehicle.modelPrint(),
                        vehicle.manufactureYear(), vehicle.enginePower(), vehicle.mileage(), vehicle.color(),
                        vehicle.isNew(), vehicle.maxMass(), vehicle.passengerSeatsCount(),
                        new AccountVehicleDocument(vehicle.document().typeGuid(), null, vehicle.document().number(),
                                vehicle.document().issueDate()),
                        vehicle.isNotPersonal(), vehicle.region(), vehicle.usePurpose()),
                new AccountKaskoContract(contract.startDate(), contract.endDate(), contract.insuranceCost()),
                previous == null ? null : new AccountKaskoPreviousContract(previous.insuranceCompanyName(),
                        previous.contractNumber(), previous.contractEnd(), previous.insuranceCost(),
                        previous.insurancePremium(), previous.contractFranchise()));
    }
}
