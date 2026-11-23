package sb.broker.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.Optional;

/**
 * Открытые сценарии лида кабинета.
 *
 * @param scenarios сценарий ({@code subtype}) -> его правила; сценария нет в списке — он закрыт
 */
@Validated
@ConfigurationProperties(prefix = "bff.leads")
public record LeadProperties(@DefaultValue Map<String, Scenario> scenarios) {

    /**
     * @param policy сценарий продления полиса: продукт и номер полиса обязательны; без него поля полиса запрещены
     */
    public record Scenario(boolean policy) {
    }

    public Optional<Scenario> scenario(String subtype) {
        return Optional.ofNullable(scenarios.get(subtype));
    }
}
