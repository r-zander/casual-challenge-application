package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.MtgJsonCard;
import gg.casualchallenge.application.model.type.Legality;

import java.util.Set;

public final class CasualChallengeRules {

    private static final Set<String> BASIC_LANDS = Set.of(
            "Plains",
            "Island",
            "Swamp",
            "Mountain",
            "Forest",
            "Wastes"
    );

    // Fuck those cards... They are "funny" cards those normalized names clash with actual cards,
    // so they are not getting fully normalized. If someone tries to find them, better be very specific
    // (jokes on you, normalization on the API won't allow that)
    private static final Set<String> COMMA_CARDS = Set.of(
            "Rampant, Growth",
            "Lava, Axe",
            "Gather, the Townsfolk",
            "Glimpse, the Unthinkable",
            "Ransack, the Lab",
            "Clear, the Mind"
    );

    private CasualChallengeRules() {}

    public static boolean isBasicLand(String cardName) {
        return BASIC_LANDS.contains(cardName);
    }

    public static boolean isCommaCard(String cardName) {
        return COMMA_CARDS.contains(cardName);
    }

    public static boolean isFlipStyleName(String cardName) {
        if (cardName == null || !cardName.contains("//")) return false;

        // Since 09.08.2025 the first and LAST part have to match, which also covers flip-adventure cards
        // like https://scryfall.com/card/tdm/381/bloomvine-regent-claim-territory-bloomvine-regent
        String[] parts = cardName.split(" // ");
        return parts[0].trim().equals(parts[parts.length - 1].trim());
    }

    /** MTGJSON leaves out formats a card is not legal in - restricted cards still count as legal */
    public static Legality legalityOf(MtgJsonCard card, Integer budgetPoints, boolean isCCBanned, boolean isCCExtendedBanned) {
        if (isBasicLand(card.getName())) return Legality.LEGAL;
        if (!card.isVintageLegal()) return Legality.NOT_LEGAL;
        if (budgetPoints == null || budgetPoints == 0) return Legality.NOT_LEGAL;
        if (card.isVintageRestricted()) return Legality.BANNED;
        if (isCCBanned) return Legality.BANNED;
        if (card.getBannedIn() != null) return Legality.BANNED;
        if (isCCExtendedBanned) return Legality.EXTENDED;

        return Legality.LEGAL;
    }
}
