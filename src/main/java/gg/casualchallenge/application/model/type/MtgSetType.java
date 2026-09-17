package gg.casualchallenge.application.model.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MtgSetType {
    ALCHEMY,
    ARCHENEMY,
    ARSENAL,
    BOX,
    COMMANDER,
    CORE,
    DRAFT_INNOVATION,
    DUEL_DECK,
    ETERNAL,
    EXPANSION,
    FROM_THE_VAULT,
    FUNNY,
    MASTERPIECE,
    MASTERS,
    MEMORABILIA,
    MINIGAME,
    PLANECHASE,
    PREMIUM_DECK,
    PROMO,
    SPELLBOOK,
    STARTER,
    TOKEN,
    TREASURE_CHEST,
    VANGUARD,
    ;

    public static MtgSetType fromMtgJson(String setType) {
        if (setType == null) throw new IllegalArgumentException("A set in AllPrintings.json comes without a type at all, and guessing one is not our job.");

        try {
            return valueOf(setType.toUpperCase());
        } catch (IllegalArgumentException e) {
            // A new set type could be another joke set, and those must not win the card identity, so stop instead of guessing
            throw new IllegalArgumentException("MTGJSON came up with the set type '" + setType + "'. Add it here and decide whether it belongs into MtgJsonClient.IGNORED_SET_TYPES.", e);
        }
    }

    @JsonValue
    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
