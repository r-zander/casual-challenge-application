package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.CardPrices;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonCard;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPrinting;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPrintingsVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonSet;
import gg.casualchallenge.application.dataprocessor.model.PriceWindowVO;
import gg.casualchallenge.application.model.type.MtgFormat;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MtgJsonClientTest {

    private final MtgJsonClient mtgJsonClient = new MtgJsonClient("https://mtgjson.com/api/v5", System.getProperty("java.io.tmpdir"));

    @Test
    void testReadPrintings() {
        String allPrintings = """
                {
                  "meta": {"date": "2026-09-13", "version": "5.2.2+20260913"},
                  "data": {
                    "C13": {
                      "cards": [
                        {"availability": ["paper"], "borderColor": "black", "finishes": ["nonfoil"], "identifiers": {"scryfallOracleId": "6ad8011d-3471-4369-9d68-b264cc027487"}, "legalities": {"legacy": "Banned", "vintage": "Restricted"}, "name": "Sol Ring", "number": "254", "uuid": "8f6a9d1c-4b3e-5a7d-9c21-6e0f5a2b7d43"},
                        {"availability": ["paper"], "borderColor": "black", "finishes": ["nonfoil"], "identifiers": {"scryfallOracleId": "6ad8011d-3471-4369-9d68-b264cc027487"}, "isOversized": true, "legalities": {"legacy": "Banned", "vintage": "Restricted"}, "name": "Sol Ring", "number": "254b", "uuid": "2d7b4e90-1a6c-5f38-b4e7-9c1d05a6f2b8"}
                      ],
                      "code": "C13", "isOnlineOnly": false, "name": "Commander 2013", "releaseDate": "2013-11-01", "type": "commander"
                    },
                    "CMB1": {
                      "cards": [
                        {"availability": ["paper"], "borderColor": "black", "finishes": ["nonfoil"], "identifiers": {"scryfallOracleId": "0c94cb69-4ee5-4e2a-9b1c-9b4ab4bdc5e5"}, "legalities": {}, "name": "Red Herring", "number": "76", "uuid": "5a1e3c72-9d84-5b06-a1f3-7e2c48d905b1"}
                      ],
                      "code": "CMB1", "isOnlineOnly": false, "name": "Mystery Booster Playtest Cards 2019", "parentCode": "MB1", "releaseDate": "2019-11-07", "type": "funny"
                    },
                    "LEA": {
                      "cards": [
                        {"availability": ["paper"], "borderColor": "black", "finishes": ["nonfoil"], "foreignData": [{"language": "German", "name": "Chaoskugel"}], "identifiers": {"scryfallOracleId": "edb455f4-8dc9-4b7c-b25c-cb51b7dfbb41"}, "legalities": {"legacy": "Banned", "vintage": "Banned"}, "name": "Chaos Orb", "number": "13", "uuid": "9c04a7e1-3b58-5d26-af19-0e7d61c452b3"},
                        {"availability": ["paper"], "borderColor": "black", "finishes": ["nonfoil"], "identifiers": {"scryfallOracleId": "6ad8011d-3471-4369-9d68-b264cc027487"}, "legalities": {"legacy": "Banned", "vintage": "Restricted"}, "name": "Sol Ring", "number": "258", "uuid": "1f3c8b2a-6e45-59d7-8c10-b4a2e76f3d09"}
                      ],
                      "code": "LEA", "isOnlineOnly": false, "name": "Limited Edition Alpha", "releaseDate": "1993-08-05", "tokens": [], "type": "core"
                    },
                    "MKM": {
                      "cards": [
                        {"availability": ["arena"], "borderColor": "black", "finishes": ["nonfoil"], "identifiers": {"scryfallOracleId": "d0f6b1e2-5c3a-4f19-8e07-2b6a9d4c1f83"}, "isRebalanced": true, "legalities": {}, "name": "A-Case of the Gorgon's Kiss", "number": "A-84", "uuid": "4e8d1b53-2f70-5a94-b6c8-1d59f0a83e27"},
                        {"availability": ["mtgo", "paper"], "borderColor": "black", "finishes": ["nonfoil", "foil"], "identifiers": {"scryfallOracleId": "f0ee45b5-697b-48f8-b1a6-cb3ae75e4a29"}, "legalities": {"pauper": "Banned", "standard": "Banned", "vintage": "Legal"}, "name": "Red Herring", "number": "90", "uuid": "6b2f90d4-7c13-5e48-9a05-3d8b17e6c204"}
                      ],
                      "code": "MKM", "decks": [{"code": "MKM", "name": "Deadly Disguise", "releaseDate": "2024-02-09", "type": "Commander Deck"}],
                      "isOnlineOnly": false, "name": "Murders at Karlov Manor", "releaseDate": "2024-02-09", "type": "expansion"
                    },
                    "SUM": {
                      "cards": [
                        {"availability": ["paper"], "borderColor": "black", "finishes": ["nonfoil"], "identifiers": {"scryfallOracleId": "02418479-9455-417f-a6a1-004356faff37"}, "legalities": {"legacy": "Legal", "vintage": "Legal"}, "name": "Tundra", "number": "281", "uuid": "3a5c7e11-8d92-5064-bf37-2c8016d4a95e"}
                      ],
                      "code": "SUM", "isOnlineOnly": false, "name": "Summer Magic / Edgar", "releaseDate": "1994-07-01", "type": "core"
                    },
                    "UGL": {
                      "cards": [
                        {"availability": ["paper"], "borderColor": "silver", "finishes": ["nonfoil"], "identifiers": {"scryfallOracleId": "8d4a1c07-3f52-4b6e-91a8-5c0e7d2b4396"}, "legalities": {}, "name": "Bee-Bee Gun", "number": "71", "uuid": "7d09f3b6-5c28-541a-93e0-8b6f21c07d4a"}
                      ],
                      "code": "UGL", "isOnlineOnly": false, "name": "Unglued", "releaseDate": "1998-08-11", "type": "funny"
                    }
                  }
                }""";

        MtgJsonPrintingsVO printings = mtgJsonClient.readPrintings(toStream(allPrintings));

        assertEquals(LocalDate.of(2026, 9, 13), printings.getMetaDate());
        assertEquals("5.2.2+20260913", printings.getMetaVersion());
        assertEquals(List.of("Sol Ring", "Chaos Orb", "Red Herring", "Tundra"), new ArrayList<>(printings.getCardsByName().keySet()));
        assertEquals(5, printings.getPrintingsByUuid().size());
        assertFalse(printings.getPrintingsByUuid().containsKey("2d7b4e90-1a6c-5f38-b4e7-9c1d05a6f2b8"));
        assertFalse(printings.getPrintingsByUuid().containsKey("4e8d1b53-2f70-5a94-b6c8-1d59f0a83e27"));
        assertFalse(printings.getPrintingsByUuid().containsKey("7d09f3b6-5c28-541a-93e0-8b6f21c07d4a"));
        assertFalse(printings.getPrintingsByUuid().containsKey("3a5c7e11-8d92-5064-bf37-2c8016d4a95e"));

        MtgJsonCard solRing = printings.getCardsByName().get("Sol Ring");
        assertEquals("LEA", solRing.getFirstSetCode());
        assertEquals(LocalDate.of(1993, 8, 5), solRing.getFirstReleaseDate());
        assertTrue(solRing.isVintageLegal());
        assertTrue(solRing.isVintageRestricted());
        assertEquals(MtgFormat.LEGACY, solRing.getBannedIn());

        MtgJsonCard chaosOrb = printings.getCardsByName().get("Chaos Orb");
        assertTrue(chaosOrb.isVintageLegal());
        assertFalse(chaosOrb.isVintageRestricted());
        assertEquals(MtgFormat.LEGACY, chaosOrb.getBannedIn());

        MtgJsonCard redHerring = printings.getCardsByName().get("Red Herring");
        assertEquals(UUID.fromString("f0ee45b5-697b-48f8-b1a6-cb3ae75e4a29"), redHerring.getOracleId());
        assertEquals("MKM", redHerring.getFirstSetCode());
        assertEquals(MtgFormat.STANDARD, redHerring.getBannedIn());
        assertTrue(printings.getPrintingsByUuid().get("6b2f90d4-7c13-5e48-9a05-3d8b17e6c204").isFoil());
        assertFalse(printings.getPrintingsByUuid().get("5a1e3c72-9d84-5b06-a1f3-7e2c48d905b1").isFoil());

        assertEquals("SUM", printings.getCardsByName().get("Tundra").getFirstSetCode());

        assertEquals(6, printings.getSets().size());
        assertEquals("MB1", printings.getSets().get(1).getParentCode());
        MtgJsonSet karlovManor = printings.getSets().get(3);
        assertEquals("Murders at Karlov Manor", karlovManor.getName());
        assertEquals("expansion", karlovManor.getType());
        assertEquals(LocalDate.of(2024, 2, 9), karlovManor.getReleaseDate());
        assertFalse(karlovManor.isOnlineOnly());
        assertEquals(1, karlovManor.getDecks().size());
        assertEquals("Deadly Disguise", karlovManor.getDecks().get(0).getName());
        assertEquals("Commander Deck", karlovManor.getDecks().get(0).getType());
        assertEquals(LocalDate.of(2024, 2, 9), karlovManor.getDecks().get(0).getReleaseDate());
    }

    @Test
    void testReadPrices() {
        String allPrices = """
                {
                  "meta": {"date": "2025-06-07", "version": "5.2.2+20250607"},
                  "data": {
                    "1f3c8b2a-6e45-59d7-8c10-b4a2e76f3d09": {
                      "mtgo": {"cardhoarder": {"retail": {"normal": {"2025-06-04": 7.0}}}},
                      "paper": {
                        "cardkingdom": {"retail": {"normal": {"2025-06-04": 0.01}}},
                        "cardmarket": {
                          "buylist": {"normal": {"2025-06-04": 0.1}},
                          "currency": "EUR",
                          "retail": {"foil": {"2025-06-04": 0.5}, "normal": {"2025-06-03": 9.0, "2025-06-04": 1.0, "2025-06-05": null, "2025-06-06": 3.0, "2025-06-07": 99.0}}
                        },
                        "tcgplayer": {"currency": "USD", "retail": {"normal": {"2025-06-05": 2.0}}}
                      }
                    },
                    "8f6a9d1c-4b3e-5a7d-9c21-6e0f5a2b7d43": {
                      "paper": {
                        "cardmarket": {"retail": {"foil": {"2025-06-04": 50.0, "2025-06-05": 50.0}, "normal": {"2025-06-04": 2.0}}},
                        "tcgplayer": {"retail": {"normal": {"2025-01-01": 5.0}}}
                      }
                    },
                    "6b2f90d4-7c13-5e48-9a05-3d8b17e6c204": {"paper": {"cardmarket": {"retail": {"normal": {"2025-06-04": 0.25}}}}}
                  }
                }""";

        Map<String, MtgJsonPrinting> printingsByUuid = new HashMap<>();
        printingsByUuid.put("1f3c8b2a-6e45-59d7-8c10-b4a2e76f3d09", new MtgJsonPrinting("1f3c8b2a-6e45-59d7-8c10-b4a2e76f3d09", "Sol Ring", false, true));
        printingsByUuid.put("8f6a9d1c-4b3e-5a7d-9c21-6e0f5a2b7d43", new MtgJsonPrinting("8f6a9d1c-4b3e-5a7d-9c21-6e0f5a2b7d43", "Sol Ring", true, true));
        PriceWindowVO window = PriceWindowVO.of(LocalDate.of(2025, 6, 7), 3);

        Map<String, CardPrices> pricesByCardName = mtgJsonClient.readPrices(toStream(allPrices), printingsByUuid, window);

        assertEquals(Set.of("Sol Ring"), pricesByCardName.keySet());
        CardPrices solRing = pricesByCardName.get("Sol Ring");
        assertEquals(3, solRing.getEur().getPrintingCount());
        assertEquals(2, solRing.getEur().getFlatPrintingCount());
        assertEquals(2.0, solRing.getEur().average());
        assertEquals(2, solRing.getUsd().getPrintingCount());
        assertEquals(0.0, solRing.getUsd().average());
    }

    private static InputStream toStream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

}
