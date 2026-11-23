package sb.broker.bff.consent;

import org.springframework.http.MediaType;

/**
 * Файл согласия для ответа фронту.
 */
public record ConsentFile(byte[] content, MediaType contentType) {
}
