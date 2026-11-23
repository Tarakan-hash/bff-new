package sb.broker.bff.calculation.dms;

import sb.broker.bff.web.ErrorType;
import sb.broker.bff.web.ServiceException;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Отбор, сортировка и страница предложений GET предрасчёта ДМС. Неверный параметр — {@code 400}
 * «Параметры отбора предрасчёта некорректны».
 *
 * @param size    без параметра — {@link Integer#MAX_VALUE}
 * @param options коды, которые должны быть у предложения все сразу
 */
record DmsPreviewQuery(int page, int size, SortField sortBy, boolean descending, Set<String> options) {

    /** Коды опций ДМС в порядке вывода. */
    static final List<String> OPTION_CODES =
            List.of("clinic", "dentistry", "hospital", "checkup", "emergency", "homehelp", "complex_checkup");

    enum SortField { insuranceCompanyId, totalCost, lpuCount, employeesCount }

    static DmsPreviewQuery parse(String page, String size, String sortBy, String sortDirection, String options) {
        return new DmsPreviewQuery(
                nonNegative(page, 0),
                nonNegative(size, Integer.MAX_VALUE),
                sortBy == null ? SortField.insuranceCompanyId : sortField(sortBy),
                sortDirection == null ? false : direction(sortDirection),
                options == null ? Set.of("clinic") : options(options));
    }

    private static int nonNegative(String value, int absent) {
        if (value == null) {
            return absent;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw invalid();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw invalid();
        }
    }

    private static SortField sortField(String value) {
        return Arrays.stream(SortField.values())
                .filter(field -> field.name().equals(value))
                .findFirst()
                .orElseThrow(DmsPreviewQuery::invalid);
    }

    private static boolean direction(String value) {
        return switch (value) {
            case "asc" -> false;
            case "desc" -> true;
            default -> throw invalid();
        };
    }

    private static Set<String> options(String value) {
        Set<String> codes = new LinkedHashSet<>();
        for (String code : value.split(",", -1)) {
            if (!OPTION_CODES.contains(code.trim())) {
                throw invalid();
            }
            codes.add(code.trim());
        }
        return codes;
    }

    private static ServiceException invalid() {
        return new ServiceException(ErrorType.PREVIEW_QUERY_INVALID);
    }
}
