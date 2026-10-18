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
        MtgJsonCard wastes = card("Wastes", false, false, null);
        MtgJsonCard forest = card("Forest", true, false, MtgFormat.LEGACY);
        MtgJsonCard wasteland = card("Wasteland", true, false, null);
        MtgJsonCard shahrazad = card("Shahrazad", false, false, null);
        MtgJsonCard lightningBolt = card("Lightning Bolt", true, false, null);
        MtgJsonCard blackLotus = card("Black Lotus", true, true, null);
        MtgJsonCard ragavan = card("Ragavan, Nimble Pilferer", true, false, null);
        MtgJsonCard lurrus = card("Lurrus of the Dream-Den", true, false, MtgFormat.MODERN);
        MtgJsonCard abrade = card("Abrade", true, false, null);

        assertEquals(Legality.LEGAL, CasualChallengeRules.legalityOf(wastes, 0, true, true));
        assertEquals(Legality.LEGAL, CasualChallengeRules.legalityOf(forest, null, true, true));
        assertEquals(Legality.BANNED, CasualChallengeRules.legalityOf(wasteland, 900, true, false));
        assertEquals(Legality.NOT_LEGAL, CasualChallengeRules.legalityOf(shahrazad, 100, true, true));
        assertEquals(Legality.NOT_LEGAL, CasualChallengeRules.legalityOf(lightningBolt, null, true, true));
        assertEquals(Legality.NOT_LEGAL, CasualChallengeRules.legalityOf(lightningBolt, 0, true, true));
        assertEquals(Legality.BANNED, CasualChallengeRules.legalityOf(blackLotus, 100000, false, false));
        assertEquals(Legality.BANNED, CasualChallengeRules.legalityOf(ragavan, 4000, true, true));
        assertEquals(Legality.BANNED, CasualChallengeRules.legalityOf(lurrus, 600, false, true));
        assertEquals(Legality.EXTENDED, CasualChallengeRules.legalityOf(abrade, 50, false, true));
        assertEquals(Legality.LEGAL, CasualChallengeRules.legalityOf(abrade, 50, false, false));
    }

    private static MtgJsonCard card(
            String cardName,
            boolean isVintageLegal,
            boolean isVintageRestricted,
            MtgFormat bannedIn
    ) {
        return new MtgJsonCard(
                cardName,
                null,
                isVintageLegal,
                isVintageRestricted,
                bannedIn,
                "LEA",
                LocalDate.of(1993, 8, 5)
        );
    }
}
