import io.temporal.activity.Activity;
import io.temporal.activity.DynamicActivity;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.converter.CodecDataConverter;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.common.converter.EncodedValues;
import io.temporal.failure.ApplicationFailure;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.DynamicWorkflow;
import sb.broker.temporal.encryption.AesGcmPayloadCodec;
import sb.broker.temporal.encryption.EncryptionKeyRing;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Фейковые CRM Integration Service, Product Catalog, Company Profile, Risk Scoring, Policy Profile, quotation-service и
 * PolicyImportWorkflow: отвечают настоящими DTO сервисов.
 */
public class FakeServices {

    static final String INN = "7707083893";
    static final java.util.UUID CALCULATION = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    public static void main(String[] args) throws Exception {
        // args: <файл ключа шифрования> <адрес Temporal> <namespace>
        String key = Files.readString(Path.of(args[0])).trim();
        String target = args[1];
        String namespace = args[2];
        var codec = new AesGcmPayloadCodec(EncryptionKeyRing.of("k1", Map.of("k1", key), true), true);
        var stubs = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        var client = WorkflowClient.newInstance(stubs, WorkflowClientOptions.newBuilder().setNamespace(namespace)
                .setDataConverter(new CodecDataConverter(DefaultDataConverter.newDefaultInstance(), List.of(codec), true))
                .build());
        var factory = WorkerFactory.newInstance(client);
        for (String queue : List.of("crm-integration-tq", "product-catalog-tq", "company-profile-tq",
                "risk-scoring-tq", "policy-profile-tq", "quotation-tq", "e2e-company-crm-tq")) {
            factory.newWorker(queue).registerActivitiesImplementations((DynamicActivity) FakeServices::activity);
        }
        Worker importWorker = factory.newWorker("policy-import-queue");
        importWorker.registerWorkflowImplementationFactory(DynamicWorkflow.class, () -> a -> {
            log("workflow PolicyImportWorkflow");
            OffsetDateTime date = OffsetDateTime.parse("2026-01-15T00:00:00+03:00");
            return new sb.broker.policyprofile.model.dto.PolicyImportResultDTO(List.of(new sb.broker.policyprofile.model.dto.DealDTO(
                    "d1", "c", "КАСКО-1", date, date, date.plusYears(1), "ACTIVE", "vehicle", "PAID",
                    new BigDecimal("1500000.00"), "INSURANCE", "x", "СК Пример", new BigDecimal("84500.00"),
                    "транспорт", "новый", "kasko", 4,
                    List.of(new sb.broker.policyprofile.model.dto.DealRiskDTO("Ущерб", new BigDecimal("1500000.00"))))));
        });
        factory.start();
        log("fake services started");
        Thread.currentThread().join();
    }

    static Object activity(EncodedValues args) {
        String type = Activity.getExecutionContext().getInfo().getActivityType();
        String queue = Activity.getExecutionContext().getInfo().getActivityTaskQueue();
        String arg = args.getSize() > 0 ? String.valueOf(args.get(0, Object.class)) : "";
        log("activity " + queue + " " + type + " " + arg);
        OffsetDateTime date = OffsetDateTime.parse("2026-01-15T00:00:00+03:00");
        return switch (type) {
            case "FindCompanyInfo" -> new sb.broker.temporal.dto.dms.CompanyInfoResponse(
                    "e2e-company", INN, "company", "ООО «Пример»", "ООО «Пример»", "123", "Директор", "38", null,
                    "broker@example.com", new sb.broker.temporal.dto.dms.RequisiteDto(null, null, null, null, null, null, "62.01"));
            case "FindRegionCodeByGosbId" -> new sb.broker.model.dto.RegionCodeDto(77, "Москва", "38");
            case "FindCompanyContact" -> List.of();
            case "FindUserInfo" -> null;
            case "FindLkUser" -> arg.startsWith("stranger")
                    ? sb.broker.integration.crm.model.dto.lk.LkUserDTO.notFound()
                    : new sb.broker.integration.crm.model.dto.lk.LkUserDTO(true, INN);
            case "GetUserConsents" -> new sb.broker.integration.crm.model.dto.lk.UserConsentsDTO(List.of(
                    new sb.broker.integration.crm.model.dto.lk.UserConsentDTO("Поручение", !arg.startsWith("noconsent"), true, null),
                    new sb.broker.integration.crm.model.dto.lk.UserConsentDTO("Рассылка", true, false, "2026-10-26T12:00:00+03:00")),
                    !arg.startsWith("noconsent"));
            case "GetConsentFile" -> "ASSIGNMENT".equals(arg)
                    ? sb.broker.integration.crm.model.dto.lk.ConsentFileDTO.notFound()
                    : new sb.broker.integration.crm.model.dto.lk.ConsentFileDTO(true, "%PDF-fake".getBytes(), "application/pdf");
            case "SubmitUserConsents", "RecallUserConsents", "SavePolicies" -> null;
            case "UpdateAccountLead" -> sb.broker.integration.crm.model.dto.lk.AccountLeadOutcome.ACCEPTED;
            // продукт 999 в каталоге нет; остальные заявки — открытый лид, который обновляется
            case "CreateAccountLead" -> Long.valueOf(999L).equals(args.get(0,
                    sb.broker.integration.crm.model.dto.lk.AccountLeadCommandDTO.class).productId())
                    ? sb.broker.integration.crm.model.dto.lk.AccountLeadOutcome.PRODUCT_NOT_FOUND
                    : sb.broker.integration.crm.model.dto.lk.AccountLeadOutcome.OPEN_LEAD_EXISTS;
            case "Query" -> {
                var query = args.get(0, sb.broker.quotation.productcatalog.temporal.dto.CatalogQueryDTO.class);
                if ("CITY".equals(query.dictionary()) && "0000000000000".equals(query.params().get("kladrId"))) {
                    throw ApplicationFailure.newNonRetryableFailure("City not found", "CatalogEntryNotFound");
                }
                if ("REGION".equals(query.dictionary()) && "9999999999".equals(query.params().get("regionCode"))) {
                    throw ApplicationFailure.newNonRetryableFailure("For input string: 9999999999", "CatalogInvalidQuery");
                }
                yield List.of(new sb.broker.quotation.productcatalog.model.dto.GosbResponseDTO("38"));
            }
            case "FindGosbByRegion" -> new sb.broker.quotation.productcatalog.model.dto.GosbResponseDTO("38");
            case "GetCompanyRegion" -> new sb.broker.companyprofile.temporal.dto.CompanyRegionDTO(77);
            case "SaveCompanyProfile" -> new sb.broker.companyprofile.model.dto.CompanyProfileResponseDTO(INN, "ООО «Пример»",
                    null, null, null, "62.01", "38", 77, null, null, null);
            case "FindFreshRiskProfile" -> new sb.broker.riskscoring.temporal.dto.FreshRiskProfileDTO(
                    new sb.broker.riskscoring.model.dto.RiskProfileDTO(INN, "Пример", "62.01", "2026-09-30T12:00:00+03:00",
                            List.of(new sb.broker.riskscoring.model.dto.RiskGroupDTO("property", "Имущество", List.of())),
                            new sb.broker.riskscoring.model.dto.RecommendationsDTO(List.of(),
                                    List.of(new sb.broker.riskscoring.model.dto.ServiceRecommendationDTO(701, "Аудит", "Проверка",
                                            new sb.broker.riskscoring.model.dto.RecommendationGroupDTO(10, "Г", 1))),
                                    List.of())));
            case "FindFreshPolicies" -> new sb.broker.policyprofile.temporal.dto.FreshPoliciesDTO(false, List.of());
            case "GetPolicies" -> List.of(new sb.broker.policyprofile.model.dto.PolicyDTO("транспорт", "КАСКО-1", "СК Пример",
                    new BigDecimal("84500.00"), date, date.plusYears(1), date.minusDays(5), 112L, 3L, "новый", 4,
                    new BigDecimal("1500000.00"),
                    List.of(new sb.broker.policyprofile.model.dto.PolicyRiskDTO("Ущерб", new BigDecimal("1500000.00")))));
            // предрасчёты: ДМС — холодная копия (дочерний расчёт ходит во внешний DOT), КАСКО — котировка,
            // ОСАГО — без котировок (интеграции нет)
            case "PrepareDmsCalculation" -> new sb.broker.quotation.temporal.dto.account.DmsCalculationDTO(CALCULATION, true,
                    List.of(new sb.broker.quotation.temporal.dto.account.DmsCalculationOfferDTO("o-1", "success", 1L,
                                    "7700000000000", new BigDecimal("150000.50"), 12, List.of("clinic")),
                            new sb.broker.quotation.temporal.dto.account.DmsCalculationOfferDTO("o-2", "city_not_supported",
                                    2L, "7700000000000", null, null, List.of())),
                    List.of(new sb.broker.quotation.temporal.dto.account.DmsEmployeeGroupDTO(10, 1L, "7700000000000")));
            case "PrepareKaskoCalculation" -> new sb.broker.quotation.temporal.dto.account.KaskoCalculationDTO(CALCULATION, false, List.of());
            case "CompleteKaskoCalculation" -> new sb.broker.quotation.temporal.dto.account.KaskoCalculationDTO(CALCULATION, false,
                    List.of(new sb.broker.quotation.temporal.dto.account.QuoteOfferDTO("k-1", "success", null, 7L,
                            new BigDecimal("84500.50"))));
            case "PrepareOsagoCalculation", "CompleteOsagoCalculation" ->
                    new sb.broker.quotation.temporal.dto.account.OsagoCalculationDTO(CALCULATION, false, List.of());
            case "FailCalculation", "CreateQuotationLead", "UpdateQuotationLead" -> null;
            default -> throw ApplicationFailure.newNonRetryableFailure("Unexpected activity " + type, "Unexpected");
        };
    }

    static void log(String line) {
        System.out.println("[fake] " + line);
    }
}
