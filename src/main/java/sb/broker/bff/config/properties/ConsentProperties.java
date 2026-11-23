package sb.broker.bff.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param defaultFileContentType Content-Type файла согласия, если CRM его не вернула
 */
@Validated
@ConfigurationProperties(prefix = "bff.consents")
public record ConsentProperties(@DefaultValue("application/octet-stream") @NotBlank String defaultFileContentType) {
}
