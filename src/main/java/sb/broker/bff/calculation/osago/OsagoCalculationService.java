package sb.broker.bff.calculation.osago;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sb.broker.bff.calculation.AccountCalculationService;
import sb.broker.bff.calculation.osago.dto.CreateAccountOsagoCompanyDTO;
import sb.broker.bff.calculation.osago.dto.CreateAccountOsagoEntrepreneurDTO;
import sb.broker.bff.calculation.osago.dto.CreateAccountOsagoIdentityDocumentDTO;
import sb.broker.bff.calculation.osago.dto.CreateAccountOsagoInsuranceContractDTO;
import sb.broker.bff.calculation.osago.dto.CreateAccountOsagoOwnerDTO;
import sb.broker.bff.calculation.osago.dto.CreateAccountOsagoPersonDTO;
import sb.broker.bff.calculation.osago.dto.CreateAccountOsagoPreviewRequestDTO;
import sb.broker.bff.calculation.osago.dto.CreateAccountOsagoPreviousContractDTO;
import sb.broker.bff.calculation.osago.dto.CreateAccountOsagoRepresentativeDTO;
import sb.broker.bff.calculation.osago.dto.CreateAccountOsagoSpecialVehicleRequestDTO;
import sb.broker.bff.calculation.osago.dto.CreateAccountOsagoVehicleDocumentDTO;
import sb.broker.bff.calculation.osago.dto.OsagoPreviewOfferDTO;
import sb.broker.bff.calculation.osago.dto.OsagoPreviewResponseDTO;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.workflow.api.calculation.AccountContacts;
import sb.broker.bff.workflow.api.calculation.AccountOsagoCompany;
import sb.broker.bff.workflow.api.calculation.AccountOsagoContract;
import sb.broker.bff.workflow.api.calculation.AccountOsagoEntrepreneur;
import sb.broker.bff.workflow.api.calculation.AccountOsagoParty;
import sb.broker.bff.workflow.api.calculation.AccountOsagoPerson;
import sb.broker.bff.workflow.api.calculation.AccountOsagoPreviewRequest;
import sb.broker.bff.workflow.api.calculation.AccountOsagoPreviousContract;
import sb.broker.bff.workflow.api.calculation.AccountOsagoRepresentative;
import sb.broker.bff.workflow.api.calculation.AccountOsagoVehicle;
import sb.broker.bff.workflow.api.calculation.AccountVehicleDocument;
import sb.broker.bff.workflow.api.calculation.InitiateAccountOsagoCalculationInput;
import sb.broker.bff.workflow.api.calculation.OsagoPreviewResult;
import sb.broker.bff.workflow.api.calculation.VehicleKind;

import java.util.List;
import java.util.Optional;

/**
 * Предрасчёт ОСАГО кабинета: обычное ОСАГО и спецтехника идут одним workflow, вид ТС задаёт путь метода.
 */
@Service
@RequiredArgsConstructor
public class OsagoCalculationService {

    private final AccountCalculationService calculations;

    public Optional<String> preview(CreateAccountOsagoPreviewRequestDTO request) {
        AccountOsagoPreviewRequest body = new AccountOsagoPreviewRequest(representative(request.representative()),
                party(request.insurant().company(), request.insurant().entrepreneur(), null),
                request.vehicles().stream()
                        .map(vehicle -> new AccountOsagoVehicle(vehicle.vin(), vehicle.registrationNumber(), null,
                                vehicle.category(), vehicle.markId(), vehicle.mark(), vehicle.modelId(), vehicle.model(),
                                vehicle.markPrint(), vehicle.modelPrint(), vehicle.manufactureYear(), vehicle.enginePower(),
                                vehicle.color(), vehicle.isNew(), vehicle.maxMass(), vehicle.passengerSeatsCount(),
                                document(vehicle.document()), vehicle.region(), vehicle.usePurpose(),
                                vehicle.isTrailerAllowed(), owner(vehicle.owner()), vehicle.ownerIsInsurant(),
                                previous(vehicle.previousContract())))
                        .toList(),
                contract(request.insuranceContract()));
        return start(VehicleKind.ORDINARY, body);
    }

    public Optional<String> preview(CreateAccountOsagoSpecialVehicleRequestDTO request) {
        AccountOsagoPreviewRequest body = new AccountOsagoPreviewRequest(representative(request.representative()),
                party(request.insurant().company(), request.insurant().entrepreneur(), null),
                request.vehicles().stream()
                        .map(vehicle -> new AccountOsagoVehicle(vehicle.vin(), vehicle.registrationNumber(),
                                vehicle.chassisNumber(), vehicle.category(), vehicle.markId(), vehicle.mark(),
                                vehicle.modelId(), vehicle.model(), vehicle.markPrint(), vehicle.modelPrint(),
                                vehicle.manufactureYear(), vehicle.enginePower(), null, null, null, null,
                                document(vehicle.document()), null, vehicle.usePurpose(), null, owner(vehicle.owner()),
                                vehicle.ownerIsInsurant(), previous(vehicle.previousContract())))
                        .toList(),
                contract(request.insuranceContract()));
        return start(VehicleKind.SPECIAL, body);
    }

    /**
     * Один GET для обычного ОСАГО и спецтехники.
     */
    public SseEmitter stream(String workflowId) {
        return calculations.stream(LkWorkflows.INITIATE_ACCOUNT_OSAGO_CALCULATION, workflowId,
                OsagoPreviewResult::gate, OsagoCalculationService::toResponse);
    }

    private Optional<String> start(VehicleKind kind, AccountOsagoPreviewRequest body) {
        return calculations.start(LkWorkflows.INITIATE_ACCOUNT_OSAGO_CALCULATION,
                (email, requestId) -> new InitiateAccountOsagoCalculationInput(email, requestId, kind, body));
    }

    private static OsagoPreviewResponseDTO toResponse(OsagoPreviewResult result) {
        return new OsagoPreviewResponseDTO(result.calculationId(), result.offers() == null ? List.of()
                : result.offers().stream()
                .map(offer -> new OsagoPreviewOfferDTO(offer.offerId(), offer.status(), offer.errorMessage(),
                        offer.insuranceCompanyId(), offer.totalCost()))
                .toList());
    }

    private static AccountOsagoRepresentative representative(CreateAccountOsagoRepresentativeDTO representative) {
        return new AccountOsagoRepresentative(representative.regionCode(), new AccountContacts(
                representative.contacts().surname(), representative.contacts().name(), representative.contacts().phone()));
    }

    private static AccountOsagoParty owner(CreateAccountOsagoOwnerDTO owner) {
        return party(owner.company(), owner.entrepreneur(), owner.person());
    }

    private static AccountOsagoParty party(CreateAccountOsagoCompanyDTO company, CreateAccountOsagoEntrepreneurDTO entrepreneur,
                                           CreateAccountOsagoPersonDTO person) {
        return new AccountOsagoParty(
                company == null ? null : new AccountOsagoCompany(company.inn(), company.contacts().phone(),
                        company.shortName(), company.fullName(), company.ogrn(), company.kpp(), company.opf(),
                        company.address(), identity(company.identityDocument())),
                entrepreneur == null ? null : new AccountOsagoEntrepreneur(entrepreneur.inn(),
                        entrepreneur.contacts().phone(), entrepreneur.shortName(), entrepreneur.fullName(),
                        entrepreneur.surname(), entrepreneur.name(), entrepreneur.patronymic(), entrepreneur.birthDate(),
                        entrepreneur.address(), identity(entrepreneur.identityDocument())),
                person == null ? null : new AccountOsagoPerson(person.contacts().phone(), person.surname(),
                        person.name(), person.patronymic(), person.birthDate(), person.address(),
                        identity(person.identityDocument())));
    }

    private static AccountVehicleDocument identity(CreateAccountOsagoIdentityDocumentDTO document) {
        return new AccountVehicleDocument(document.typeGuid(), document.series(), document.number(), document.issueDate());
    }

    private static AccountVehicleDocument document(CreateAccountOsagoVehicleDocumentDTO document) {
        return new AccountVehicleDocument(document.typeGuid(), document.series(), document.number(), document.issueDate());
    }

    private static AccountOsagoPreviousContract previous(CreateAccountOsagoPreviousContractDTO previous) {
        return previous == null ? null : new AccountOsagoPreviousContract(previous.insuranceCompanyName(),
                previous.contractNumber(), previous.contractEnd(), previous.insurancePremium(),
                previous.contractFranchise());
    }

    private static AccountOsagoContract contract(CreateAccountOsagoInsuranceContractDTO contract) {
        return new AccountOsagoContract(contract.startDateOsago(), contract.endDateOsago());
    }
}
