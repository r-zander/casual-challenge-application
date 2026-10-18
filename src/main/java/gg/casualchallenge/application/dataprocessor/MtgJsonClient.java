package gg.casualchallenge.application.dataprocessor;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import gg.casualchallenge.application.dataprocessor.model.CardPrices;
import gg.casualchallenge.application.dataprocessor.model.Cents;
import gg.casualchallenge.application.dataprocessor.model.IgnoredPriceVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonCard;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPrinting;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPricesVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPrintingsVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonSet;
import gg.casualchallenge.application.dataprocessor.model.PriceWindowVO;
import gg.casualchallenge.application.model.type.Finish;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.type.MtgSetType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class MtgJsonClient {

    private static final String PRINTINGS_FILE = "AllPrintings.json";
    private static final String PRINTINGS_ZIP = "AllPrintings.json.zip";
    private static final String PRICES_FILE = "AllPrices.json";
    private static final String PRICES_ZIP = "AllPrices.json.zip";
    private static final String IGNORED_PRICES_FILE = "IgnoredPrices.json";

    private static final long REQUIRED_DISK_SPACE = 2L * 1024 * 1024 * 1024;

    private static final Set<MtgSetType> IGNORED_SET_TYPES = EnumSet.of(MtgSetType.FUNNY, MtgSetType.MEMORABILIA, MtgSetType.MINIGAME);

    // The order decides which format ends up in banned_in for a card that is banned in more than one of them
    private final static Map<String, MtgFormat> FORMATS_BY_LEGALITY_KEY = new LinkedHashMap<>();

    static {
        FORMATS_BY_LEGALITY_KEY.put("standard", MtgFormat.STANDARD);
        FORMATS_BY_LEGALITY_KEY.put("pioneer", MtgFormat.PIONEER);
        FORMATS_BY_LEGALITY_KEY.put("modern", MtgFormat.MODERN);
        FORMATS_BY_LEGALITY_KEY.put("legacy", MtgFormat.LEGACY);
        FORMATS_BY_LEGALITY_KEY.put("vintage", MtgFormat.VINTAGE);
        FORMATS_BY_LEGALITY_KEY.put("pauper", MtgFormat.PAUPER);
    }

    private final String mtgJsonBaseUrl;
    private final Path archiveDirectory;
    private final Map<String, List<IgnoredPriceVO>> ignoredPricesByCardName;

    public MtgJsonClient(
            @Value("${casual-challenge.season.mtgjson-base-url}") String mtgJsonBaseUrl,
            @Value("${casual-challenge.season.archive-directory}") String archiveDirectory
    ) {
        this.mtgJsonBaseUrl = mtgJsonBaseUrl;
        this.archiveDirectory = Paths.get(archiveDirectory);

        try (InputStream ignoredPrices = new ClassPathResource(IGNORED_PRICES_FILE).getInputStream()) {
            this.ignoredPricesByCardName = readIgnoredPrices(ignoredPrices, IGNORED_PRICES_FILE);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read '" + IGNORED_PRICES_FILE + "'.", e);
        }
    }

    /** @param seasonDirectory null = download into a temp directory in the archive directory and throw it away afterwards */
    public MtgJsonPrintingsVO fetchPrintings(Path seasonDirectory) {
        if (seasonDirectory != null) archiveIgnoredPrices(seasonDirectory);
        Path directory = seasonDirectory != null ? seasonDirectory : createDownloadDirectory();
        try (ZipInputStream zipStream = new ZipInputStream(new BufferedInputStream(Files.newInputStream(download(PRINTINGS_ZIP, directory))))) {
            positionOnEntry(zipStream, PRINTINGS_FILE);
            return readPrintings(zipStream);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read '" + PRINTINGS_FILE + "'.", e);
        } finally {
            if (seasonDirectory == null) deleteDownload(directory, PRINTINGS_ZIP);
        }
    }

    /** @param seasonDirectory null = download into a temp directory in the archive directory and throw it away afterwards */
    public MtgJsonPricesVO fetchPrices(Map<UUID, MtgJsonPrinting> printingsByUuid, PriceWindowVO window, Path seasonDirectory) {
        Path directory = seasonDirectory != null ? seasonDirectory : createDownloadDirectory();
        try (ZipInputStream zipStream = new ZipInputStream(new BufferedInputStream(Files.newInputStream(download(PRICES_ZIP, directory))))) {
            positionOnEntry(zipStream, PRICES_FILE);
            return readPrices(zipStream, printingsByUuid, window);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read '" + PRICES_FILE + "'.", e);
        } finally {
            if (seasonDirectory == null) deleteDownload(directory, PRICES_ZIP);
        }
    }

    public MtgJsonPrintingsVO readArchivedPrintings(Path seasonDirectory) {
        Path zip = seasonDirectory.resolve(PRINTINGS_ZIP);
        if (!Files.isRegularFile(zip)) throw new IllegalArgumentException("The season archive has no '" + zip + "'.");

        // A season prepared before the ignore list went into the archive is read with the list of today
        Map<String, List<IgnoredPriceVO>> seasonIgnoredPricesByCardName = ignoredPricesByCardName;
        Path ignoredPrices = seasonDirectory.resolve(IGNORED_PRICES_FILE);
        if (Files.isRegularFile(ignoredPrices)) {
            try (InputStream ignoredPricesJson = Files.newInputStream(ignoredPrices)) {
                seasonIgnoredPricesByCardName = readIgnoredPrices(ignoredPricesJson, ignoredPrices.toString());
            } catch (IOException e) {
                throw new RuntimeException("Couldn't read '" + ignoredPrices + "': " + e.getMessage(), e);
            }
        }

        try (ZipInputStream zipStream = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            positionOnEntry(zipStream, PRINTINGS_FILE);
            return readPrintings(zipStream, seasonIgnoredPricesByCardName);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read '" + zip + "'.", e);
        }
    }

    public MtgJsonPricesVO readArchivedPrices(
            Map<UUID, MtgJsonPrinting> printingsByUuid,
            PriceWindowVO window,
            Path seasonDirectory
    ) {
        Path zip = seasonDirectory.resolve(PRICES_ZIP);
        if (!Files.isRegularFile(zip)) throw new IllegalArgumentException("The season archive has no '" + zip + "'.");

        try (ZipInputStream zipStream = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            positionOnEntry(zipStream, PRICES_FILE);
            return readPrices(zipStream, printingsByUuid, window);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read '" + zip + "'.", e);
        }
    }

    public MtgJsonPrintingsVO readPrintings(InputStream allPrintingsJson) {
        return readPrintings(allPrintingsJson, ignoredPricesByCardName);
    }

    public MtgJsonPrintingsVO readPrintings(
            InputStream allPrintingsJson,
            Map<String, List<IgnoredPriceVO>> ignoredPricesByCardName
    ) {
        Map<UUID, MtgJsonPrinting> printingsByUuid = new HashMap<>();
        Map<String, List<MtgJsonPrinting>> ignoredPrintingsByCardName = new HashMap<>();
        Map<String, IdentityCandidate> identitiesByName = new LinkedHashMap<>();
        List<MtgJsonSet> sets = new ArrayList<>();
        LocalDate metaDate = null;
        String metaVersion = null;

        try (JsonParser parser = new JsonFactory().createParser(allPrintingsJson)) {
            parser.nextToken();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();
                switch (fieldName) {
                    case "meta":
                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                            String metaField = parser.currentName();
                            parser.nextToken();
                            if ("date".equals(metaField)) {
                                metaDate = parseDate(parser.getValueAsString());
                            } else if ("version".equals(metaField)) {
                                metaVersion = parser.getValueAsString();
                            } else {
                                parser.skipChildren();
                            }
                        }
                        break;
                    case "data":
                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                            String setCode = parser.currentName();
                            parser.nextToken();
                            sets.add(readSet(
                                    parser,
                                    setCode,
                                    identitiesByName,
                                    printingsByUuid,
                                    ignoredPrintingsByCardName,
                                    ignoredPricesByCardName
                            ));
                        }
                        break;
                    default:
                        parser.skipChildren();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read " + PRINTINGS_FILE + ".", e);
        }

        Map<String, MtgJsonCard> cardsByName = new LinkedHashMap<>();
        for (IdentityCandidate identity : identitiesByName.values()) {
            cardsByName.put(identity.getCardName(), toCard(identity));
        }

        log.info("Read {} sets with {} cards and {} printings.", sets.size(), cardsByName.size(), printingsByUuid.size());
        return new MtgJsonPrintingsVO(
                cardsByName,
                printingsByUuid,
                ignoredPrintingsByCardName,
                sets,
                metaDate,
                metaVersion
        );
    }

    public MtgJsonPricesVO readPrices(InputStream allPricesJson, Map<UUID, MtgJsonPrinting> printingsByUuid, PriceWindowVO window) {
        Map<String, CardPrices> pricesByCardName = new HashMap<>();
        boolean[] pricedDays = new boolean[window.lengthInDays()];

        try (JsonParser parser = new JsonFactory().createParser(allPricesJson)) {
            parser.nextToken();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();
                if (!"data".equals(fieldName)) {
                    parser.skipChildren();
                    continue;
                }
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    MtgJsonPrinting printing = printingsByUuid.get(UUID.fromString(parser.currentName()));
                    parser.nextToken();
                    if (printing == null) {
                        parser.skipChildren();
                        continue;
                    }
                    readPricesOfPrinting(parser, printing, window, pricesByCardName, pricedDays);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read " + PRICES_FILE + ".", e);
        }

        int pricedDayCount = 0;
        for (boolean pricedDay : pricedDays) {
            if (pricedDay) pricedDayCount++;
        }

        log.info("Read prices for {} cards on {} of the {} days in the window.", pricesByCardName.size(), pricedDayCount, pricedDays.length);
        return new MtgJsonPricesVO(pricesByCardName, pricedDayCount);
    }

    private Path createDownloadDirectory() {
        try {
            Files.createDirectories(archiveDirectory);
            return Files.createTempDirectory(archiveDirectory, "download-");
        } catch (IOException e) {
            throw new RuntimeException("Couldn't create a download directory in '" + archiveDirectory + "'.", e);
        }
    }

    private Path download(String fileName, Path directory) {
        String url = mtgJsonBaseUrl + "/" + fileName;
        Path target = directory.resolve(fileName);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(30))
                .GET()
                .build();

        try {
            long usableSpace = Files.getFileStore(directory).getUsableSpace();
            if (usableSpace < REQUIRED_DISK_SPACE) {
                throw new RuntimeException("Not enough disk space for '" + fileName + "'. We want " + REQUIRED_DISK_SPACE + " bytes free, but '" + directory + "' only has " + usableSpace + " bytes left.");
            }
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Couldn't download '" + url + "'. Status code was " + response.statusCode() + ".");
                }
                Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't download '" + url + "'.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Download of '" + url + "' got interrupted.", e);
        }

        log.info("Downloaded {} to '{}'.", url, target);
        return target;
    }

    private void deleteDownload(Path directory, String fileName) {
        try {
            Files.deleteIfExists(directory.resolve(fileName));
            Files.deleteIfExists(directory);
        } catch (IOException e) {
            log.warn("Couldn't clean up the download directory '{}'.", directory, e);
        }
    }

    private static void positionOnEntry(ZipInputStream zipStream, String entryName) throws IOException {
        ZipEntry entry = zipStream.getNextEntry();
        while (entry != null && !entryName.equals(entry.getName())) {
            entry = zipStream.getNextEntry();
        }
        if (entry == null) throw new RuntimeException("Couldn't find '" + entryName + "' in the downloaded zip file.");
    }

    private MtgJsonSet readSet(
            JsonParser parser,
            String setCode,
            Map<String, IdentityCandidate> identitiesByName,
            Map<UUID, MtgJsonPrinting> printingsByUuid,
            Map<String, List<MtgJsonPrinting>> ignoredPrintingsByCardName,
            Map<String, List<IgnoredPriceVO>> ignoredPricesByCardName
    ) throws IOException {
        List<IdentityCandidate> identityCandidates = new ArrayList<>();
        List<MtgJsonSet.MtgJsonDeck> decks = new ArrayList<>();
        String setName = null;
        MtgSetType setType = null;
        String parentCode = null;
        LocalDate releaseDate = null;
        boolean isOnlineOnly = false;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case "name":
                    setName = parser.getValueAsString();
                    break;
                case "type":
                    setType = MtgSetType.fromMtgJson(parser.getValueAsString());
                    break;
                case "parentCode":
                    parentCode = parser.getValueAsString();
                    break;
                case "releaseDate":
                    releaseDate = parseDate(parser.getValueAsString());
                    break;
                case "isOnlineOnly":
                    isOnlineOnly = parser.getValueAsBoolean();
                    break;
                case "cards":
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        readCard(
                                parser,
                                setCode,
                                identityCandidates,
                                printingsByUuid,
                                ignoredPrintingsByCardName,
                                ignoredPricesByCardName
                        );
                    }
                    break;
                case "decks":
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        decks.add(readDeck(parser));
                    }
                    break;
                default:
                    parser.skipChildren();
            }
        }

        // The cards come before the set attributes in the file --> we can only judge them once the set is done
        boolean isIgnoredSetType = IGNORED_SET_TYPES.contains(setType);
        for (IdentityCandidate candidate : identityCandidates) {
            if (isIgnoredSetType && !candidate.isVintageLegal()) continue;

            candidate.setReleaseDate(releaseDate);
            candidate.setMainSet(parentCode == null);
            IdentityCandidate identity = identitiesByName.get(candidate.getCardName());
            if (identity == null || isOlderPrinting(candidate, identity)) {
                identitiesByName.put(candidate.getCardName(), candidate);
            }
        }

        return new MtgJsonSet(setName, setCode, releaseDate, setType, parentCode, isOnlineOnly, decks);
    }

    private void readCard(
            JsonParser parser,
            String setCode,
            List<IdentityCandidate> identityCandidates,
            Map<UUID, MtgJsonPrinting> printingsByUuid,
            Map<String, List<MtgJsonPrinting>> ignoredPrintingsByCardName,
            Map<String, List<IgnoredPriceVO>> ignoredPricesByCardName
    ) throws IOException {
        Map<MtgFormat, String> legalities = new EnumMap<>(MtgFormat.class);
        String cardName = null;
        String uuid = null;
        String oracleId = null;
        String number = null;
        String borderColor = null;
        boolean isPaper = false;
        boolean isOversized = false;
        boolean isFunny = false;
        boolean isRebalanced = false;
        Set<Finish> finishes = EnumSet.noneOf(Finish.class);

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case "name":
                    cardName = parser.getValueAsString();
                    break;
                case "uuid":
                    uuid = parser.getValueAsString();
                    break;
                case "number":
                    number = parser.getValueAsString();
                    break;
                case "borderColor":
                    borderColor = parser.getValueAsString();
                    break;
                case "isOversized":
                    isOversized = parser.getValueAsBoolean();
                    break;
                case "isFunny":
                    isFunny = parser.getValueAsBoolean();
                    break;
                case "isRebalanced":
                    isRebalanced = parser.getValueAsBoolean();
                    break;
                case "availability":
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        if ("paper".equals(parser.getValueAsString())) isPaper = true;
                    }
                    break;
                case "finishes":
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        String finish = parser.getValueAsString();
                        if ("foil".equals(finish)) finishes.add(Finish.FOIL);
                        if ("nonfoil".equals(finish)) finishes.add(Finish.NONFOIL);
                    }
                    break;
                case "identifiers":
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        boolean isOracleId = "scryfallOracleId".equals(parser.currentName());
                        parser.nextToken();
                        if (isOracleId) {
                            oracleId = parser.getValueAsString();
                        } else {
                            parser.skipChildren();
                        }
                    }
                    break;
                case "legalities":
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        MtgFormat mtgFormat = FORMATS_BY_LEGALITY_KEY.get(parser.currentName());
                        parser.nextToken();
                        if (mtgFormat != null) legalities.put(mtgFormat, parser.getValueAsString());
                    }
                    break;
                default:
                    parser.skipChildren();
            }
        }

        if (!isPaper || isOversized || "silver".equals(borderColor) || "gold".equals(borderColor)) return;

        UUID printingUuid = UUID.fromString(uuid);
        Set<Finish> ignoredFinishes = EnumSet.noneOf(Finish.class);
        for (Finish finish : finishes) {
            if (isIgnoredForPrices(cardName, setCode, number, finish, ignoredPricesByCardName)) {
                ignoredFinishes.add(finish);
            }
        }
        if (ignoredFinishes.isEmpty()) {
            MtgJsonPrinting printing = new MtgJsonPrinting(printingUuid, cardName, setCode, number, finishes);
            printingsByUuid.put(printingUuid, printing);
        } else {
            MtgJsonPrinting ignoredPrinting =
                    new MtgJsonPrinting(printingUuid, cardName, setCode, number, ignoredFinishes);
            ignoredPrintingsByCardName.computeIfAbsent(cardName, name -> new ArrayList<>()).add(ignoredPrinting);
            finishes.removeAll(ignoredFinishes);
            if (!finishes.isEmpty()) {
                MtgJsonPrinting pricedPrinting =
                        new MtgJsonPrinting(printingUuid, cardName, setCode, number, finishes);
                printingsByUuid.put(printingUuid, pricedPrinting);
            }
        }
        if (isRebalanced) return;
        if (isFunny && !legalities.containsKey(MtgFormat.VINTAGE)) return; // MTGJSON flags every Unfinity card as funny, but the eternal legal ones are real cards

        String vintage = legalities.get(MtgFormat.VINTAGE);
        IdentityCandidate candidate = new IdentityCandidate();
        candidate.setCardName(cardName);
        candidate.setOracleId(oracleId);
        candidate.setNumber(number);
        candidate.setSetCode(setCode);
        candidate.setVintageLegal(legalities.containsKey(MtgFormat.VINTAGE)); // MTGJSON leaves out formats a card isn't legal in --> key present is what counts, like the python did
        candidate.setVintageRestricted("Restricted".equalsIgnoreCase(vintage));
        for (MtgFormat mtgFormat : FORMATS_BY_LEGALITY_KEY.values()) {
            if ("Banned".equals(legalities.get(mtgFormat))) {
                candidate.setBannedIn(mtgFormat);
                break;
            }
        }
        identityCandidates.add(candidate);
    }

    private static MtgJsonSet.MtgJsonDeck readDeck(JsonParser parser) throws IOException {
        String deckName = null;
        String deckType = null;
        LocalDate releaseDate = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case "name":
                    deckName = parser.getValueAsString();
                    break;
                case "type":
                    deckType = parser.getValueAsString();
                    break;
                case "releaseDate":
                    releaseDate = parseDate(parser.getValueAsString());
                    break;
                default:
                    parser.skipChildren();
            }
        }

        // Free text, "Commander Deck" and "MTGO Commander Deck" are both a thing
        boolean isCommanderDeck = false;
        if (deckType != null) isCommanderDeck = deckType.contains("Commander");

        return new MtgJsonSet.MtgJsonDeck(deckName, isCommanderDeck, releaseDate);
    }

    private static void readPricesOfPrinting(JsonParser parser, MtgJsonPrinting printing, PriceWindowVO window, Map<String, CardPrices> pricesByCardName, boolean[] pricedDays) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            if (!"paper".equals(fieldName)) {
                parser.skipChildren();
                continue;
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String marketIdentifier = parser.currentName();
                parser.nextToken();
                if (!"cardmarket".equals(marketIdentifier) && !"tcgplayer".equals(marketIdentifier)) {
                    parser.skipChildren();
                    continue;
                }
                CardPrices cardPrices = pricesByCardName.computeIfAbsent(printing.getCardName(), cardName -> new CardPrices(window.lengthInDays()));
                PriceSeries series = "cardmarket".equals(marketIdentifier) ? cardPrices.getEur() : cardPrices.getUsd();
                readRetailPrices(parser, printing, window, series, pricedDays);
            }
        }
    }

    private static void readRetailPrices(JsonParser parser, MtgJsonPrinting printing, PriceWindowVO window, PriceSeries series, boolean[] pricedDays) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            if (!"retail".equals(fieldName)) {
                parser.skipChildren();
                continue;
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String finishKey = parser.currentName();
                parser.nextToken();
                // MTGJSON's finishes say nonfoil, only its prices call it normal
                Finish finish = null;
                if ("normal".equals(finishKey)) finish = Finish.NONFOIL;
                if ("foil".equals(finishKey)) finish = Finish.FOIL;
                if (finish == null) {
                    parser.skipChildren();
                    continue;
                }
                // Foil and non-foil are two separate "printings" for the price calculation
                if (printing.getFinishes().contains(finish)) {
                    series.addPrinting(printing, finish, readPricesPerDay(parser, window, pricedDays));
                } else {
                    parser.skipChildren();
                }
            }
        }
    }

    private static Cents[] readPricesPerDay(JsonParser parser, PriceWindowVO window, boolean[] pricedDays) throws IOException {
        Cents[] centsPerDay = new Cents[window.lengthInDays()];

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            LocalDate date = LocalDate.parse(parser.currentName());
            JsonToken price = parser.nextToken();
            if (price != JsonToken.VALUE_NUMBER_FLOAT && price != JsonToken.VALUE_NUMBER_INT) {
                parser.skipChildren();
                continue;
            }
            int dayIndex = window.dayIndex(date);
            if (dayIndex < 0) continue;

            centsPerDay[dayIndex] = Cents.fromPrice(parser.getDecimalValue());
            pricedDays[dayIndex] = true;
        }

        return centsPerDay;
    }

    private static MtgJsonCard toCard(IdentityCandidate identity) {
        UUID oracleId = identity.getOracleId() != null ? UUID.fromString(identity.getOracleId()) : null;
        return new MtgJsonCard(
                identity.getCardName(),
                oracleId,
                identity.isVintageLegal(),
                identity.isVintageRestricted(),
                identity.getBannedIn(),
                identity.getSetCode(),
                identity.getReleaseDate()
        );
    }

    private static boolean isIgnoredForPrices(
            String cardName,
            String setCode,
            String number,
            Finish finish,
            Map<String, List<IgnoredPriceVO>> ignoredPricesByCardName
    ) {
        List<IgnoredPriceVO> ignoredPrices = ignoredPricesByCardName.get(cardName);
        if (ignoredPrices == null) return false;

        for (IgnoredPriceVO ignoredPrice : ignoredPrices) {
            if (!ignoredPrice.getSet().equals(setCode)) continue;
            if (ignoredPrice.getNumber() != null) {
                if (!ignoredPrice.getNumber().equals(number)) continue;
            }
            if (ignoredPrice.getFinish() != null) {
                if (ignoredPrice.getFinish() != finish) continue;
            }

            return true;
        }

        return false;
    }

    private static Map<String, List<IgnoredPriceVO>> readIgnoredPrices(
            InputStream ignoredPricesJson,
            String fileName
    ) throws IOException {
        /*
         * Not Spring's ObjectMapper: this one refuses unknown keys and finishes given as numbers,
         * so a typo like "finsh" can't quietly ignore the whole printing.
         */
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new ParameterNamesModule())
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
        Map<String, List<IgnoredPriceVO>> ignoredPricesByCardName =
                objectMapper.readValue(ignoredPricesJson, new TypeReference<Map<String, List<IgnoredPriceVO>>>() {});
        for (Map.Entry<String, List<IgnoredPriceVO>> entry : ignoredPricesByCardName.entrySet()) {
            String cardName = entry.getKey();
            for (IgnoredPriceVO ignoredPrice : entry.getValue()) {
                if (ignoredPrice.getSet() == null) {
                    throw new RuntimeException("'" + cardName + "' has an entry without a set"
                            + " in '" + fileName + "': " + ignoredPrice + ".");
                }
                if (ignoredPrice.getFinish() != null) {
                    if (ignoredPrice.getNumber() == null) {
                        throw new RuntimeException("'" + cardName + "' has an entry with a finish,"
                                + " but without a number in '" + fileName + "': " + ignoredPrice + "."
                                + " A finish only works for one printing.");
                    }
                }
            }
        }

        return ignoredPricesByCardName;
    }

    /**
     * A rebuild of the season's price details has to ignore the very same prices,
     * whatever IgnoredPrices.json says by then.
     */
    private static void archiveIgnoredPrices(Path seasonDirectory) {
        try (InputStream ignoredPrices = new ClassPathResource(IGNORED_PRICES_FILE).getInputStream()) {
            Path target = seasonDirectory.resolve(IGNORED_PRICES_FILE);
            Files.copy(ignoredPrices, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't copy '" + IGNORED_PRICES_FILE + "'"
                    + " into '" + seasonDirectory + "'.", e);
        }
    }


    private static boolean isOlderPrinting(IdentityCandidate candidate, IdentityCandidate identity) {
        if (candidate.getReleaseDate() == null) return false;
        if (identity.getReleaseDate() == null) return true;
        int comparison = candidate.getReleaseDate().compareTo(identity.getReleaseDate());
        if (comparison != 0) return comparison < 0;
        if (candidate.isMainSet() != identity.isMainSet()) return candidate.isMainSet(); // promos come out on the same day as their set, but should not take precedence over main sets
        comparison = candidate.getSetCode().compareTo(identity.getSetCode());
        if (comparison != 0) return comparison < 0;
        return compareCollectorNumbers(candidate.getNumber(), identity.getNumber()) < 0;
    }

    // Collector numbers are strings like "1", "12a" or "A-7" - we want 2 before 12 and the plain numbers first
    private static int compareCollectorNumbers(String number, String otherNumber) {
        int comparison = Integer.compare(leadingNumber(number), leadingNumber(otherNumber));
        if (comparison != 0) return comparison;
        return number.compareTo(otherNumber);
    }

    private static int leadingNumber(String number) {
        int digits = 0;
        while (digits < number.length() && Character.isDigit(number.charAt(digits))) {
            digits++;
        }
        if (digits == 0) return Integer.MAX_VALUE;
        return Integer.parseInt(number.substring(0, digits));
    }

    private static LocalDate parseDate(String date) {
        if (date == null) return null;
        return LocalDate.parse(date);
    }

    // One printing of one card while we are reading the file
    @Data
    private static class IdentityCandidate {
        private String cardName;
        private String oracleId;
        private String number;
        private String setCode;
        private boolean vintageLegal;
        private boolean vintageRestricted;
        private MtgFormat bannedIn;
        private LocalDate releaseDate;
        private boolean mainSet;
    }

}
