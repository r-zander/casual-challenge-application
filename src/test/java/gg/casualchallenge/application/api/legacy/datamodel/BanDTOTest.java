package gg.casualchallenge.application.api.legacy.datamodel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BanDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void testParseBanFile() throws Exception {
        String banFile = """
                [
                {"name":"Abandon Attachments", "formats":{"Pioneer": 0.13}},
                {"name":"Abrade", "formats":{"Pioneer": 0.14, "Legacy": 0.13}},
                {"name":"Annul", "formats":{"Standard": 0.22, "Pauper": 0.17}},
                {"name":"Atraxa, Grand Unifier", "formats":{"Modern": 0.11, "Vintage": 0.15}}
                ]""";

        List<BanDTO> bans = Arrays.asList(objectMapper.readValue(banFile, BanDTO[].class));

        assertEquals(4, bans.size());
        assertEquals("Abandon Attachments", bans.get(0).getName());
        assertEquals(new BigDecimal("0.13"), bans.get(0).getFormats().get(LegacyMtgFormat.PIONEER));
        assertNull(bans.get(0).getFormats().get(LegacyMtgFormat.LEGACY));
        assertEquals(new BigDecimal("0.14"), bans.get(1).getFormats().get(LegacyMtgFormat.PIONEER));
        assertEquals(new BigDecimal("0.13"), bans.get(1).getFormats().get(LegacyMtgFormat.LEGACY));
        assertEquals(new BigDecimal("0.22"), bans.get(2).getFormats().get(LegacyMtgFormat.STANDARD));
        assertEquals(new BigDecimal("0.17"), bans.get(2).getFormats().get(LegacyMtgFormat.PAUPER));
        assertEquals("Atraxa, Grand Unifier", bans.get(3).getName());
        assertEquals(new BigDecimal("0.11"), bans.get(3).getFormats().get(LegacyMtgFormat.MODERN));
        assertEquals(new BigDecimal("0.15"), bans.get(3).getFormats().get(LegacyMtgFormat.VINTAGE));
    }
}
