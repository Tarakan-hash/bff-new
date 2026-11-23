package sb.broker.bff.consent;

import jakarta.validation.Validation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sb.broker.bff.consent.dto.ConsentSubmissionDTO;
import sb.broker.bff.workflow.api.consent.ConsentType;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsentSubmissionDTOTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"type\":\"PDN\",\"consent\":true}]",
            "{\"data\":[{\"type\":\"PDN\",\"consent\":true}]}"
    })
    void acceptsArrayAndDataEnvelope(String body) {
        var submission = mapper.readValue(body, ConsentSubmissionDTO.class);

        assertThat(submission.data()).hasSize(1);
        assertThat(submission.data().getFirst().type()).isEqualTo(ConsentType.PDN);
        assertThat(submission.data().getFirst().consent()).isTrue();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(submission)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[null]", "{\"data\":[null]}", "{\"data\":null}",
            "[{\"type\":\"PDN\"}]", "{\"data\":[{\"type\":\"PDN\"}]}",
            "[{\"consent\":true}]", "{\"data\":[{\"consent\":true}]}"
    })
    void validatesDecisionsInBothBodyShapes(String body) {
        var submission = mapper.readValue(body, ConsentSubmissionDTO.class);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(submission)).isNotEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"data\":{\"type\":\"PDN\",\"consent\":true}}", "[[{\"type\":\"PDN\",\"consent\":true}]]"})
    void rejectsMalformedBodyShapes(String body) {
        assertThatThrownBy(() -> mapper.readValue(body, ConsentSubmissionDTO.class))
                .isInstanceOf(tools.jackson.databind.DatabindException.class);
    }
}
