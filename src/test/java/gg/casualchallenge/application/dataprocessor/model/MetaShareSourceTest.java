package gg.casualchallenge.application.dataprocessor.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetaShareSourceTest {

    @Test
    void testJson() throws JsonProcessingException {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        assertEquals("\"mtggoldfish\"", objectMapper.writeValueAsString(MetaShareSource.MTGGOLDFISH));
        assertEquals("\"mtgtop8\"", objectMapper.writeValueAsString(MetaShareSource.MTGTOP8));
        assertEquals("\"files\"", objectMapper.writeValueAsString(MetaShareSource.FILES));
        assertEquals(MetaShareSource.MTGGOLDFISH, objectMapper.readValue("\"mtggoldfish\"", MetaShareSource.class));
        assertEquals(MetaShareSource.MTGTOP8, objectMapper.readValue("\"mtgtop8\"", MetaShareSource.class));
        assertEquals(MetaShareSource.FILES, objectMapper.readValue("\"files\"", MetaShareSource.class));
        assertEquals(MetaShareSource.MTGGOLDFISH, objectMapper.readValue("\"MTGGOLDFISH\"", MetaShareSource.class));
        assertEquals(MetaShareSource.MTGTOP8, objectMapper.readValue("\"MTGTOP8\"", MetaShareSource.class));
        assertEquals(MetaShareSource.FILES, objectMapper.readValue("\"FILES\"", MetaShareSource.class));
    }

}
