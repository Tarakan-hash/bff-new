package sb.broker.bff.calculation.dms;

import sb.broker.bff.calculation.dms.dto.DmsPreviewOfferDTO;
import sb.broker.bff.calculation.dms.dto.DmsPreviewResponseDTO;
import sb.broker.bff.calculation.dms.dto.DmsPreviewSummaryCityDTO;
import sb.broker.bff.calculation.dms.dto.DmsPreviewSummaryDTO;
import sb.broker.bff.workflow.api.calculation.AccountDmsEmployee;
import sb.broker.bff.workflow.api.calculation.DmsPreviewOffer;
import sb.broker.bff.workflow.api.calculation.DmsPreviewResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Событие {@code complete} GET предрасчёта ДМС (план): отбор по опциям, сводки по отобранному набору,
 * сортировка и страница только у массива {@code offers}. Суммы стоимостей — два знака, вверх.
 */
final class DmsPreviewAssembler {

    private static final String SUCCESS = "success";
    private static final String CITY_NOT_SUPPORTED = "city_not_supported";

    private DmsPreviewAssembler() {
    }

    static DmsPreviewResponseDTO assemble(DmsPreviewResult result, DmsPreviewQuery query) {
        Map<String, List<Integer>> groupsByKladr = nullToEmpty(result.employeeGroups()).stream()
                .collect(Collectors.groupingBy(AccountDmsEmployee::kladrId, LinkedHashMap::new,
                        Collectors.mapping(AccountDmsEmployee::count, Collectors.toList())));
        List<DmsPreviewOfferDTO> selected = nullToEmpty(result.offers()).stream()
                .filter(offer -> CITY_NOT_SUPPORTED.equals(offer.status())
                        || nullToEmpty(offer.options()).containsAll(query.options()))
                .map(offer -> offer(offer, groupsByKladr))
                .toList();
        List<DmsPreviewSummaryCityDTO> cities = cities(selected, groupsByKladr);
        return new DmsPreviewResponseDTO(result.calculationId(), page(selected, query), cities, summary(selected, cities));
    }

    private static DmsPreviewOfferDTO offer(DmsPreviewOffer offer, Map<String, List<Integer>> groupsByKladr) {
        List<Integer> groups = groupsByKladr.getOrDefault(offer.kladrId(), List.of());
        boolean citySupported = !CITY_NOT_SUPPORTED.equals(offer.status());
        return new DmsPreviewOfferDTO(offer.offerId(), offer.status(), offer.insuranceCompanyId(), offer.kladrId(),
                groups.size() == 1 ? groups.getFirst() : null,
                offer.totalCost(), offer.lpuCount(),
                citySupported || !nullToEmpty(offer.options()).isEmpty() ? nullToEmpty(offer.options()) : null);
    }

    /**
     * Пустое значение поля сортировки — после заполненных при любом направлении. Пропуск {@code page * size}
     * считается в {@code long}: при размере по умолчанию ({@link Integer#MAX_VALUE}) {@code int} переполнился бы.
     */
    private static List<DmsPreviewOfferDTO> page(List<DmsPreviewOfferDTO> offers, DmsPreviewQuery query) {
        Comparator<Comparable<Object>> order = query.descending() ? Comparator.reverseOrder() : Comparator.naturalOrder();
        Function<DmsPreviewOfferDTO, Comparable<Object>> key = sortKey(query.sortBy());
        return offers.stream()
                .sorted(Comparator.comparing(key, Comparator.nullsLast(order)))
                .skip((long) query.page() * query.size())
                .limit(query.size())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Function<DmsPreviewOfferDTO, Comparable<Object>> sortKey(DmsPreviewQuery.SortField field) {
        return offer -> (Comparable<Object>) (Comparable<?>) switch (field) {
            case insuranceCompanyId -> offer.insuranceCompanyId();
            case totalCost -> offer.totalCost();
            case lpuCount -> offer.lpuCount();
            case employeesCount -> offer.employeesCount();
        };
    }

    private static List<DmsPreviewSummaryCityDTO> cities(List<DmsPreviewOfferDTO> offers,
                                                         Map<String, List<Integer>> groupsByKladr) {
        Map<List<Object>, List<DmsPreviewOfferDTO>> blocks = offers.stream()
                .sorted(Comparator.comparing(DmsPreviewOfferDTO::insuranceCompanyId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DmsPreviewOfferDTO::kladrId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.groupingBy(offer -> List.of(offer.insuranceCompanyId(), offer.kladrId()),
                        LinkedHashMap::new, Collectors.toList()));
        return blocks.values().stream()
                .map(block -> new DmsPreviewSummaryCityDTO(
                        block.getFirst().insuranceCompanyId(),
                        groupsByKladr.getOrDefault(block.getFirst().kladrId(), List.of()).stream()
                                .filter(Objects::nonNull).mapToInt(Integer::intValue).sum(),
                        block.getFirst().kladrId(),
                        block.stream().map(DmsPreviewOfferDTO::offerId).toList(),
                        totalCost(block),
                        lpuCount(block),
                        options(block)))
                .toList();
    }

    private static List<DmsPreviewSummaryDTO> summary(List<DmsPreviewOfferDTO> offers,
                                                      List<DmsPreviewSummaryCityDTO> cities) {
        Map<Long, List<DmsPreviewOfferDTO>> successByCompany = offers.stream()
                .filter(offer -> SUCCESS.equals(offer.status()))
                .sorted(Comparator.comparing(DmsPreviewOfferDTO::insuranceCompanyId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.groupingBy(DmsPreviewOfferDTO::insuranceCompanyId, LinkedHashMap::new, Collectors.toList()));
        return successByCompany.entrySet().stream()
                .map(company -> {
                    List<String> ids = company.getValue().stream().map(DmsPreviewOfferDTO::offerId).toList();
                    int employees = cities.stream()
                            .filter(city -> Objects.equals(city.insuranceCompanyId(), company.getKey()))
                            .filter(city -> city.offers().stream().anyMatch(ids::contains))
                            .mapToInt(DmsPreviewSummaryCityDTO::employeesCount)
                            .sum();
                    Integer lpu = lpuCount(company.getValue());
                    return new DmsPreviewSummaryDTO(company.getKey(), employees, ids,
                            Objects.requireNonNullElse(totalCost(company.getValue()), BigDecimal.ZERO.setScale(2)),
                            lpu == null ? 0 : lpu, options(company.getValue()));
                })
                .toList();
    }

    private static BigDecimal totalCost(Collection<DmsPreviewOfferDTO> offers) {
        return offers.stream().map(DmsPreviewOfferDTO::totalCost).filter(Objects::nonNull)
                .reduce(BigDecimal::add)
                .map(sum -> sum.setScale(2, RoundingMode.CEILING))
                .orElse(null);
    }

    private static Integer lpuCount(Collection<DmsPreviewOfferDTO> offers) {
        return offers.stream().map(DmsPreviewOfferDTO::lpuCount).filter(Objects::nonNull)
                .reduce(Integer::sum).orElse(null);
    }

    private static List<String> options(Collection<DmsPreviewOfferDTO> offers) {
        List<String> present = offers.stream().flatMap(offer -> nullToEmpty(offer.options()).stream()).toList();
        return DmsPreviewQuery.OPTION_CODES.stream().filter(present::contains).toList();
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
