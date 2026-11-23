package sb.broker.bff.calculation.dms.dto;


import java.util.List;

/**
 * Входные данные workflow предрасчёта ДМС.
 * ИНН до получения допуска не заполнен.
 */
public record AccountDmsCalculationWorkflowInput(
        String email,
        String requestId,
        Request request
) {

    public record Request(
            String companyType,
            String inn,
            Integer regionCode,
            String surname,
            String name,
            String phone,
            List<EmployeeGroup> employees,
            String product
    ) {
    }

    public record EmployeeGroup(
            Integer count,
            Long ageGroupId,
            String kladrId
    ) {
    }
}
