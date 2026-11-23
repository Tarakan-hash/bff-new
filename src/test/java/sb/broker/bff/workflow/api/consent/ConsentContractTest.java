package sb.broker.bff.workflow.api.consent;

import io.temporal.common.converter.DefaultDataConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentContractTest {

    @Test
    void temporalPayloadKeepsAllSixFields() {
        var source = new UserConsent("Новое согласие", "NEW_TYPE", true, false,
                "consent-guid", "2026-11-12T10:38:35+03:00");
        var converter = DefaultDataConverter.newDefaultInstance();

        var result = converter.fromPayloads(0, converter.toPayloads(source),
                UserConsent.class, UserConsent.class);

        assertThat(result).isEqualTo(source);
    }

    @Test
    void historicalPayloadWithoutTypeAndGuidRemainsReadable() {
        record LegacyConsent(String title, boolean status, boolean required, String date) {}
        var converter = DefaultDataConverter.newDefaultInstance();

        var result = converter.fromPayloads(0, converter.toPayloads(
                new LegacyConsent("ПДН", true, true, null)), UserConsent.class, UserConsent.class);

        assertThat(result).isEqualTo(new UserConsent("ПДН", null, true, true, null, null));
    }

    @Test
    void oldConstructorRemainsAvailable() {
        assertThat(new UserConsent("ПДН", true, true, null))
                .isEqualTo(new UserConsent("ПДН", null, true, true, null, null));
    }

    @Test
    void temporalPayloadKeepsNewsletterType() {
        var source = new SubmitUserConsentsInput("user@example.com", java.util.List.of(ConsentType.NEWSLETTER));
        var converter = DefaultDataConverter.newDefaultInstance();

        var result = converter.fromPayloads(0, converter.toPayloads(source),
                SubmitUserConsentsInput.class, SubmitUserConsentsInput.class);

        assertThat(result).isEqualTo(source);
    }
}
