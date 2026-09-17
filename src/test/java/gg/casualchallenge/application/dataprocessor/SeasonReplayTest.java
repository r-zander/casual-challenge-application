package gg.casualchallenge.application.dataprocessor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.common.SeasonDates;
import gg.casualchallenge.application.dataprocessor.model.AssembledDraftVO;
import gg.casualchallenge.application.dataprocessor.model.CardPrices;
import gg.casualchallenge.application.dataprocessor.model.Cents;
import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import gg.casualchallenge.application.dataprocessor.model.MetaSharesVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPrinting;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPricesVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPrintingsVO;
import gg.casualchallenge.application.dataprocessor.model.PriceWindowVO;
import gg.casualchallenge.application.model.values.SeasonDraftCardVO;
import gg.casualchallenge.application.model.values.SeasonPreparationRequestVO;
import gg.casualchallenge.application.persistence.entity.Season;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// ./gradlew replayTest -Dreplay.dir=C:\Users\XieLong\workspaces\chrome-extension-casual-challenge
@Tag("replay")
@EnabledIfSystemProperty(named = "replay.dir", matches = ".+")
class SeasonReplayTest {

    private static final Path REPLAY_DIRECTORY = Paths.get(System.getProperty("replay.dir", ""));
    private static final Path SEASON_21_DIRECTORY = Paths.get("temp", "season-21-expected");

    private static final String PRINTINGS_FILE = "AllPrintings.json";

    private static final int PRICE_WINDOW_DAYS = 70;
    private static final SeasonDates SEASON_DATES = new SeasonDates(10, DayOfWeek.FRIDAY);
    private static final int MAX_PRINTED_LINES = 20;

    // added_at is the timestamp of the run, there is nothing to compare there
    private static final String[] CARD_COLUMNS = {"oracle_id", "name", "normalized_name", null};
    private static final String[] CARD_SEASON_DATA_COLUMNS = {
            "season_id", "card_oracle_id", "budget_points", "legality",
            "meta_share_standard", "meta_share_pioneer", "meta_share_modern", "meta_share_legacy", "meta_share_vintage", "meta_share_pauper",
            "banned_in", "vintage_restricted"
    };

    // The python treats Wasteland as a basic land (a typo that survived twenty seasons) and doesn't know that Wastes is one.
    // Millicent and Strefan are only "not legal" for it because their first printing in file order is the oversized OVOC one, which carries no legalities at all.
    // The season 21 ban files spell Dain's Company and Kili the Resourceful without their accents, the python matched the raw names and missed both, our normalizer doesn't.
    private static final Set<String> FIXED_BY_US = Set.of("Wasteland", "Wastes", "Millicent, Restless Revenant", "Strefan, Maurer Progenitor", "Dáin's Company", "Kíli the Resourceful");

    // Season 21 was built in September 2026, the newest AllPrintings.json we have is from November 2025 --> everything MTGJSON decided in between lands in these columns
    private static final Set<String> DRIFTING_COLUMNS = Set.of("name", "normalized_name", "legality", "banned_in", "vintage_restricted");

    private static final LocalDateTime ADDED_AT = LocalDateTime.of(2026, 9, 15, 12, 0);

    private final MtgJsonClient mtgJsonClient = new MtgJsonClient("https://mtgjson.com/api/v5", System.getProperty("java.io.tmpdir"));
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new ParameterNamesModule()); // the ban files map onto constructors, just like they do in the controller

    @Test
    void testBudgetPoints_season18() throws Exception {
        Path budgetPointDirectory = REPLAY_DIRECTORY.resolve("janik-budgetpoints");
        LocalDate startDate = LocalDate.of(2025, 6, 7);
        PriceWindowVO priceWindow = PriceWindowVO.of(startDate, PRICE_WINDOW_DAYS);

        MtgJsonPrintingsVO printings = readPrintings(budgetPointDirectory.resolve("AllPrintings.json"));
        Map<String, CardPrices> pricesByCardName = readPrices(budgetPointDirectory.resolve("AllPrices.json"), printings, priceWindow);

        List<SeasonDraftCardVO> cards = assemble(printings, pricesByCardName, MetaSharesVO.fromBanFiles(List.of(), List.of()), startDate, 17);
        BudgetPointDiff diff = compareBudgetPoints(cards, printings, pricesByCardName, readBudgetPoints(REPLAY_DIRECTORY.resolve("data-prep-api/season-18/card-prices.json")));

        print("Season 18 budget points", diff);
        printHeap();

        assertTrue(diff.equal > 29000);
        assertEquals(0, diff.different.size());
        assertEquals(0, diff.offByOne.size());
        assertTrue(diff.halfCentTies.size() <= 10);
        assertTrue(diff.unknownToUs.size() + diff.pricedByUsOnly.size() <= 10);
        assertTrue(diff.droppedByIdentityRule.size() <= 1000);
    }

    @Test
    void testSeasonData_season19() throws Exception {
        Path seasonDirectory = REPLAY_DIRECTORY.resolve("data-prep-api/season-19");
        Path outputDirectory = REPLAY_DIRECTORY.resolve("data-prep-api/output/season-19");

        Path allPrintingsJson = printingsOf(seasonDirectory);
        System.out.println("Season 19 using " + REPLAY_DIRECTORY.relativize(allPrintingsJson) + ".");
        MtgJsonPrintingsVO printings = readPrintings(allPrintingsJson);
        MetaSharesVO metaShares = MetaSharesVO.fromBanFiles(readBans(seasonDirectory.resolve("bans.json")), readBans(seasonDirectory.resolve("extended-bans.json")));
        Map<String, CardPrices> pricesByCardName = toCardPrices(readBudgetPoints(seasonDirectory.resolve("card-prices.json")));

        List<SeasonDraftCardVO> cards = assemble(printings, pricesByCardName, metaShares, LocalDate.of(2025, 11, 17), 18);

        Map<String, String[]> ourCards = rowsByOracleId(SeasonMigrationSql.insertCards(cards, ADDED_AT), 0);
        Map<String, String[]> theirCards = rowsByOracleId(Files.readString(outputDirectory.resolve("20251119_0103_01_insert_cards.sql")), 0);
        Map<String, String> ourNames = namesByOracleId(ourCards);
        Map<String, String> theirNames = namesByOracleId(theirCards);
        ReplayDiff cardDiff = compare(ourCards, theirCards, ourNames, theirNames, CARD_COLUMNS, FIXED_BY_US, Set.of());
        ReplayDiff seasonDataDiff = compare(
                rowsByOracleId(SeasonMigrationSql.insertCardSeasonData(19, cards), 1),
                rowsByOracleId(Files.readString(outputDirectory.resolve("20251119_0103_02_insert_card_season_data_for_season_19.sql")), 1),
                ourNames,
                theirNames,
                CARD_SEASON_DATA_COLUMNS,
                FIXED_BY_US,
                Set.of()
        );

        print("Season 19 card", cardDiff);
        print("Season 19 card_season_data", seasonDataDiff);
        printHeap();

        assertTrue(cardDiff.equal > 30000);
        assertTrue(seasonDataDiff.equal > 30000);
        assertEquals(0, cardDiff.differences.size());
        assertEquals(0, cardDiff.tolerated.size());
        assertEquals(0, seasonDataDiff.differences.size());
        assertEquals(4, seasonDataDiff.tolerated.size());
        assertTrue(cardDiff.newOracleIds.size() <= 15);
        assertTrue(seasonDataDiff.newOracleIds.size() <= 15);
        assertTrue(cardDiff.ourOnly.size() <= 10);
        assertTrue(seasonDataDiff.ourOnly.size() <= 10);
        assertTrue(cardDiff.theirOnly.size() <= 1000); // the cards the identity rule drops
        assertTrue(seasonDataDiff.theirOnly.size() <= 1000);
    }

    @Test
    void testSeasonData_season21() throws Exception {
        Path seasonDirectory = REPLAY_DIRECTORY.resolve("data-prep-api/season-21");

        // Without the season's own dump the newest AllPrintings.json we have is from November 2025, while the season 21 files were built in September 2026
        Path allPrintingsJson = printingsOf(seasonDirectory);
        boolean isSeasonSnapshot = allPrintingsJson.startsWith(seasonDirectory);
        System.out.println("Season 21 using " + REPLAY_DIRECTORY.relativize(allPrintingsJson) + ".");
        LocalDate startDate = LocalDate.of(2026, 9, 13);
        MtgJsonPrintingsVO printings = readPrintings(allPrintingsJson);
        MetaSharesVO metaShares = MetaSharesVO.fromBanFiles(readBans(seasonDirectory.resolve("bans.json")), readBans(seasonDirectory.resolve("extended-bans.json")));
        Map<String, Integer> theirBudgetPoints = readBudgetPoints(seasonDirectory.resolve("card-prices.json"));

        // The season's own AllPrices.json covers exactly the window the python ran on (20260705 to 20260913) --> we can price the cards ourselves instead of believing its output
        Path allPricesJson = seasonDirectory.resolve("AllPrices.json");
        boolean arePricesOurs = Files.exists(allPricesJson);
        Map<String, CardPrices> pricesByCardName = arePricesOurs ? readPrices(allPricesJson, printings, PriceWindowVO.of(startDate, PRICE_WINDOW_DAYS)) : toCardPrices(theirBudgetPoints);

        List<SeasonDraftCardVO> cards = assemble(printings, pricesByCardName, metaShares, startDate, 20);

        Set<String> pricedDifferently = Set.of();
        Set<String> toleratedSeasonData = FIXED_BY_US;
        if (arePricesOurs) {
            BudgetPointDiff budgetPointDiff = compareBudgetPoints(cards, printings, pricesByCardName, theirBudgetPoints);
            print("Season 21 budget points", budgetPointDiff);
            pricedDifferently = budgetPointDiff.pricedDifferently;
            toleratedSeasonData = new HashSet<>(FIXED_BY_US); // the budget points we price differently are explained above --> the rows don't need to list them a second time
            toleratedSeasonData.addAll(pricedDifferently);

            assertTrue(budgetPointDiff.equal > 30000);
            assertEquals(0, budgetPointDiff.different.size());
            assertEquals(0, budgetPointDiff.offByOne.size());
            assertTrue(budgetPointDiff.halfCentTies.size() <= 10);
            assertTrue(budgetPointDiff.unknownToUs.size() + budgetPointDiff.pricedByUsOnly.size() <= 10);
            assertTrue(budgetPointDiff.droppedByIdentityRule.size() <= 1000);
        }

        Map<String, String[]> ourCards = rowsByOracleId(SeasonMigrationSql.insertCards(cards, ADDED_AT), 0);
        Map<String, String[]> theirCards = rowsByOracleId(Files.readString(SEASON_21_DIRECTORY.resolve("20260913_2021_01_insert_cards.sql")), 0);
        Map<String, String> ourNames = namesByOracleId(ourCards);
        Map<String, String> theirNames = namesByOracleId(theirCards);
        ReplayDiff cardDiff = compare(ourCards, theirCards, ourNames, theirNames, CARD_COLUMNS, FIXED_BY_US, DRIFTING_COLUMNS);
        ReplayDiff seasonDataDiff = compare(
                rowsByOracleId(SeasonMigrationSql.insertCardSeasonData(21, cards), 1),
                rowsByOracleId(Files.readString(SEASON_21_DIRECTORY.resolve("20260913_2021_02_insert_card_season_data_for_season_21.sql")), 1),
                ourNames,
                theirNames,
                CARD_SEASON_DATA_COLUMNS,
                toleratedSeasonData,
                DRIFTING_COLUMNS
        );

        print("Season 21 card", cardDiff);
        print("Season 21 card_season_data", seasonDataDiff);
        printHeap();

        // Tolerating a card by name would swallow a second difference on the same row --> the repriced ones may only differ in their budget points
        for (String cardName : pricedDifferently) {
            for (String toleratedLine : seasonDataDiff.tolerated) {
                if (!toleratedLine.startsWith(cardName + ": ")) continue;
                assertTrue(toleratedLine.startsWith(cardName + ": budget_points ") && toleratedLine.indexOf(", ", cardName.length()) < 0, toleratedLine);
            }
        }

        assertTrue(cardDiff.equal > 30000);
        assertTrue(seasonDataDiff.equal > 30000);
        assertEquals(0, cardDiff.differences.size());
        assertEquals(0, seasonDataDiff.differences.size());
        assertTrue(cardDiff.newOracleIds.size() <= 15);
        assertTrue(seasonDataDiff.newOracleIds.size() <= 15);
        assertTrue(cardDiff.ourOnly.size() <= 10);
        assertTrue(seasonDataDiff.ourOnly.size() <= 10);
        if (isSeasonSnapshot) {
            assertEquals(0, cardDiff.drifted.size());
            assertEquals(0, seasonDataDiff.drifted.size());
            assertTrue(cardDiff.theirOnly.size() <= 1000); // only the cards the identity rule drops
            assertTrue(seasonDataDiff.theirOnly.size() <= 1000);
        } else {
            assertTrue(seasonDataDiff.drifted.size() <= 200);
            assertTrue(cardDiff.theirOnly.size() <= 3500); // the identity drops plus everything printed after November 2025
            assertTrue(seasonDataDiff.theirOnly.size() <= 3500);
        }
    }

    // The seasons come with their own MTGJSON dump once somebody put one next to their json files
    private static Path printingsOf(Path seasonDirectory) {
        Path seasonPrintings = seasonDirectory.resolve(PRINTINGS_FILE);
        if (Files.exists(seasonPrintings)) return seasonPrintings;

        return REPLAY_DIRECTORY.resolve("data-prep-api").resolve(PRINTINGS_FILE);
    }

    private MtgJsonPrintingsVO readPrintings(Path allPrintingsJson) throws IOException {
        try (InputStream printings = new BufferedInputStream(Files.newInputStream(allPrintingsJson))) {
            return mtgJsonClient.readPrintings(printings);
        }
    }

    private Map<String, CardPrices> readPrices(Path allPricesJson, MtgJsonPrintingsVO printings, PriceWindowVO window) throws IOException {
        try (InputStream prices = new BufferedInputStream(Files.newInputStream(allPricesJson))) {
            return mtgJsonClient.readPrices(prices, printings.getPrintingsByUuid(), window).getPricesByCardName();
        }
    }

    private Map<String, Integer> readBudgetPoints(Path cardPricesJson) throws IOException {
        return objectMapper.readValue(cardPricesJson.toFile(), new TypeReference<Map<String, Integer>>() {});
    }

    private List<BanDTO> readBans(Path bansJson) throws IOException {
        return objectMapper.readValue(bansJson.toFile(), new TypeReference<List<BanDTO>>() {});
    }

    // Cards without a cardmarket price are converted from tcgplayer, and that rate is an average over every card we keep --> the python's differs by the cards it keeps that we don't
    private static boolean isExchangeRatePrice(String cardName, CardPrices cardPrices) {
        if (cardPrices == null || CasualChallengeRules.isBasicLand(cardName)) return false;

        return cardPrices.getEur().sumOfCheapest().getAmount() == 0 && cardPrices.getUsd().sumOfCheapest().getAmount() > 0;
    }

    // Half a cent sits exactly between two budget points: we round it to the even one, the python's floats went whichever way
    private static boolean isHalfCentTie(CardPrices cardPrices) {
        if (cardPrices == null) return false;

        int days = cardPrices.getEur().pricedDays();
        if (days == 0) return false;

        long sumOfCents = cardPrices.getEur().sumOfCheapest().getAmount();
        return 2 * sumOfCents % days == 0 && sumOfCents % days != 0;
    }

    // The budget points of seasons 19 and 21 are a given, so every card gets a one day price window holding exactly its known price
    private static Map<String, CardPrices> toCardPrices(Map<String, Integer> budgetPointsByCardName) {
        Map<String, CardPrices> pricesByCardName = new HashMap<>(budgetPointsByCardName.size());
        for (Map.Entry<String, Integer> entry : budgetPointsByCardName.entrySet()) {
            CardPrices cardPrices = new CardPrices(1);
            cardPrices.getEur().addPrinting(new Cents[]{Cents.of(entry.getValue())});
            pricesByCardName.put(entry.getKey(), cardPrices);
        }

        return pricesByCardName;
    }

    private static List<SeasonDraftCardVO> assemble(
            MtgJsonPrintingsVO printings,
            Map<String, CardPrices> pricesByCardName,
            MetaSharesVO metaShares,
            LocalDate startDate,
            int previousSeasonNumber
    ) {
        Season previousSeason = new Season();
        previousSeason.setId(previousSeasonNumber);
        previousSeason.setSeasonNumber(previousSeasonNumber);
        previousSeason.setStartDate(startDate.minusWeeks(10));
        previousSeason.setEndDate(startDate.minusDays(1));
        previousSeason.setUpdatedAt(startDate.minusWeeks(10).atStartOfDay());

        SeasonPreparationRequestVO request = new SeasonPreparationRequestVO(
                startDate,
                SEASON_DATES.defaultEndDate(previousSeason.getEndDate()),
                PriceWindowVO.of(startDate, PRICE_WINDOW_DAYS),
                MetaShareSource.FILES,
                List.of(),
                List.of(),
                null
        );

        AssembledDraftVO assembledDraft = SeasonPreparationService.assemble(printings, new MtgJsonPricesVO(pricesByCardName, PRICE_WINDOW_DAYS), metaShares, List.of(), List.of(), request, previousSeason, SEASON_DATES);
        return assembledDraft.getCards();
    }

    private static BudgetPointDiff compareBudgetPoints(
            List<SeasonDraftCardVO> cards,
            MtgJsonPrintingsVO printings,
            Map<String, CardPrices> pricesByCardName,
            Map<String, Integer> theirBudgetPoints
    ) {
        Map<String, Integer> ourBudgetPoints = new HashMap<>(cards.size());
        for (SeasonDraftCardVO card : cards) {
            ourBudgetPoints.put(card.getName(), card.getBudgetPoints());
        }

        Set<String> printedNames = new HashSet<>(printings.getPrintingsByUuid().size());
        for (MtgJsonPrinting printing : printings.getPrintingsByUuid().values()) {
            printedNames.add(printing.getCardName());
        }

        BudgetPointDiff diff = new BudgetPointDiff();
        for (Map.Entry<String, Integer> entry : theirBudgetPoints.entrySet()) {
            String cardName = entry.getKey();
            if (CasualChallengeRules.isFlipStyleName(cardName)) continue; // the python leaves those out of the season data as well

            Integer budgetPoints = ourBudgetPoints.remove(cardName);
            if (budgetPoints == null) {
                if (printedNames.contains(cardName)) diff.droppedByIdentityRule.add(cardName);
                else diff.unknownToUs.add(cardName);
                continue;
            }

            int difference = budgetPoints - entry.getValue();
            if (difference == 0) {
                diff.equal++;
            } else if (isExchangeRatePrice(cardName, pricesByCardName.get(cardName)) && Math.abs(difference) <= 1 + entry.getValue() / 100) {
                double drift = Math.abs((double) difference) / entry.getValue();
                if (drift > diff.worstDrift) diff.worstDrift = drift;
                diff.exchangeRateDrift.add(cardName + ": " + entry.getValue() + " --> " + budgetPoints);
                diff.pricedDifferently.add(cardName);
            } else if ((difference == 1 || difference == -1) && isHalfCentTie(pricesByCardName.get(cardName))) {
                diff.halfCentTies.add(cardName + ": " + entry.getValue() + " --> " + budgetPoints);
                diff.pricedDifferently.add(cardName);
            } else if (difference == 1 || difference == -1) {
                diff.offByOne.add(cardName + ": " + entry.getValue() + " --> " + budgetPoints);
            } else {
                diff.different.add(cardName + ": " + entry.getValue() + " --> " + budgetPoints);
            }
        }
        diff.pricedByUsOnly.addAll(ourBudgetPoints.keySet());

        return diff;
    }

    private static ReplayDiff compare(
            Map<String, String[]> ourRows,
            Map<String, String[]> theirRows,
            Map<String, String> ourNamesByOracleId,
            Map<String, String> theirNamesByOracleId,
            String[] columnNames,
            Set<String> toleratedNames,
            Set<String> driftingColumns
    ) {
        ReplayDiff diff = new ReplayDiff();
        for (Map.Entry<String, String[]> entry : theirRows.entrySet()) {
            String cardName = theirNamesByOracleId.getOrDefault(entry.getKey(), entry.getKey());
            String[] ourRow = ourRows.get(entry.getKey());
            if (ourRow == null) {
                diff.theirOnly.add(cardName);
                continue;
            }

            // A card MTGJSON renamed since loses its budget points as well, because the price file is keyed by the name it had back then
            boolean wasRenamed = !driftingColumns.isEmpty() && !cardName.equals(ourNamesByOracleId.get(entry.getKey()));
            String differences = differencesOf(ourRow, entry.getValue(), columnNames);
            if (differences == null) diff.equal++;
            else if (toleratedNames.contains(cardName)) diff.tolerated.add(cardName + ": " + differences);
            else if (wasRenamed || isOnlyDrift(ourRow, entry.getValue(), columnNames, driftingColumns)) diff.drifted.add(cardName + ": " + differences);
            else diff.differences.add(cardName + ": " + differences);
        }

        for (Map.Entry<String, String[]> entry : ourRows.entrySet()) {
            if (theirRows.containsKey(entry.getKey())) continue;
            diff.ourOnly.add(ourNamesByOracleId.getOrDefault(entry.getKey(), entry.getKey()));
        }

        // A card the identity rule pinned to a different printing shows up on both sides under the same name
        for (String cardName : new ArrayList<>(diff.ourOnly)) {
            if (!diff.theirOnly.remove(cardName)) continue;
            diff.ourOnly.remove(cardName);
            diff.newOracleIds.add(cardName);
        }

        return diff;
    }

    private static boolean isOnlyDrift(String[] ourRow, String[] theirRow, String[] columnNames, Set<String> driftingColumns) {
        if (driftingColumns.isEmpty() || ourRow.length != columnNames.length || theirRow.length != columnNames.length) return false;

        for (int column = 0; column < columnNames.length; column++) {
            if (columnNames[column] == null || isSameValue(columnNames[column], ourRow[column], theirRow[column])) continue;
            if (!driftingColumns.contains(columnNames[column])) return false;
        }

        return true;
    }

    private static String differencesOf(String[] ourRow, String[] theirRow, String[] columnNames) {
        if (ourRow.length != columnNames.length || theirRow.length != columnNames.length) {
            return "column count " + theirRow.length + " --> " + ourRow.length;
        }

        StringBuilder differences = new StringBuilder();
        for (int column = 0; column < columnNames.length; column++) {
            if (columnNames[column] == null || isSameValue(columnNames[column], ourRow[column], theirRow[column])) continue;

            if (differences.length() > 0) differences.append(", ");
            differences.append(columnNames[column]).append(" ").append(theirRow[column]).append(" --> ").append(ourRow[column]);
        }
        if (differences.length() == 0) return null;

        return differences.toString();
    }

    // The python writes the two decimals of the ban files, we write the three of the column
    private static boolean isSameValue(String columnName, String value, String otherValue) {
        if (value.equals(otherValue)) return true;

        return columnName.startsWith("meta_share") && isSameNumber(value, otherValue);
    }

    private static boolean isSameNumber(String value, String otherValue) {
        if ("NULL".equals(value) || "NULL".equals(otherValue)) return false;

        return new BigDecimal(value).compareTo(new BigDecimal(otherValue)) == 0;
    }

    private static Map<String, String[]> rowsByOracleId(String sql, int oracleIdColumn) {
        Map<String, String[]> rowsByOracleId = new LinkedHashMap<>();
        for (String line : sql.split("\n")) {
            String row = line.trim();
            if (!row.startsWith("(")) continue;

            String[] columns = splitColumns(row.substring(1, row.lastIndexOf(')')));
            rowsByOracleId.put(columns[oracleIdColumn].substring(1, 37), columns);
        }

        return rowsByOracleId;
    }

    // Card names hold commas and single quotes, so ", " only separates two columns outside of a quoted value
    private static String[] splitColumns(String row) {
        List<String> columns = new ArrayList<>();
        boolean isInsideQuotes = false;
        int start = 0;
        for (int index = 0; index < row.length(); index++) {
            if (row.charAt(index) == '\'') isInsideQuotes = !isInsideQuotes;
            if (isInsideQuotes || row.charAt(index) != ',' || index + 1 >= row.length() || row.charAt(index + 1) != ' ') continue;

            columns.add(row.substring(start, index));
            start = index + 2;
        }
        columns.add(row.substring(start));

        return columns.toArray(new String[0]);
    }

    private static Map<String, String> namesByOracleId(Map<String, String[]> cardRows) {
        Map<String, String> namesByOracleId = new HashMap<>(cardRows.size());
        for (Map.Entry<String, String[]> entry : cardRows.entrySet()) {
            String name = entry.getValue()[1];
            namesByOracleId.put(entry.getKey(), name.substring(1, name.length() - 1).replace("''", "'"));
        }

        return namesByOracleId;
    }

    private static void print(String title, ReplayDiff diff) {
        System.out.println(title + ": " + diff.equal + " equal, " + diff.differences.size() + " different, " + diff.tolerated.size() + " tolerated, "
                + diff.drifted.size() + " changed in MTGJSON since, " + diff.newOracleIds.size() + " with a new oracle id, "
                + diff.ourOnly.size() + " only ours, " + diff.theirOnly.size() + " only the python's.");
        printSome("  tolerated", diff.tolerated);
        printSome("  changed in MTGJSON since", diff.drifted);
        printSome("  new oracle id", diff.newOracleIds);
        printSome("  different", diff.differences);
        printSome("  only ours", diff.ourOnly);
        printSome("  only the python's", diff.theirOnly);
    }

    private static void print(String title, BudgetPointDiff diff) {
        System.out.println(title + ": " + diff.equal + " equal, " + diff.halfCentTies.size() + " half cent ties, " + diff.offByOne.size() + " off by one, " + diff.different.size() + " different, "
                + diff.exchangeRateDrift.size() + " off by the exchange rate (worst " + Math.round(diff.worstDrift * 1000) / 10.0 + " %), "
                + diff.droppedByIdentityRule.size() + " dropped by the identity rule, " + diff.unknownToUs.size() + " unknown to us, " + diff.pricedByUsOnly.size() + " priced by us only.");
        printSome("  half cent ties", diff.halfCentTies);
        printSome("  off by one", diff.offByOne);
        printSome("  different", diff.different);
        printSome("  off by the exchange rate", diff.exchangeRateDrift);
        printSome("  dropped by the identity rule", diff.droppedByIdentityRule);
        printSome("  unknown to us", diff.unknownToUs);
        printSome("  priced by us only", diff.pricedByUsOnly);
    }

    private static void printSome(String title, List<String> lines) {
        if (lines.isEmpty()) return;

        System.out.println(title + " (" + lines.size() + "):");
        for (int line = 0; line < lines.size() && line < MAX_PRINTED_LINES; line++) {
            System.out.println("    " + lines.get(line));
        }
    }

    private static void printHeap() {
        Runtime runtime = Runtime.getRuntime();
        long usedMegaBytes = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        System.out.println("  Heap in use: " + usedMegaBytes + " MB of " + runtime.maxMemory() / (1024 * 1024) + " MB.");
    }

    // What one comparison of two sets of budget points found, split by the reason they differ
    private static class BudgetPointDiff {
        private final List<String> halfCentTies = new ArrayList<>();
        private final List<String> offByOne = new ArrayList<>();
        private final List<String> different = new ArrayList<>();
        private final List<String> exchangeRateDrift = new ArrayList<>();
        private final List<String> droppedByIdentityRule = new ArrayList<>();
        private final List<String> unknownToUs = new ArrayList<>();
        private final List<String> pricedByUsOnly = new ArrayList<>();
        private final Set<String> pricedDifferently = new HashSet<>(); // the names behind the two explained lists, without the prices around them
        private double worstDrift;
        private int equal;
    }

    // What one comparison of two row sets found, split by the reason the rows differ
    private static class ReplayDiff {
        private final List<String> tolerated = new ArrayList<>();
        private final List<String> drifted = new ArrayList<>();
        private final List<String> differences = new ArrayList<>();
        private final List<String> newOracleIds = new ArrayList<>();
        private final List<String> ourOnly = new ArrayList<>();
        private final List<String> theirOnly = new ArrayList<>();
        private int equal;
    }

}
