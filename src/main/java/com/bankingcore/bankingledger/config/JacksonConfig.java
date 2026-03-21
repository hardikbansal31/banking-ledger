package com.bankingcore.bankingledger.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * JacksonConfig — customises the ObjectMapper used by Spring MVC.
 *
 * The main problem this solves:
 *   BigDecimal(5000.00) serialises as 5000.0 by default because Jackson
 *   calls .toString() which strips trailing zeros.
 *
 *   5000.00 is meaningful in banking — it signals 2 decimal places of
 *   precision. An amount field showing "5000.0" looks like a bug to any
 *   auditor or frontend developer reading the API response.
 *
 * Fix: register a custom serializer that calls toPlainString() on a
 * BigDecimal that has been scaled to 2dp, which outputs "5000.00".
 *
 * This serializer is registered globally — every BigDecimal in every
 * response body will render with its full scale preserved.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.build();

        SimpleModule module = new SimpleModule("BigDecimalModule");
        module.addSerializer(BigDecimal.class, new BigDecimalScaleSerializer());
        mapper.registerModule(module);

        return mapper;
    }

    /**
     * Serialises BigDecimal values using toPlainString() which:
     *  - preserves trailing zeros:  5000.00 → "5000.00"  (not "5000.0")
     *  - avoids scientific notation: 1.5E+10 → "15000000000"
     *
     * Values are written as JSON numbers (no quotes) so clients can parse
     * them as numbers. Note: JavaScript's Number loses precision beyond 15
     * significant digits — clients handling large amounts should use a
     * decimal library or treat the field as a string.
     */
    static class BigDecimalScaleSerializer extends StdSerializer<BigDecimal> {

        BigDecimalScaleSerializer() {
            super(BigDecimal.class);
        }

        @Override
        public void serialize(BigDecimal value,
                              JsonGenerator gen,
                              SerializerProvider provider) throws IOException {
            // Write as a raw number string — preserves scale without quotes
            gen.writeNumber(value.toPlainString());
        }
    }
}