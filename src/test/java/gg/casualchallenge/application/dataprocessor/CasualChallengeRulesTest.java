package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasualChallengeRulesTest {

    @Test
    void testIsBasicLand() {
        assertTrue(CasualChallengeRules.isBasicLand("Plains"));
        assertTrue(CasualChallengeRules.isBasicLand("Island"));
        assertTrue(CasualChallengeRules.isBasicLand("Swamp"));
        assertTrue(CasualChallengeRules.isBasicLand("Mountain"));
        assertTrue(CasualChallengeRules.isBasicLand("Forest"));
        assertTrue(CasualChallengeRules.isBasicLand("Wastes"));
        assertFalse(CasualChallengeRules.isBasicLand("Wasteland"));
        assertFalse(CasualChallengeRules.isBasicLand("Snow-Covered Forest"));
        assertFalse(CasualChallengeRules.isBasicLand("forest"));
    }

    @Test
    void testIsFlipStyleName() {
        assertTrue(CasualChallengeRules.isFlipStyleName("Delver of Secrets // Delver of Secrets"));
        assertTrue(CasualChallengeRules.isFlipStyleName("Bloomvine Regent // Claim Territory // Bloomvine Regent"));
        assertFalse(CasualChallengeRules.isFlipStyleName("Fire // Ice"));
        assertFalse(CasualChallengeRules.isFlipStyleName("Delver of Secrets // Insectile Aberration"));
        assertFalse(CasualChallengeRules.isFlipStyleName("Lightning Bolt"));
        assertFalse(CasualChallengeRules.isFlipStyleName(null));
    }

    @Test
    void testLegalityOf() {
        assertEquals(Legality.LEGAL, CasualChallengeRules.legalityOf("Wastes", false, false, null, 0, true, true));
        assertEquals(Legality.LEGAL, CasualChallengeRules.legalityOf("Forest", true, false, MtgFormat.LEGACY, null, true, true));
        assertEquals(Legality.BANNED, CasualChallengeRules.legalityOf("Wasteland", true, false, null, 900, true, false));
        assertEquals(Legality.NOT_LEGAL, CasualChallengeRules.legalityOf("Shahrazad", false, false, null, 100, true, true));
        assertEquals(Legality.NOT_LEGAL, CasualChallengeRules.legalityOf("Lightning Bolt", true, false, null, null, true, true));
        assertEquals(Legality.NOT_LEGAL, CasualChallengeRules.legalityOf("Lightning Bolt", true, false, null, 0, true, true));
        assertEquals(Legality.BANNED, CasualChallengeRules.legalityOf("Black Lotus", true, true, null, 100000, false, false));
        assertEquals(Legality.BANNED, CasualChallengeRules.legalityOf("Ragavan, Nimble Pilferer", true, false, null, 4000, true, true));
        assertEquals(Legality.BANNED, CasualChallengeRules.legalityOf("Lurrus of the Dream-Den", true, false, MtgFormat.MODERN, 600, false, true));
        assertEquals(Legality.EXTENDED, CasualChallengeRules.legalityOf("Abrade", true, false, null, 50, false, true));
        assertEquals(Legality.LEGAL, CasualChallengeRules.legalityOf("Abrade", true, false, null, 50, false, false));
    }
}
