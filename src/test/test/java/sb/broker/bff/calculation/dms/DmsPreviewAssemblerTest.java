package sb.broker.bff.calculation.dms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sb.broker.bff.calculation.dms.dto.DmsPreviewOfferDTO;
import sb.broker.bff.calculation.dms.dto.DmsPreviewResponseDTO;
import sb.broker.bff.web.ServiceException;
import sb.broker.bff.workflow.api.calculation.AccountDmsEmployee;
import sb.broker.bff.workflow.api.calculation.DmsPreviewOffer;
import sb.broker.bff.workflow.api.calculation.DmsPreviewResult;
import sb.broker.bff.workflow.api.calculation.PreviewGateOutcome;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DmsPreviewAssemblerTest {

    private static final String MOSCOW = "7700000000000";
    private static final String SPB = "7800000000000";

    @Test
    void planExampleIsReproduced() {
        DmsPreviewResult result = new DmsPreviewResult(PreviewGateOutcome.ACCEPTED, "calc-1",
                List.of(offer("offer-1", "success", 1, MOSCOW, "150000.5", 12, "clinic"),
                        new DmsPreviewOffer("offer-2", "city_not_supported", 2L, MOSCOW, null, null, List.of())),
                List.of(new AccountDmsEmployee(10, 1L, MOSCOW)));

        String json = JsonMapper.builder().build().writeValueAsString(
                DmsPreviewAssembler.assemble(result, DmsPreviewQuery.parse(null, null, null, null, null)));

        assertThat(json).isEqualToIgnoringWhitespace("""
                {"calculationId":"calc-1",
                 "offers":[
                  {"offerId":"offer-1","status":"success","insuranceCompanyId":1,"kladrId":"7700000000000","employeesCount":10,"totalCost":150000.5,"lpuCount":12,"options":["clinic"]},
                  {"offerId":"offer-2","status":"city_not_supported","insuranceCompanyId":2,"kladrId":"7700000000000","employeesCount":10,"totalCost":null,"lpuCount":null,"options":null}],
                 "summaryCities":[
                  {"insuranceCompanyId":1,"employeesCount":10,"kladrId":"7700000000000","offers":["offer-1"],"totalCost":150000.50,"lpuCount":12,"options":["clinic"]},
                  {"insuranceCompanyId":2,"employeesCount":10,"kladrId":"7700000000000","offers":["offer-2"],"totalCost":null,"lpuCount":null,"options":[]}],
                 "summary":[
                  {"insuranceCompanyId":1,"employeesCount":10,"offers":["offer-1"],"totalCost":150000.50,"lpuCount":12,"options":["clinic"]}]}""");
    }

    @Test
    void optionFilterRequiresEveryCodeAndKeepsUnsupportedCities() {
        DmsPreviewResponseDTO response = DmsPreviewAssembler.assemble(result(), DmsPreviewQuery.parse(null, null, null,
                null, "clinic,dentistry"));

        assertThat(response.offers()).extracting(DmsPreviewOfferDTO::offerId).containsExactly("a-2", "b-1", "c-1");
    }

    @Test
    void sortingPutsEmptyValuesLastInBothDirections() {
        assertThat(ids(DmsPreviewQuery.parse(null, null, "totalCost", "desc", null))).containsExactly("b-1", "a-1", "a-2", "c-1");
        assertThat(ids(DmsPreviewQuery.parse(null, null, "totalCost", "asc", null))).containsExactly("a-2", "a-1", "b-1", "c-1");
    }

    @Test
    void employeesCountIsEmptyWhenCityHasSeveralGroups() {
        DmsPreviewResponseDTO response = DmsPreviewAssembler.assemble(result(), DmsPreviewQuery.parse(null, null, null, null, null));

        assertThat(response.offers()).filteredOn(offer -> offer.kladrId().equals(MOSCOW))
                .allSatisfy(offer -> assertThat(offer.employeesCount()).isNull());
        assertThat(response.summaryCities()).filteredOn(city -> city.kladrId().equals(MOSCOW))
                .allSatisfy(city -> assertThat(city.employeesCount()).isEqualTo(15));
        assertThat(response.summary()).extracting(summary -> summary.insuranceCompanyId()).containsExactly(1L, 2L);
        assertThat(response.summary().getFirst().totalCost()).isEqualByComparingTo("300.02");
    }

    @Test
    void secondPageOfDefaultSizeIsEmptyInsteadOfOverflow() {
        assertThat(ids(DmsPreviewQuery.parse("1", null, null, null, null))).isEmpty();
        assertThat(ids(DmsPreviewQuery.parse("1", "2", null, null, null))).containsExactly("b-1", "c-1");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {"-1,null,null,null,null", "null,-1,null,null,null", "x,null,null,null,null",
            "null,null,price,null,null", "null,null,null,up,null", "null,null,null,null,'clinic,spa'", "null,null,null,null,''"})
    void invalidQueryIs400(String page, String size, String sortBy, String direction, String options) {
        assertThatThrownBy(() -> DmsPreviewQuery.parse(page, size, sortBy, direction, options))
                .isInstanceOf(ServiceException.class);
    }

    private static List<String> ids(DmsPreviewQuery query) {
        return DmsPreviewAssembler.assemble(result(), query).offers().stream().map(DmsPreviewOfferDTO::offerId).toList();
    }

    /** Компания 1 — две группы Москвы, компания 2 — СПб, компания 3 — город не поддерживается. */
    private static DmsPreviewResult result() {
        return new DmsPreviewResult(PreviewGateOutcome.ACCEPTED, "calc-1",
                List.of(offer("a-1", "success", 1, MOSCOW, "200.011", 3, "clinic"),
                        offer("a-2", "success", 1, MOSCOW, "100.001", 2, "clinic", "dentistry"),
                        offer("b-1", "success", 2, SPB, "500", 7, "clinic", "dentistry", "hospital"),
                        new DmsPreviewOffer("c-1", "city_not_supported", 3L, SPB, null, null, List.of())),
                List.of(new AccountDmsEmployee(10, 1L, MOSCOW), new AccountDmsEmployee(5, 2L, MOSCOW),
                        new AccountDmsEmployee(20, 1L, SPB)));
    }

    private static DmsPreviewOffer offer(String id, String status, long company, String kladr, String cost, int lpu,
                                         String... options) {
        return new DmsPreviewOffer(id, status, company, kladr, new BigDecimal(cost), lpu, List.of(options));
    }
}
