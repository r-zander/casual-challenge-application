package gg.casualchallenge.application.api.legacy.datamodel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyMtgFormatTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializationToTitleCase() throws Exception {
        assertEquals("\"Standard\"", objectMapper.writeValueAsString(LegacyMtgFormat.STANDARD));
        assertEquals("\"Pioneer\"", objectMapper.writeValueAsString(LegacyMtgFormat.PIONEER));
        assertEquals("\"Pauper\"", objectMapper.writeValueAsString(LegacyMtgFormat.PAUPER));
        assertEquals("\"Modern\"", objectMapper.writeValueAsString(LegacyMtgFormat.MODERN));
        assertEquals("\"Legacy\"", objectMapper.writeValueAsString(LegacyMtgFormat.LEGACY));
        assertEquals("\"Vintage\"", objectMapper.writeValueAsString(LegacyMtgFormat.VINTAGE));
    }

    @Test
    void testDeserializationFromTitleCase() throws Exception {
        assertEquals(LegacyMtgFormat.STANDARD, objectMapper.readValue("\"Standard\"", LegacyMtgFormat.class));
        assertEquals(LegacyMtgFormat.PIONEER, objectMapper.readValue("\"Pioneer\"", LegacyMtgFormat.class));
        assertEquals(LegacyMtgFormat.PAUPER, objectMapper.readValue("\"Pauper\"", LegacyMtgFormat.class));
        assertEquals(LegacyMtgFormat.MODERN, objectMapper.readValue("\"Modern\"", LegacyMtgFormat.class));
        assertEquals(LegacyMtgFormat.LEGACY, objectMapper.readValue("\"Legacy\"", LegacyMtgFormat.class));
        assertEquals(LegacyMtgFormat.VINTAGE, objectMapper.readValue("\"Vintage\"", LegacyMtgFormat.class));
    }
}
