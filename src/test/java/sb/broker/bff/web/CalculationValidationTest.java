package sb.broker.bff.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import sb.broker.bff.support.ApiTestSupport;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalculationValidationTest extends ApiTestSupport {

    private static final String BODY = """
                {"insurant":{"companyType":"company","inn":"7707083893","regionCode":77,
                   "contacts":{"surname":"Иванов","name":"Иван","phone":"+74951234567"},"product":"kasko"},
                 "vehicle":{"vin":"XTA00000000000001","category":"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f11","markId":1,"mark":"Lada",
                   "modelId":2,"model":"Vesta","manufactureYear":2024,"enginePower":106,"mileage":1000,
                   "document":{"typeGuid":"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f12","number":"77УН123456","issueDate":"2024-05-01"},
                   "usePurpose":"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f13"},
                 "insuranceContract":{"startDate":"2026-10-08","insuranceCost":1500000}}""";

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-13-01", "2025-02-29", "2026-2-01", "2026-00-01"})
    void invalidCalendarDateIs400WithoutTemporal(String value) throws Exception {
        reject(BODY.replace("2026-10-08", value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "\"1\"", "2147483648"})
    void invalidIntegerIs400WithoutTemporal(String value) throws Exception {
        reject(BODY.replace("\"markId\":1", "\"markId\":" + value));
    }

    private void reject(String body) throws Exception {
        mvc.perform(post("/api/v1/calculation/kasko").with(user())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(workflows);
    }
}
