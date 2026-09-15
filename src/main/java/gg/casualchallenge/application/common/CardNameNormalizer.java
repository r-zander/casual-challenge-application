package gg.casualchallenge.application.common;

import java.text.Normalizer;
import java.util.Locale;

public final class CardNameNormalizer {

    private CardNameNormalizer() {}

    /**
     * Normalize a card name by performing the following transformations:
     * 1. Replace diacritic characters (e.g. accents) with their base characters.
     * 2. Strip single quotes (according to scryfall's rules)
     * 3. Replace all non-alphanumeric characters with a dash '-' (e.g. "Card!" -> "Card-").
     * 4. Replace repeated dashes ("---") with a single dash ("-").
     * 5. Remove leading and trailing dashes.
     * 6. Convert the resulting string to lowercase.
     *
     * @param cardName The original card name to normalize
     * @return The normalized card name
     */
    public static String normalize(String cardName) {
        if (cardName == null || cardName.isEmpty()) {
            return "";
        }

        // Step: Decompose the string into its base characters and remove diacritic marks
        String normalized = Normalizer.normalize(cardName, Normalizer.Form.NFD);

        // Remove diacritic marks (Unicode category 'Mn')
        normalized = normalized.replaceAll("\\p{M}", "");

        // Step: Strip single quotes
        normalized = normalized.replaceAll("'", "");

        // Step: Replace non-alphanumeric characters with dashes
        normalized = normalized.replaceAll("[^a-zA-Z0-9]", "-");

        // Step: Replace multiple dashes with a single dash
        normalized = normalized.replaceAll("-+", "-");

        // Step: Remove leading and trailing dashes
        normalized = normalized.replaceAll("^-|-$", "");

        // Step: Convert to lowercase
        return normalized.toLowerCase(Locale.ENGLISH);
    }
}
