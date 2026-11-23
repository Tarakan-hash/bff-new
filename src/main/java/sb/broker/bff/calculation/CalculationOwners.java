package sb.broker.bff.calculation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sb.broker.bff.config.properties.CalculationProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Ключ владельца предрасчёта в идентификаторе workflow: HMAC-SHA256 от email, первые 16 байт.
 *
 * <p>{@code workflowId} уходит фронту, а GET и выбор предложения приходят с ним обратно. Ключ позволяет
 * проверить владельца без похода в Temporal — в том числе у закрытого или упавшего workflow — и не раскрывает
 * email. Без секрета ключ чужого email не подобрать.
 */
@Slf4j
@Component
public class CalculationOwners {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_BYTES = 16;

    private final SecretKeySpec secret;

    public CalculationOwners(CalculationProperties properties) {
        this.secret = new SecretKeySpec(properties.ownerKey().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String key(String email) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secret);
            byte[] digest = mac.doFinal(email.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, KEY_BYTES);
        } catch (GeneralSecurityException e) {
            log.error("{} is unavailable", ALGORITHM, e);
            throw new IllegalStateException(ALGORITHM + " is unavailable", e);
        }
    }
}
