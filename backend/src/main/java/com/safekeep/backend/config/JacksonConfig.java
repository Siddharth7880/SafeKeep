package com.safekeep.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Configures Jackson to serialize LocalDateTime as ISO-8601 strings with a UTC
 * offset suffix ("Z"), e.g. "2026-08-12T14:51:45Z".
 *
 * Without this, Jackson emits bare strings like "2026-08-12T14:51:45" which
 * browsers interpret as LOCAL time instead of UTC, causing the frontend timer
 * to appear offset by the user's timezone (e.g. -5h30m for IST users).
 */
@Configuration
public class JacksonConfig {

    // ISO-8601 with 'Z' suffix — unambiguously UTC
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        JavaTimeModule module = new JavaTimeModule();

        // Serialize LocalDateTime as "2026-08-12T14:51:45Z"
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(FORMATTER));

        // Accept both bare and 'Z'-suffixed strings on deserialization
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(FORMATTER));

        return new ObjectMapper()
                .registerModule(module)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
