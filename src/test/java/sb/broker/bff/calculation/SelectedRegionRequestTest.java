package sb.broker.bff.calculation;

import org.junit.jupiter.api.Test;
import sb.broker.bff.calculation.dms.dto.CreateAccountDmsPreviewRequestDTO;
import sb.broker.bff.calculation.kasko.dto.CreateAccountKaskoInsurantDTO;
import sb.broker.bff.lead.dto.AccountLeadRequestDTO;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SelectedRegionRequestTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void leadAcceptsLegacyScalarProductId() {
        var request = mapper.readValue("""
                {"fio":"Иванов Иван Иванович","phone":"+79990000000","comment":"Нужна консультация",
                 "subtype":"CONTACT_BROKER","productId":3}
                """, AccountLeadRequestDTO.class);
        assertThat(request.productIds()).containsExactly(3L);
        assertThat(request.hasPolicyFields()).isFalse();
    }

    @Test
    void leadStillAcceptsProductIdsList() {
        var request = mapper.readValue("""
                {"subtype":"CONTACT_BROKER","productIds":[3,4]}
                """, AccountLeadRequestDTO.class);
        assertThat(request.productIds()).containsExactly(3L, 4L);
    }

    @Test
    void dmsGosbIsOptionalAndPreserved() {
        assertThat(mapper.readValue("{\"regionCode\":77}", CreateAccountDmsPreviewRequestDTO.class).gosb()).isNull();
        assertThat(mapper.readValue("{\"regionCode\":77,\"gosb\":\"\"}", CreateAccountDmsPreviewRequestDTO.class).gosb()).isEmpty();
        assertThat(mapper.readValue("{\"regionCode\":77,\"gosb\":\"38\"}", CreateAccountDmsPreviewRequestDTO.class).gosb()).isEqualTo("38");
    }

    @Test
    void kaskoGosbIsOptionalAndPreserved() {
        assertThat(mapper.readValue("{\"regionCode\":77}", CreateAccountKaskoInsurantDTO.class).gosb()).isNull();
        assertThat(mapper.readValue("{\"regionCode\":77,\"gosb\":\"\"}", CreateAccountKaskoInsurantDTO.class).gosb()).isEmpty();
        assertThat(mapper.readValue("{\"regionCode\":77,\"gosb\":\"38\"}", CreateAccountKaskoInsurantDTO.class).gosb()).isEqualTo("38");
    }
}
