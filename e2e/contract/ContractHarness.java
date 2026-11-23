import io.temporal.api.common.v1.Payloads;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DefaultDataConverter;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Контракты активити: объект одной стороны -> payload Temporal (конвертер по умолчанию) -> объект другой стороны.
 */
public class ContractHarness {

    static final DataConverter DC = DefaultDataConverter.newDefaultInstance();
    static final boolean VERBOSE = Boolean.getBoolean("verbose");
    static int passed, failed;

    static <T> T transfer(String name, Object source, Class<T> target, Type type) {
        try {
            Optional<Payloads> payloads = DC.toPayloads(source);
            T result = DC.fromPayloads(0, payloads, target, type);
            System.out.println("OK   " + name + (VERBOSE ? "\n       json=" + payloads.get().getPayloads(0).getData().toStringUtf8() : ""));
            passed++;
            return result;
        } catch (Exception e) {
            Throwable root = e; while (root.getCause() != null) root = root.getCause();
            System.out.println("FAIL " + name + ": " + root);
            failed++;
            return null;
        }
    }

    static <T> T transfer(String name, Object source, Class<T> target) {
        return transfer(name, source, target, target);
    }

    static void check(String name, boolean condition) {
        if (condition) { passed++; System.out.println("OK   " + name); }
        else { failed++; System.out.println("FAIL " + name); }
    }

    public static void main(String[] args) throws Exception {
        OffsetDateTime date = OffsetDateTime.parse("2026-01-15T00:00:00+03:00");

        // ===== CRM Integration Service -> WDS (результаты) =====
        var user = transfer("crm LkUserDTO -> WDS LkUserDto",
                new sb.broker.integration.crm.model.dto.lk.LkUserDTO(true, "7707083893"),
                sb.broker.temporal.dto.lk.LkUserDto.class);
        check("  inn preserved", user != null && "7707083893".equals(user.inn()) && user.found());

        var consents = transfer("crm UserConsentsDTO -> WDS UserConsentsDto",
                new sb.broker.integration.crm.model.dto.lk.UserConsentsDTO(List.of(
                        new sb.broker.integration.crm.model.dto.lk.UserConsentDTO("Поручение", false, true, null),
                        new sb.broker.integration.crm.model.dto.lk.UserConsentDTO("Рассылка", true, false, "2026-10-26T12:00:00+03:00")),
                        true),
                sb.broker.temporal.dto.lk.UserConsentsDto.class);
        check("  consents preserved", consents != null && consents.accessAllowed() && consents.consents().size() == 2
                && consents.consents().get(1).date().equals("2026-10-26T12:00:00+03:00") && consents.consents().get(0).required());

        var file = transfer("crm ConsentFileDTO -> WDS ConsentFileDto",
                new sb.broker.integration.crm.model.dto.lk.ConsentFileDTO(true, new byte[]{1, 2, (byte) 255}, "application/pdf"),
                sb.broker.temporal.dto.lk.ConsentFileDto.class);
        check("  bytes preserved", file != null && Arrays.equals(file.content(), new byte[]{1, 2, (byte) 255}));

        transfer("crm AccountLeadOutcome -> WDS AccountLeadOutcome",
                sb.broker.integration.crm.model.dto.lk.AccountLeadOutcome.OPEN_LEAD_EXISTS,
                sb.broker.temporal.dto.lk.AccountLeadOutcome.class);

        // ===== WDS -> CRM Integration Service (входы) =====
        var command = transfer("WDS ConsentCommand -> crm ConsentCommandDTO",
                new sb.broker.temporal.dto.lk.ConsentCommand("user@example.com", List.of("PDN", "CSI")),
                sb.broker.integration.crm.model.dto.lk.ConsentCommandDTO.class);
        check("  types preserved", command != null && command.types().equals(List.of("PDN", "CSI")));

        UUID requestId = UUID.randomUUID();
        var lead = transfer("WDS AccountLeadCommand -> crm AccountLeadCommandDTO",
                new sb.broker.temporal.dto.lk.AccountLeadCommand(requestId, "7707083893", "company", "38",
                        "CONTACT_BROKER", null, "", "Иванов", "+79990000000", "user@example.com", null, null),
                sb.broker.integration.crm.model.dto.lk.AccountLeadCommandDTO.class);
        check("  lead preserved", lead != null && lead.requestId().equals(requestId) && "38".equals(lead.gosb())
                && "".equals(lead.comment()) && lead.product() == null);
        var prolongation = transfer("WDS AccountLeadCommand (продление) -> crm AccountLeadCommandDTO",
                new sb.broker.temporal.dto.lk.AccountLeadCommand(requestId, "7707083893", "company", "38",
                        "POLICY_PROLONGATION", null, "Номер полиса: N-1", "Иванов", "+79990000000", "user@example.com",
                        10L, "N-1"),
                sb.broker.integration.crm.model.dto.lk.AccountLeadCommandDTO.class);
        check("  product and policy preserved", prolongation != null && Long.valueOf(10L).equals(prolongation.productId())
                && "N-1".equals(prolongation.policyNumber()));
        var notFound = transfer("crm AccountLeadOutcome -> WDS AccountLeadOutcome",
                sb.broker.integration.crm.model.dto.lk.AccountLeadOutcome.PRODUCT_NOT_FOUND,
                sb.broker.temporal.dto.lk.AccountLeadOutcome.class);
        check("  product_not_found preserved", notFound == sb.broker.temporal.dto.lk.AccountLeadOutcome.PRODUCT_NOT_FOUND);

        // ===== WDS <-> quotation-service (предрасчёты кабинета) =====
        UUID calculationId = UUID.randomUUID();
        var dmsPrepare = transfer("WDS PrepareDmsCalculationCommand -> quotation PrepareDmsCalculationDTO",
                new sb.broker.temporal.dto.lk.PrepareDmsCalculationCommand("wf-1", new sb.broker.temporal.dto.lk.DmsCalculationRequest(
                        "company", "7707083893", 77, "Иванов", "Иван", "+7", "user@example.com",
                        List.of(new sb.broker.temporal.dto.lk.DmsCalculationEmployee(10, 1L, "7700000000000")), "dms")),
                sb.broker.quotation.temporal.dto.account.PrepareDmsCalculationDTO.class);
        check("  company type, employees, email", dmsPrepare != null
                && dmsPrepare.request().companyType() == sb.broker.quotation.model.enums.CompanyType.COMPANY
                && dmsPrepare.request().employees().getFirst().ageGroupId() == 1
                && "user@example.com".equals(dmsPrepare.request().email()));
        var dms = transfer("quotation DmsCalculationDTO -> WDS DmsCalculation",
                new sb.broker.quotation.temporal.dto.account.DmsCalculationDTO(calculationId, true, List.of(
                        new sb.broker.quotation.temporal.dto.account.DmsCalculationOfferDTO("o-1", "success", 1L,
                                "7700000000000", new BigDecimal("1000.50"), 3, List.of("clinic"))),
                        List.of(new sb.broker.quotation.temporal.dto.account.DmsEmployeeGroupDTO(10, 1L, "7700000000000"))),
                sb.broker.temporal.dto.lk.DmsCalculation.class);
        check("  offers and groups", dms != null && dms.freshCopy() && dms.offers().getFirst().totalCost().scale() == 2
                && dms.employeeGroups().getFirst().count() == 10);
        var complete = transfer("WDS CompleteCalculationCommand -> quotation CompleteCalculationDTO",
                new sb.broker.temporal.dto.lk.CompleteCalculationCommand(calculationId, "dms-preview-x"),
                sb.broker.quotation.temporal.dto.account.CompleteCalculationDTO.class);
        check("  calculation id", complete != null && calculationId.equals(complete.calculationId()));

        var kaskoPrepare = transfer("WDS PrepareKaskoCalculationCommand -> quotation PrepareKaskoCalculationDTO",
                new sb.broker.temporal.dto.lk.PrepareKaskoCalculationCommand("wf-2", new sb.broker.temporal.dto.lk.KaskoCalculationRequest(
                        new sb.broker.temporal.dto.lk.KaskoCalculationInsurant("company", "7707083893", 77,
                                new sb.broker.temporal.dto.lk.CalculationContacts("Иванов", "Иван", "+7", "user@example.com")),
                        new sb.broker.temporal.dto.lk.KaskoCalculationVehicle("XTA00000000000001", null,
                                "6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f11", 1, "Lada", 2, "Vesta", null, null, 2024,
                                new BigDecimal("106"), 1000, null, false, null, null,
                                new sb.broker.temporal.dto.lk.KaskoCalculationDocument("6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f12",
                                        "77УН123456", "2024-05-01T00:00:00+03:00"),
                                null, 77, "6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f13"),
                        new sb.broker.temporal.dto.lk.KaskoCalculationContract("2026-10-08T00:00:00+03:00", null,
                                new BigDecimal("1500000")),
                        new sb.broker.temporal.dto.lk.KaskoCalculationPreviousContract("СК", "N-1",
                                "2026-10-07T00:00:00+03:00", new BigDecimal("1"), new BigDecimal("2"), null),
                        "kasko")),
                sb.broker.quotation.temporal.dto.account.PrepareKaskoCalculationDTO.class);
        check("  UUID, OffsetDateTime, email", kaskoPrepare != null
                && kaskoPrepare.request().vehicle().category() != null
                && kaskoPrepare.request().insuranceContract().startDate().getDayOfMonth() == 8
                && kaskoPrepare.request().previousContract().contractEnd() != null
                && "user@example.com".equals(kaskoPrepare.request().insurant().contacts().email()));
        var kasko = transfer("quotation KaskoCalculationDTO -> WDS KaskoCalculation",
                new sb.broker.quotation.temporal.dto.account.KaskoCalculationDTO(calculationId, false, List.of(
                        new sb.broker.quotation.temporal.dto.account.QuoteOfferDTO("o-1", "error", "нет ответа", 7L, null))),
                sb.broker.temporal.dto.lk.KaskoCalculation.class);
        check("  quote error message", kasko != null && "нет ответа".equals(kasko.offers().getFirst().errorMessage()));

        var document = new sb.broker.temporal.dto.lk.OsagoCalculationDocument("6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f12", "4510",
                "123456", "2020-01-15T00:00:00+03:00");
        var person = new sb.broker.temporal.dto.lk.OsagoCalculationParty(null, null, new sb.broker.temporal.dto.lk.OsagoCalculationPerson(
                new sb.broker.temporal.dto.lk.OsagoCalculationPartyContacts("+7", "user@example.com"), "Петров", "Пётр", null,
                "1980-01-01T00:00:00+03:00", "Москва", document));
        var osagoPrepare = transfer("WDS PrepareOsagoCalculationCommand -> quotation PrepareOsagoCalculationDTO",
                new sb.broker.temporal.dto.lk.PrepareOsagoCalculationCommand("wf-3", new sb.broker.temporal.dto.lk.OsagoCalculationRequest(
                        new sb.broker.temporal.dto.lk.OsagoCalculationRepresentative(77,
                                new sb.broker.temporal.dto.lk.CalculationContacts("Иванов", "Иван", "+7", "user@example.com")),
                        new sb.broker.temporal.dto.lk.OsagoCalculationInsurant(new sb.broker.temporal.dto.lk.OsagoCalculationCompany(
                                "7707083893", new sb.broker.temporal.dto.lk.OsagoCalculationPartyContacts("+7", "user@example.com"),
                                "ООО", "ООО", "1", "2", 12300, "Москва", document), null),
                        List.of(new sb.broker.temporal.dto.lk.OsagoCalculationVehicle("XTA00000000000001", "CH-1", null,
                                "6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f11", 1, "Lada", 2, "Vesta", null, null, 2024,
                                new BigDecimal("106"), null, null, null, null, document, 77,
                                "6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f13", true, person, false, null)),
                        new sb.broker.temporal.dto.lk.OsagoCalculationContract("2026-10-08T00:00:00+03:00",
                                "2027-10-07T00:00:00+03:00"),
                        "osago")),
                sb.broker.quotation.temporal.dto.account.PrepareOsagoCalculationDTO.class);
        check("  trailer flag (isTrailedAllowed), owner person, birth date", osagoPrepare != null
                && Boolean.TRUE.equals(osagoPrepare.request().vehicles().getFirst().isTrailedAllowed())
                && osagoPrepare.request().vehicles().getFirst().owner().person().birthDate() != null
                && "CH-1".equals(osagoPrepare.request().vehicles().getFirst().chassisNumber()));

        // ===== WDS -> CRM Integration Service (лиды предрасчётов) =====
        var osagoComment = new sb.broker.temporal.dto.lk.CommentSource(null, null, null, null,
                List.of(new sb.broker.temporal.dto.lk.CommentOsagoVehicle(false, null, "VIN", "CH-1", 2020, "cat", null,
                        null, "Kia", "Rio", null, new BigDecimal("123.5"), "pts", null, "N2", "2020-01-01", null,
                        "personal", false,
                        new sb.broker.temporal.dto.lk.CommentOsagoOwner("person", null, null, null, "Петров", "Пётр", null,
                                "4510", "123456", "2015-03-03"),
                        new sb.broker.temporal.dto.lk.CommentOsagoPreviousContract("Х", "2026-10-07", new BigDecimal("8000"), null))),
                new sb.broker.temporal.dto.lk.CommentOsagoInsurant("company", null, "123456", "2020-01-15"),
                new sb.broker.temporal.dto.lk.CommentOsagoContract("2026-10-08", "2027-10-07"));
        var quotationLead = transfer("WDS QuotationLeadCommand -> crm QuotationLeadCommandDTO",
                new sb.broker.temporal.dto.lk.QuotationLeadCommand(requestId, "7707083893", "company", "38", "osago",
                        calculationId.toString(), osagoComment, "Иванов Иван", "+7", "user@example.com"),
                sb.broker.integration.crm.model.dto.quotation.QuotationLeadCommandDTO.class);
        check("  osago comment block", quotationLead != null
                && sb.broker.integration.crm.service.quotation.QuotationCommentBuilder.hasProductBlock("osago", quotationLead.commentSource())
                && "person".equals(quotationLead.commentSource().vehicles().getFirst().owner().partyType())
                && "2015-03-03".equals(quotationLead.commentSource().vehicles().getFirst().owner().documentIssueDate()));
        var kaskoComment = new sb.broker.temporal.dto.lk.CommentSource(null,
                new sb.broker.temporal.dto.lk.CommentKaskoVehicle(false, null, "VIN", 2024, "cat", null, null, "Lada",
                        "Vesta", null, 1000, new BigDecimal("106"), "N", "2024-05-01", 77, "personal", null),
                new sb.broker.temporal.dto.lk.CommentKaskoContract(new BigDecimal("1500000"), "2026-10-08", null),
                null, null, null, null);
        var quotationUpdate = transfer("WDS QuotationLeadUpdate -> crm QuotationLeadUpdateDTO",
                new sb.broker.temporal.dto.lk.QuotationLeadUpdate(requestId, calculationId.toString(), "7707083893", "kasko",
                        kaskoComment, new sb.broker.temporal.dto.lk.CommentOffer(7L, new BigDecimal("84500.50"), null)),
                sb.broker.integration.crm.model.dto.quotation.QuotationLeadUpdateDTO.class);
        check("  kasko block and offer", quotationUpdate != null
                && sb.broker.integration.crm.service.quotation.QuotationCommentBuilder.hasProductBlock("kasko", quotationUpdate.commentSource())
                && quotationUpdate.offer().insuranceCompanyId() == 7L);
        var dmsComment = transfer("WDS CommentSource (ДМС) -> crm CommentSourceDTO",
                new sb.broker.temporal.dto.lk.CommentSource(List.of(new sb.broker.temporal.dto.lk.CommentEmployee(10, 1L,
                        "7700000000000")), null, null, null, null, null, null),
                sb.broker.integration.crm.model.dto.quotation.CommentSourceDTO.class);
        check("  dms block", dmsComment != null
                && sb.broker.integration.crm.service.quotation.QuotationCommentBuilder.hasProductBlock("dms", dmsComment));

        // ===== Product Catalog =====
        var query = transfer("WDS CatalogQuery -> catalog CatalogQueryDTO",
                new sb.broker.temporal.dto.lk.CatalogQuery("VEHICLE_CATEGORY", Map.of("categoryId", "c1", "full", "true")),
                sb.broker.quotation.productcatalog.temporal.dto.CatalogQueryDTO.class);
        check("  params preserved", query != null && query.params().get("full").equals("true"));
        for (var dictionary : sb.broker.bff.workflow.api.catalog.CatalogDictionary.values()) {
            check("  catalog knows " + dictionary,
                    java.util.Arrays.stream(sb.broker.quotation.productcatalog.temporal.dto.CatalogDictionary.values())
                            .anyMatch(known -> known.name().equals(dictionary.name())));
        }
        var gosb = transfer("catalog GosbResponseDTO -> WDS GosbDto",
                new sb.broker.quotation.productcatalog.model.dto.GosbResponseDTO("38"),
                sb.broker.temporal.dto.lk.GosbDto.class);
        check("  gosb preserved", gosb != null && "38".equals(gosb.gosbId()));

        // ===== Company Profile =====
        var region = transfer("company CompanyRegionDTO -> WDS CompanyRegionDto",
                new sb.broker.companyprofile.temporal.dto.CompanyRegionDTO(77), sb.broker.temporal.dto.lk.CompanyRegionDto.class);
        check("  region preserved", region != null && region.regionCode() == 77);

        var crmProfile = sb.broker.temporal.dto.companyProfileCrm.CompanyProfileCrmIntegrationWorkflowResponse.builder()
                .inn("7707083893").type("company").name("ООО «Пример»").fullName("ОБЩЕСТВО").ogrn("1").ceo("Иванов")
                .gosb("38").regionCode("76").region("Ярославская").okved("62.01")
                .assignedBroker(new sb.broker.temporal.dto.companyProfileCrm.AssignedBroker("Иван", "Петров", "b@x.ru"))
                .contacts(List.of(new sb.broker.temporal.dto.companyProfileCrm.CompanyContactsDto(
                        "A", "B", "C", "CEO", List.of("1"), List.of("a@b.c"), true)))
                .build();
        var save = transfer("WDS SaveCompanyProfileCommand -> company SaveCompanyProfileCommandDTO",
                new sb.broker.temporal.dto.lk.SaveCompanyProfileCommand("7707083893", crmProfile),
                sb.broker.companyprofile.temporal.dto.SaveCompanyProfileCommandDTO.class);
        check("  regionCode coerced, broker mapped", save != null && save.crmProfile().regionCode() == 76
                && save.crmProfile().personalBroker() != null && "Иван".equals(save.crmProfile().personalBroker().firstName()));

        var profile = transfer("company CompanyProfileResponseDTO -> api CompanyProfile",
                new sb.broker.companyprofile.model.dto.CompanyProfileResponseDTO("7707083893", "ООО", "ОБЩЕСТВО", "1", "Иванов",
                        "62.01", "38", 76, new sb.broker.companyprofile.model.dto.BrokerDTO("Иван", "Петров", "b@x.ru"),
                        List.of("62.02"), List.of(new sb.broker.companyprofile.model.dto.LegalAddressDTO(
                        date, null, "г. Москва", false, null, null, true, null, "GLOBAS"))),
                sb.broker.bff.workflow.api.companyprofile.CompanyProfile.class);
        check("  legal address preserved", profile != null && profile.legalAddresses().get(0).fromDate().startsWith("2026-01-15")
                && Boolean.TRUE.equals(profile.legalAddresses().get(0).isMassAddressFTS())
                && Boolean.FALSE.equals(profile.legalAddresses().get(0).isAddressFalse()));
        check("  additionalOkveds null vs list", profile != null && profile.additionalOkveds().equals(List.of("62.02")));

        // ===== Risk Scoring =====
        var group = new sb.broker.riskscoring.model.dto.RecommendationGroupDTO(10, "Остановка", 1);
        var riskDto = new sb.broker.riskscoring.model.dto.RiskProfileDTO("7707083893", "Пример", "62.01",
                "2026-09-30T12:00:00+03:00",
                List.of(new sb.broker.riskscoring.model.dto.RiskGroupDTO("property", "Имущество", List.of(
                        new sb.broker.riskscoring.model.dto.RiskDTO(101, "Перерыв", null, "Имущество")))),
                new sb.broker.riskscoring.model.dto.RecommendationsDTO(
                        List.of(new sb.broker.riskscoring.model.dto.InsuranceRecommendationDTO(501, "Страх", group)),
                        List.of(new sb.broker.riskscoring.model.dto.ServiceRecommendationDTO(701, "Аудит", "Проверка", group)),
                        List.of(new sb.broker.riskscoring.model.dto.NonInsuranceRecommendationDTO(801, "Площадка", null, group))));
        var fresh = transfer("risk FreshRiskProfileDTO -> WDS FreshRiskProfileDto",
                new sb.broker.riskscoring.temporal.dto.FreshRiskProfileDTO(riskDto), sb.broker.temporal.dto.lk.FreshRiskProfileDto.class);
        check("  risk profile preserved", fresh != null && "property".equals(fresh.profile().riskGroups().get(0).code())
                && fresh.profile().recommendations().service().get(0).code() == 701);
        transfer("risk FreshRiskProfileDTO(null) -> WDS", new sb.broker.riskscoring.temporal.dto.FreshRiskProfileDTO(null),
                sb.broker.temporal.dto.lk.FreshRiskProfileDto.class);
        var codes = transfer("risk RiskCodesDTO -> WDS RiskCodesDto",
                new sb.broker.riskscoring.temporal.dto.RiskCodesDTO(List.of(300001, 300013)), sb.broker.temporal.dto.lk.RiskCodesDto.class);
        check("  codes preserved", codes != null && codes.riskCodes().size() == 2);

        var recs = sb.broker.temporal.dto.riskRecommendations.RiskRecommendationsWorkflowResponse.builder()
                .inn("7707083893")
                .insurance(List.of(new sb.broker.temporal.dto.riskRecommendations.RiskRecommendationsWorkflowResponse.InsuranceRecommendation(
                        501, "Страх", new sb.broker.temporal.dto.riskRecommendations.RiskRecommendationsWorkflowResponse.RecommendationGroup(10, "О", 1))))
                .service(List.of()).nonInsurance(List.of()).build();
        var saveRisk = transfer("WDS SaveRiskProfileCommand -> risk SaveRiskProfileCommandDTO",
                new sb.broker.temporal.dto.lk.SaveRiskProfileCommand("wf-1", "7707083893", crmProfile, recs),
                sb.broker.riskscoring.temporal.dto.SaveRiskProfileCommandDTO.class);
        check("  company okved & recommendations preserved", saveRisk != null && "62.01".equals(saveRisk.company().okved())
                && saveRisk.recommendations().insurance().get(0).group().priority() == 1
                && saveRisk.company().assignedBroker() != null && saveRisk.company().contacts().size() == 1);

        // ===== Policy Profile =====
        var policy = new sb.broker.policyprofile.model.dto.PolicyDTO("транспорт", "КАСКО-1", "СК", new BigDecimal("84500.00"),
                date, date.plusYears(1), date.minusDays(5), 112L, 3L, "новый", 4, new BigDecimal("1500000.00"),
                List.of(new sb.broker.policyprofile.model.dto.PolicyRiskDTO("Ущерб", new BigDecimal("1500000.00"))));
        var freshPolicies = transfer("policy FreshPoliciesDTO -> WDS FreshPoliciesDto",
                new sb.broker.policyprofile.temporal.dto.FreshPoliciesDTO(true, List.of(policy)), sb.broker.temporal.dto.lk.FreshPoliciesDto.class);
        check("  ISO dates and scale preserved", freshPolicies != null
                && freshPolicies.policies().get(0).validFrom().equals("2026-01-15T00:00:00+03:00")
                && freshPolicies.policies().get(0).totalInsurancePremium().toPlainString().equals("84500.00"));
        Type policyList = sb.broker.temporal.activities.lk.PolicyProfileActivity.class.getMethod("getPolicies", String.class).getGenericReturnType();
        @SuppressWarnings("unchecked")
        List<sb.broker.bff.workflow.api.policy.Policy> policies = transfer("policy List<PolicyDTO> -> WDS List<Policy>",
                List.of(policy), List.class, policyList);
        check("  generic list element type", policies != null && policies.get(0).daysToExpire() == 112L);

        var deal = new sb.broker.policyprofile.model.dto.DealDTO("d1", "c", "К-1", date, date, date.plusYears(1), "ACTIVE",
                "vehicle", "PAID", new BigDecimal("1.00"), "INSURANCE", "x", "СК", new BigDecimal("2.00"), "транспорт",
                "новый", "kasko", 4, List.of(new sb.broker.policyprofile.model.dto.DealRiskDTO("Ущерб", new BigDecimal("3.00"))));
        // результат PolicyImportWorkflow приходит в WDS как Object (Map) и уходит в savePolicies
        Object importAsSeenByWds = transfer("policy PolicyImportResultDTO -> WDS Object",
                new sb.broker.policyprofile.model.dto.PolicyImportResultDTO(List.of(deal)), Object.class);
        var savePolicies = transfer("WDS SavePoliciesCommand -> policy SavePoliciesCommandDTO",
                new sb.broker.temporal.dto.lk.SavePoliciesCommand("7707083893", "sync-1", importAsSeenByWds),
                sb.broker.policyprofile.temporal.dto.SavePoliciesCommandDTO.class);
        check("  deal dates and amounts survive Map round-trip", savePolicies != null
                && savePolicies.importResult().deals().get(0).endDate().isEqual(date.plusYears(1))
                && savePolicies.importResult().deals().get(0).productPrice().compareTo(new BigDecimal("1.00")) == 0);

        // ===== WDS -> bff (общий api, но риск-группы/рекомендации через конвертер) =====
        var api = transfer("risk RiskProfileDTO -> api RiskProfile (bff)", riskDto,
                sb.broker.bff.workflow.api.riskprofile.RiskProfile.class);
        check("  api risk profile", api != null && api.recommendations().nonInsurance().get(0).group().code() == 10);

        System.out.println("\npassed=" + passed + " failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
