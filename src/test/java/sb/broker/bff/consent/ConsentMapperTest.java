package sb.broker.bff.consent;

import org.junit.jupiter.api.Test;
import sb.broker.bff.workflow.api.consent.UserConsent;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentMapperTest {

    @Test
    void allSixFieldsReachHttpResponse() {
        var dto = ConsentMapper.toDto(List.of(new UserConsent("Новое согласие", "NEW_TYPE",
                true, false, "consent-guid", "2026-11-12T10:38:35+03:00"))).getFirst();

        var json = JsonMapper.builder().build().valueToTree(dto);

        assertThat(json.size()).isEqualTo(6);
        assertThat(json.get("title").asString()).isEqualTo("Новое согласие");
        assertThat(json.get("type").asString()).isEqualTo("NEW_TYPE");
        assertThat(json.get("status").asBoolean()).isTrue();
        assertThat(json.get("required").asBoolean()).isFalse();
        assertThat(json.get("guid").asString()).isEqualTo("consent-guid");
        assertThat(json.get("date").asString()).isEqualTo("2026-11-12T10:38:35+03:00");
    }

    @Test
    void absentDateIsOmittedWithoutLosingTypeOrGuid() {
        var dto = ConsentMapper.toDto(List.of(new UserConsent("ПДН", "PDN",
                false, true, "pdn-guid", null))).getFirst();

        var json = JsonMapper.builder().build().valueToTree(dto);

        assertThat(json.has("date")).isFalse();
        assertThat(json.get("type").asString()).isEqualTo("PDN");
        assertThat(json.get("guid").asString()).isEqualTo("pdn-guid");
    }
}
