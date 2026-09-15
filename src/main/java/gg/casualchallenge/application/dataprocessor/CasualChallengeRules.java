package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;

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

    private CasualChallengeRules() {}

    public static boolean isBasicLand(String cardName) {
        return BASIC_LANDS.contains(cardName);
    }

    public static boolean isFlipStyleName(String cardName) {
        if (cardName == null || !cardName.contains("//")) return false;

        // Since 09.08.2025 the first and LAST part have to match, which also covers flip-adventure cards
        // like https://scryfall.com/card/tdm/381/bloomvine-regent-claim-territory-bloomvine-regent
        String[] parts = cardName.split(" // ");
        return parts[0].trim().equals(parts[parts.length - 1].trim());
    }

    /** @param isVintageLegal MTGJSON leaves out formats a card is not legal in - restricted cards still count as legal */
    public static Legality legalityOf(
            String cardName,
            boolean isVintageLegal,
            boolean isVintageRestricted,
            MtgFormat bannedIn,
            Integer budgetPoints,
            boolean isCCBanned,
            boolean isCCExtendedBanned
    ) {
        if (isBasicLand(cardName)) return Legality.LEGAL;
        if (!isVintageLegal) return Legality.NOT_LEGAL;
        if (budgetPoints == null || budgetPoints == 0) return Legality.NOT_LEGAL;
        if (isVintageRestricted) return Legality.BANNED;
        if (isCCBanned) return Legality.BANNED;
        if (bannedIn != null) return Legality.BANNED;
        if (isCCExtendedBanned) return Legality.EXTENDED;

        return Legality.LEGAL;
    }
}
