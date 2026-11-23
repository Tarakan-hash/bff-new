package sb.broker.bff.web;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Целое число только из JSON-целого: {@code 1.5} и {@code "10"} Jackson по умолчанию привёл бы к {@code Long},
 * а план требует {@code 400} для «не целого числа».
 */
public class StrictLongDeserializer extends ValueDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
                || parser.getNumberType() != JsonParser.NumberType.INT && parser.getNumberType() != JsonParser.NumberType.LONG) {
            return (Long) context.handleUnexpectedToken(Long.class, parser);
        }
        return parser.getLongValue();
    }
}
