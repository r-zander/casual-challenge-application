package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.MtgJsonCard;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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
    void testIsCommaCard() {
        assertTrue(CasualChallengeRules.isCommaCard("Rampant, Growth"));
        assertTrue(CasualChallengeRules.isCommaCard("Lava, Axe"));
        assertTrue(CasualChallengeRules.isCommaCard("Clear, the Mind"));
        assertFalse(CasualChallengeRules.isCommaCard("Rampant Growth"));
        assertFalse(CasualChallengeRules.isCommaCard("Ragavan, Nimble Pilferer"));
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
        assertEquals(Legality.LEGAL, CasualChallengeRules.legalityOf(card("Wastes", false, false, null), 0, true, true));
        assertEquals(Legality.LEGAL, CasualChallengeRules.legalityOf(card("Forest", true, false, MtgFormat.LEGACY), null, true, true));
        assertEquals(Legality.BANNED, CasualChallengeRules.legalityOf(card("Wasteland", true, false, null), 900, true, false));
        assertEquals(Legality.NOT_LEGAL, CasualChallengeRules.legalityOf(card("Shahrazad", false, false, null), 100, true, true));
        assertEquals(Legality.NOT_LEGAL, CasualChallengeRules.legalityOf(card("Lightning Bolt", true, false, null), null, true, true));
        assertEquals(Legality.NOT_LEGAL, CasualChallengeRules.legalityOf(card("Lightning Bolt", true, false, null), 0, true, true));
        assertEquals(Legality.BANNED, CasualChallengeRules.legalityOf(card("Black Lotus", true, true, null), 100000, false, false));
        assertEquals(Legality.BANNED, CasualChallengeRules.legalityOf(card("Ragavan, Nimble Pilferer", true, false, null), 4000, true, true));
        assertEquals(Legality.BANNED, CasualChallengeRules.legalityOf(card("Lurrus of the Dream-Den", true, false, MtgFormat.MODERN), 600, false, true));
        assertEquals(Legality.EXTENDED, CasualChallengeRules.legalityOf(card("Abrade", true, false, null), 50, false, true));
        assertEquals(Legality.LEGAL, CasualChallengeRules.legalityOf(card("Abrade", true, false, null), 50, false, false));
    }

    private static MtgJsonCard card(String cardName, boolean isVintageLegal, boolean isVintageRestricted, MtgFormat bannedIn) {
        return new MtgJsonCard(cardName, null, isVintageLegal, isVintageRestricted, bannedIn, "LEA", LocalDate.of(1993, 8, 5));
    }
}
