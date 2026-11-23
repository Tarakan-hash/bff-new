package sb.broker.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import sb.broker.bff.catalog.CatalogEntryNotFoundException;
import sb.broker.bff.workflow.api.catalog.CatalogDictionary;
import sb.broker.bff.workflow.api.companyprofile.CompanyProfileErrorCode;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * У каждого кода ошибки, исхода, справочника и ошибки карточки есть текст: новый enum без текста
 * иначе всплыл бы только в рантайме.
 */
class MessagesCompletenessTest {

    private final ResourceBundleMessageSource messages = new ResourceBundleMessageSource();

    MessagesCompletenessTest() {
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
    }

    @Test
    void everyCodeHasText() {
        Stream<String> codes = Stream.of(
                Arrays.stream(ErrorType.values()).map(ErrorType::getMessageCode),
                Arrays.stream(Outcome.values()).filter(o -> o != Outcome.ACCEPTED).map(Outcome::getMessageCode),
                Arrays.stream(CatalogDictionary.values()).map(d -> new CatalogEntryNotFoundException(d).getMessageCode()),
                Arrays.stream(CompanyProfileErrorCode.values())
                        .map(c -> "company-profile." + c.name().toLowerCase().replace('_', '-')),
                Stream.of("company-profile.session-not-found", "company-profile.internal-fallback",
                        "policies.no-policies")
        ).flatMap(s -> s);

        assertThat(codes).allSatisfy(code ->
                assertThat(messages.getMessage(code, null, null, Locale.ROOT)).as(code).isNotBlank());
    }
}
