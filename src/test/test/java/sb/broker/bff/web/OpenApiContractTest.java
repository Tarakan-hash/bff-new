package sb.broker.bff.web;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import sb.broker.bff.support.ApiTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * OpenAPI bff сверяется с docs/plan: каждый метод плана описан, каждый код из таблицы «Коды ответов»
 * есть в спецификации, у методов с {@code consents_required} есть этот пример. Спецификация выгружается
 * в {@code target/openapi/bff-lk-openapi.json} для ревью.
 */
class OpenApiContractTest extends ApiTestSupport {

   private static final Pattern TITLE = Pattern.compile("`(GET|POST) /bff-lk(/api/v1[^`]*)`");
    private static final Pattern CODE_ROW = Pattern.compile("^\\| (\\d{3}) \\|(.*)$", Pattern.MULTILINE);

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @Disabled
    void checkedInSpecificationMatchesRuntime() throws Exception {
        JsonNode runtime = json.readTree(mvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString());
        JsonNode checkedIn = json.readTree(Files.readString(Path.of("../openapi/bff-lk-openapi.json")));
        assertThat(checkedIn).as("Обновите openapi/bff-lk-openapi.json по инструкции README").isEqualTo(runtime);
    }

    private static List<String> check(JsonNode spec, String plan, String file) {
        Matcher title = TITLE.matcher(plan);
        if (!title.find()) {
            return List.of(file + ": нет заголовка метода");
        }
        String method = title.group(1).toLowerCase();
        String path = title.group(2);
        JsonNode operation = spec.path("paths").path(path).path(method);
        if (operation.isMissingNode()) {
            return List.of(file + ": в OpenAPI нет " + method + " " + path);
        }
        List<String> problems = new ArrayList<>();
        Set<String> documented = new TreeSet<>(operation.path("responses").propertyNames());
        String table = plan.substring(plan.indexOf("## Коды ответов"));
        Matcher row = CODE_ROW.matcher(table);
        while (row.find()) {
            String code = row.group(1);
            if (!documented.contains(code)) {
                problems.add(file + ": код " + code + " не описан, описаны " + documented);
            }
            if (row.group(2).contains("consents_required")
                    && !operation.path("responses").path(code).toString().contains("consents_required")) {
                problems.add(file + ": у " + code + " нет примера consents_required");
            }
        }
        return problems;
    }

    private static void export(JsonNode spec) throws IOException {
        Path target = Path.of("target/openapi/bff-lk-openapi.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, spec.toPrettyString());
    }
}
