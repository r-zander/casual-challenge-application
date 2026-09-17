package gg.casualchallenge.application.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardNameNormalizerTest {

    @Test
    void testNormalize() {
        assertEquals("lightning-bolt", CardNameNormalizer.normalize("Lightning Bolt"));
        assertEquals("black-lotus", CardNameNormalizer.normalize("BLACK LOTUS"));
        assertEquals("innkeepers-talent", CardNameNormalizer.normalize("Innkeeper's Talent"));
        assertEquals("fire-ice", CardNameNormalizer.normalize("Fire // Ice"));
        assertEquals("delver-of-secrets-insectile-aberration", CardNameNormalizer.normalize("Delver of Secrets // Insectile Aberration"));
        assertEquals("a-b", CardNameNormalizer.normalize("A---B"));
        assertEquals("test", CardNameNormalizer.normalize("-test-"));
        assertEquals("", CardNameNormalizer.normalize(null));
        assertEquals("", CardNameNormalizer.normalize(""));
    }

    @Test
    void testNormalize_withDiacritics() {
        assertEquals("lorien-revealed", CardNameNormalizer.normalize("Lórien Revealed"));
        assertEquals("troll-of-khazad-dum", CardNameNormalizer.normalize("Troll of Khazad-dûm"));
        assertEquals("juzam-djinn", CardNameNormalizer.normalize("Juzám Djinn"));
    }
}
