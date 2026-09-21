package gg.casualchallenge.application.dataprocessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.casualchallenge.application.common.CardNameNormalizer;
import gg.casualchallenge.application.common.Constants;
import gg.casualchallenge.application.common.RomanNumeral;
import gg.casualchallenge.application.common.SeasonDates;
import gg.casualchallenge.application.dataprocessor.model.AssembledDraftVO;
import gg.casualchallenge.application.dataprocessor.model.BudgetPointsVO;
import gg.casualchallenge.application.dataprocessor.model.CardPrices;
import gg.casualchallenge.application.dataprocessor.model.Cents;
import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import gg.casualchallenge.application.dataprocessor.model.MetaSharesVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonCard;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPricesVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPrintingsVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonSet;
import gg.casualchallenge.application.dataprocessor.model.PriceWindowVO;
import gg.casualchallenge.application.dataprocessor.model.SeasonPreparationState;
import gg.casualchallenge.application.dataprocessor.model.SeasonPreparationStep;
import gg.casualchallenge.application.dataprocessor.model.Staple;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.values.MtgSetVO;
import gg.casualchallenge.application.model.values.SeasonDraftCardVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonDraftVO;
import gg.casualchallenge.application.model.values.SeasonPreparationRequestVO;
import gg.casualchallenge.application.model.values.SeasonPreparationStatusVO;
import gg.casualchallenge.application.persistence.CardRepository;
import gg.casualchallenge.application.persistence.CardSeasonDataRepository;
import gg.casualchallenge.application.persistence.SeasonDraftRepository;
import gg.casualchallenge.application.persistence.SeasonRepository;
import gg.casualchallenge.application.persistence.entity.Card;
import gg.casualchallenge.application.persistence.entity.CardSeasonData;
import gg.casualchallenge.application.persistence.entity.Season;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class SeasonPreparationService {

    private static final int TOTAL_STEPS = SeasonPreparationStep.STORING_THE_DRAFT.getNumber();

    private static final int MAX_SKIP_REASON_LENGTH = 255;

    private static final int SHUTDOWN_TIMEOUT_IN_SECONDS = 30;

    // A budget point change is only worth reading when it is big relatively and absolutely
    private static final int RELEVANT_CHANGE_IN_PERCENT = 50;
    private static final int RELEVANT_CHANGE_IN_BUDGET_POINTS = 100;
    private static final int TOP_CHANGES = 20;

    private static final String SEASON_DIRECTORY_PREFIX = "season-";
    private static final String REQUEST_FILE = "request.json";
    private static final String STAPLES_FILE = "staples.json";

    private final MtgJsonClient mtgJsonClient;
    private final MtgGoldfishClient mtgGoldfishClient;
    private final MtgTop8Client mtgTop8Client;
    private final SeasonRepository seasonRepository;
    private final CardRepository cardRepository;
    private final CardSeasonDataRepository cardSeasonDataRepository;
    private final SeasonDraftRepository seasonDraftRepository;
    private final SeasonDates seasonDates;
    private final ObjectMapper objectMapper;
    private final int priceWindowDays;
    private final Path archiveDirectory;
    private final int archivedSeasons;

    private final ExecutorService preparationExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "season-preparation");
        }
    });
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    private volatile SeasonPreparationStatusVO preparation = new SeasonPreparationStatusVO(SeasonPreparationState.IDLE, null, null, null, null, null);

    public SeasonPreparationService(
            MtgJsonClient mtgJsonClient,
            MtgGoldfishClient mtgGoldfishClient,
            MtgTop8Client mtgTop8Client,
            SeasonRepository seasonRepository,
            CardRepository cardRepository,
            CardSeasonDataRepository cardSeasonDataRepository,
            SeasonDraftRepository seasonDraftRepository,
            SeasonDates seasonDates,
            ObjectMapper objectMapper,
            @Value("${casual-challenge.season.price-window-days}") int priceWindowDays,
            @Value("${casual-challenge.season.archive-directory}") String archiveDirectory,
            @Value("${casual-challenge.season.archived-seasons}") int archivedSeasons
    ) {
        this.mtgJsonClient = mtgJsonClient;
        this.mtgGoldfishClient = mtgGoldfishClient;
        this.mtgTop8Client = mtgTop8Client;
        this.seasonRepository = seasonRepository;
        this.cardRepository = cardRepository;
        this.cardSeasonDataRepository = cardSeasonDataRepository;
        this.seasonDraftRepository = seasonDraftRepository;
        this.seasonDates = seasonDates;
        this.objectMapper = objectMapper;
        this.priceWindowDays = priceWindowDays;
        this.archiveDirectory = Paths.get(archiveDirectory);
        this.archivedSeasons = archivedSeasons;
    }

    public synchronized SeasonPreparationStatusVO prepare(SeasonPreparationRequestVO request) {
        Season currentSeason = seasonRepository.findCurrentSeason();
        if (currentSeason == null) {
            throw new IllegalStateException("There is no current season to continue.");
        }

        SeasonPreparationRequestVO fullRequest = withDefaults(request, currentSeason.getEndDate(), priceWindowDays, seasonDates);
        if (!fullRequest.getEndDate().isAfter(fullRequest.getStartDate())) {
            throw new IllegalArgumentException("A season that starts on " + fullRequest.getStartDate() + " can't end on " + fullRequest.getEndDate() + ".");
        }
        if (fullRequest.getPriceWindow().getEnd().isAfter(fullRequest.getStartDate())) {
            throw new IllegalArgumentException("The price window has to end at the start of the season at the latest, but it ends on " + fullRequest.getPriceWindow().getEnd() + ".");
        }
        if (fullRequest.getMetaSource() == MetaShareSource.FILES) {
            if (fullRequest.getUploadedBans() == null) throw new IllegalArgumentException("The meta source 'files' needs an uploaded 'bans' file.");
            if (fullRequest.getUploadedExtendedBans() == null) throw new IllegalArgumentException("The meta source 'files' needs an uploaded 'extendedBans' file.");
        }
        if (preparation.getState() == SeasonPreparationState.RUNNING) {
            throw new IllegalStateException("A season is already being prepared since " + preparation.getStartedAt() + ".");
        }

        cancelRequested.set(false);
        preparation = new SeasonPreparationStatusVO(SeasonPreparationState.RUNNING, SeasonPreparationStep.STARTING, "Untap, Upkeep, Draw!", LocalDateTime.now(Constants.TIMEZONE), null, null);
        preparationExecutor.submit(() -> runPreparation(fullRequest));

        return preparation;
    }

    public SeasonPreparationStatusVO status() {
        return preparation;
    }

    public void cancel() {
        cancelRequested.set(true);
    }

    @PreDestroy
    public void shutdown() {
        cancelRequested.set(true); // the preparation only gives up between steps, interrupting it alone doesn't help
        preparationExecutor.shutdownNow(); // the executor thread is not a daemon --> the JVM would wait for it on every deploy
        try {
            preparationExecutor.awaitTermination(SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static SeasonPreparationRequestVO withDefaults(
            SeasonPreparationRequestVO request,
            LocalDate previousSeasonEnd,
            int priceWindowDays,
            SeasonDates seasonDates
    ) {
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now(Constants.TIMEZONE);
        LocalDate endDate = request.getEndDate();
        if (endDate == null) {
            endDate = seasonDates.defaultEndDate(previousSeasonEnd);
            if (!endDate.isAfter(startDate)) {
                endDate = seasonDates.defaultEndDate(startDate.minusDays(1)); // the previous season ended ages ago --> count the season length from the new start instead
            }
        }

        PriceWindowVO defaultPriceWindow = PriceWindowVO.of(startDate, priceWindowDays);
        PriceWindowVO priceWindow = request.getPriceWindow();

        return new SeasonPreparationRequestVO(
                startDate,
                endDate,
                new PriceWindowVO(
                        priceWindow != null && priceWindow.getStart() != null ? priceWindow.getStart() : defaultPriceWindow.getStart(),
                        priceWindow != null && priceWindow.getEnd() != null ? priceWindow.getEnd() : defaultPriceWindow.getEnd()
                ),
                request.getMetaSource() != null ? request.getMetaSource() : MetaShareSource.MTGGOLDFISH,
                request.getUploadedBans(),
                request.getUploadedExtendedBans(),
                request.getPreparedBy()
        );
    }

    private void runPreparation(SeasonPreparationRequestVO request) {
        try {
            log.info("0 / {} | Untap, Upkeep, Draw!", TOTAL_STEPS);
            SeasonDraftVO draft = prepareSeason(request);
            if (draft == null) {
                log.info("Preparing a new season was cancelled. Nothing was written.");
                preparation = new SeasonPreparationStatusVO(SeasonPreparationState.CANCELLED, preparation.getStepId(), preparation.getStep(), preparation.getStartedAt(), LocalDateTime.now(Constants.TIMEZONE), null);
                return;
            }

            preparation = new SeasonPreparationStatusVO(
                    SeasonPreparationState.DONE,
                    SeasonPreparationStep.READY_FOR_REVIEW,
                    "Season " + draft.getSeasonNumber() + " is ready for review.",
                    preparation.getStartedAt(),
                    LocalDateTime.now(Constants.TIMEZONE),
                    null
            );
        } catch (Throwable throwable) {
            log.error("Preparing a new season failed.", throwable);
            preparation = new SeasonPreparationStatusVO(
                    SeasonPreparationState.FAILED,
                    preparation.getStepId(),
                    preparation.getStep(),
                    preparation.getStartedAt(),
                    LocalDateTime.now(Constants.TIMEZONE),
                    throwable.getMessage() != null ? throwable.getMessage() : throwable.toString()
            );
            if (throwable instanceof Error) throw (Error) throwable; // an out of memory is not ours to swallow
        }
    }

    private SeasonDraftVO prepareSeason(SeasonPreparationRequestVO request) {
        Season currentSeason = seasonRepository.findCurrentSeason();
        if (currentSeason == null) {
            throw new IllegalStateException("There is no current season to continue.");
        }

        Path seasonArchive = createArchive(currentSeason.getSeasonNumber() + 1, request);

        startStep(SeasonPreparationStep.READING_ALL_PRINTINGS, "Reading AllPrintings.json");
        MtgJsonPrintingsVO printings = mtgJsonClient.fetchPrintings(seasonArchive);
        LocalDate lastPricedDay = printings.getMetaDate();
        if (lastPricedDay != null && request.getPriceWindow().getEnd().isAfter(lastPricedDay.plusDays(1))) {
            throw new IllegalStateException("MTGJSON has prices until " + lastPricedDay + ", the price window ends " + request.getPriceWindow().getEnd()
                    + " --> prepare on the season start date or pass an earlier priceWindowEnd.");
        }
        if (cancelRequested.get()) return null;

        startStep(SeasonPreparationStep.READING_ALL_PRICES, "Reading AllPrices.json");
        MtgJsonPricesVO prices = mtgJsonClient.fetchPrices(printings.getPrintingsByUuid(), request.getPriceWindow(), seasonArchive);
        if (cancelRequested.get()) return null;

        startStep(SeasonPreparationStep.READING_META_SHARES, "Reading meta shares from " + request.getMetaSource());
        MetaSharesVO metaShares = fetchMetaShares(request);
        writeArchiveFile(seasonArchive, STAPLES_FILE, metaShares);
        if (cancelRequested.get()) return null;

        startStep(SeasonPreparationStep.ASSEMBLING_THE_DRAFT, "Calculating budget points and assembling the draft - the big step");
        AssembledDraftVO assembledDraft = assemble(
                printings,
                prices,
                metaShares,
                cardRepository.findAll(),
                cardSeasonDataRepository.findAllBySeason(currentSeason),
                request,
                currentSeason,
                seasonDates
        );
        if (cancelRequested.get()) return null;

        startStep(SeasonPreparationStep.STORING_THE_DRAFT, "Storing the season draft");
        SeasonDraftVO draft = toDraft(assembledDraft.getReport(), printings, request, currentSeason);
        seasonDraftRepository.replace(draft, assembledDraft.getCards());
        pruneArchive(archiveDirectory, archivedSeasons);
        log.info("{} / {} | All done. Season {} is ready for review with {} cards.", TOTAL_STEPS, TOTAL_STEPS, draft.getSeasonNumber(), assembledDraft.getCards().size());

        return draft;
    }

    private void startStep(SeasonPreparationStep step, String description) {
        log.info("{} / {} | {}", step.getNumber(), TOTAL_STEPS, description);
        preparation = new SeasonPreparationStatusVO(SeasonPreparationState.RUNNING, step, description, preparation.getStartedAt(), null, null);
    }

    private MetaSharesVO fetchMetaShares(SeasonPreparationRequestVO request) {
        if (request.getMetaSource() == MetaShareSource.FILES) {
            return MetaSharesVO.fromBanFiles(request.getUploadedBans(), request.getUploadedExtendedBans());
        }

        MetaGameSourceClient metaGameSourceClient = request.getMetaSource() == MetaShareSource.MTGTOP8 ? mtgTop8Client : mtgGoldfishClient;
        Map<MtgFormat, List<Staple>> top50 = new EnumMap<>(MtgFormat.class);
        Map<MtgFormat, List<Staple>> top150 = new EnumMap<>(MtgFormat.class);
        for (MtgFormat mtgFormat : MtgFormat.values()) {
            top50.put(mtgFormat, metaGameSourceClient.fetchTop50Staples(mtgFormat));
            top150.put(mtgFormat, metaGameSourceClient.fetchTop150Staples(mtgFormat));
        }

        return MetaSharesVO.fromStaples(request.getMetaSource(), top50, top150);
    }

    private SeasonDraftVO toDraft(SeasonDraftReportVO report, MtgJsonPrintingsVO printings, SeasonPreparationRequestVO request, Season currentSeason) {
        return new SeasonDraftVO(
                0,
                report.getSeasonNumber(),
                request.getStartDate(),
                request.getEndDate(),
                request.getPriceWindow().getStart(),
                request.getPriceWindow().getEnd(),
                currentSeason.getId(),
                currentSeason.getEndDate(),
                currentSeason.getUpdatedAt(),
                printings.getMetaDate() != null ? printings.getMetaDate().toString() : null,
                request.getMetaSource().toString(),
                report.getPreparedAt(),
                request.getPreparedBy(),
                null,
                null,
                null,
                null,
                null,
                null,
                toJson(report)
        );
    }

    private String toJson(SeasonDraftReportVO report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Couldn't write the report for season " + report.getSeasonNumber() + ".", e);
        }
    }

    private Path createArchive(int seasonNumber, SeasonPreparationRequestVO request) {
        if (archivedSeasons <= 0) return null;

        Path seasonDirectory = archiveDirectory.resolve(SEASON_DIRECTORY_PREFIX + seasonNumber);
        try {
            Files.createDirectories(seasonDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't create the season archive '" + seasonDirectory + "'.", e);
        }

        log.info("Keeping the raw input of this run in '{}'.", seasonDirectory);
        writeArchiveFile(seasonDirectory, REQUEST_FILE, request);

        return seasonDirectory;
    }

    private void writeArchiveFile(Path seasonDirectory, String fileName, Object content) {
        if (seasonDirectory == null) return;

        try {
            objectMapper.writeValue(seasonDirectory.resolve(fileName).toFile(), content);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't write '" + fileName + "' to '" + seasonDirectory + "'.", e);
        }
    }

    // The season we just wrote is the newest one, so it always survives
    public static void pruneArchive(Path archiveDirectory, int archivedSeasons) {
        if (archivedSeasons <= 0) return;

        try {
            List<Path> seasonDirectories = new ArrayList<>();
            try (DirectoryStream<Path> directories = Files.newDirectoryStream(archiveDirectory, SEASON_DIRECTORY_PREFIX + "*")) {
                for (Path directory : directories) {
                    if (Files.isDirectory(directory)) seasonDirectories.add(directory);
                }
            }

            seasonDirectories.sort(Comparator.comparingInt(SeasonPreparationService::seasonNumberOf).reversed());
            for (int index = archivedSeasons; index < seasonDirectories.size(); index++) {
                deleteArchive(seasonDirectories.get(index));
            }
        } catch (IOException e) {
            log.warn("Couldn't clean up the season archive '{}'.", archiveDirectory, e);
        }
    }

    private static int seasonNumberOf(Path seasonDirectory) {
        String seasonNumber = seasonDirectory.getFileName().toString().substring(SEASON_DIRECTORY_PREFIX.length());
        if (!seasonNumber.matches("[0-9]+")) return 0; // whatever that is, it is the first to go

        return Integer.parseInt(seasonNumber);
    }

    public static boolean deleteArchive(Path archiveDirectory, int seasonNumber) {
        Path seasonDirectory = archiveDirectory.resolve(SEASON_DIRECTORY_PREFIX + seasonNumber);
        if (!Files.isDirectory(seasonDirectory)) return false;

        try {
            deleteArchive(seasonDirectory);
            return true;
        } catch (IOException e) {
            log.warn("Couldn't delete the season archive '{}'.", seasonDirectory, e);
            return false;
        }
    }

    private static void deleteArchive(Path seasonDirectory) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(seasonDirectory)) {
            for (Path file : files) {
                Files.delete(file);
            }
        }
        Files.delete(seasonDirectory);
    }

    public static AssembledDraftVO assemble(
            MtgJsonPrintingsVO printings,
            MtgJsonPricesVO prices,
            MetaSharesVO metaShares,
            List<Card> existingCards,
            List<CardSeasonData> previousSeasonData,
            SeasonPreparationRequestVO request,
            Season currentSeason,
            SeasonDates seasonDates
    ) {
        BudgetPointsVO budgetPoints = calculateBudgetPoints(printings, prices.getPricesByCardName());

        Map<String, Card> existingCardsByName = new HashMap<>(existingCards.size());
        Map<String, Card> existingCardsByNormalizedName = new HashMap<>(existingCards.size());
        Map<UUID, Card> existingCardsByOracleId = new HashMap<>(existingCards.size());
        for (Card existingCard : existingCards) {
            existingCardsByName.put(existingCard.getName(), existingCard);
            existingCardsByNormalizedName.put(existingCard.getNormalizedName(), existingCard);
            existingCardsByOracleId.put(existingCard.getOracleId(), existingCard);
        }

        // Playtest and joke cards share their normalized name with the real card often enough, and the order of the sets in the file is nothing to decide that by
        Map<String, String> legalNamesByNormalizedName = new HashMap<>(printings.getCardsByName().size());
        for (MtgJsonCard card : printings.getCardsByName().values()) {
            if (!card.isVintageLegal() || card.getOracleId() == null || CasualChallengeRules.isFlipStyleName(card.getName())) continue;

            legalNamesByNormalizedName.putIfAbsent(CardNameNormalizer.normalize(card.getName()), card.getName());
        }

        List<SeasonDraftCardVO> cards = new ArrayList<>(printings.getCardsByName().size());
        Map<String, String> namesByNormalizedName = new HashMap<>(printings.getCardsByName().size());
        Map<UUID, String> namesByOracleId = new HashMap<>(printings.getCardsByName().size());

        for (MtgJsonCard card : printings.getCardsByName().values()) {
            String cardName = card.getName();
            if (CasualChallengeRules.isFlipStyleName(cardName) || card.getOracleId() == null) continue;

            Card cardWithSameName = existingCardsByName.get(cardName);
            Card cardWithSameOracleId = existingCardsByOracleId.get(card.getOracleId());
            String normalizedName = CardNameNormalizer.normalize(cardName);
            String skipReason = findSkipReason(card, normalizedName, cardWithSameName, cardWithSameOracleId, existingCardsByNormalizedName, namesByNormalizedName, namesByOracleId, legalNamesByNormalizedName);
            // skip_reason is a varchar(255) and card names can be silly long
            if (skipReason != null && skipReason.length() > MAX_SKIP_REASON_LENGTH) skipReason = skipReason.substring(0, MAX_SKIP_REASON_LENGTH);
            if (skipReason == null) {
                namesByNormalizedName.put(normalizedName, cardName);
                namesByOracleId.put(card.getOracleId(), cardName);
            }

            boolean isNewCard = true;
            UUID previousOracleId = null;
            if (cardWithSameName != null && cardWithSameName.getOracleId().equals(card.getOracleId())) {
                isNewCard = false;
            } else if (cardWithSameName != null) {
                // The card kept its name but got a new oracle id --> commit moves the existing rows over and has nothing left to insert
                previousOracleId = cardWithSameName.getOracleId();
            } else if (cardWithSameOracleId != null) {
                isNewCard = false;
            }

            Map<MtgFormat, BigDecimal> ban = metaShares.findBan(cardName);
            Map<MtgFormat, BigDecimal> extendedBan = metaShares.findExtendedBan(cardName);
            Cents cardPrice = budgetPoints.getPricesByCardName().get(cardName);
            Integer cardBudgetPoints = cardPrice != null ? cardPrice.toBudgetPoints() : null;
            cards.add(new SeasonDraftCardVO(
                    card.getOracleId(),
                    previousOracleId,
                    cardName,
                    normalizedName,
                    cardBudgetPoints,
                    CasualChallengeRules.legalityOf(card, cardBudgetPoints, ban != null, extendedBan != null),
                    metaShareOf(extendedBan, ban, MtgFormat.STANDARD),
                    metaShareOf(extendedBan, ban, MtgFormat.PIONEER),
                    metaShareOf(extendedBan, ban, MtgFormat.MODERN),
                    metaShareOf(extendedBan, ban, MtgFormat.LEGACY),
                    metaShareOf(extendedBan, ban, MtgFormat.VINTAGE),
                    metaShareOf(extendedBan, ban, MtgFormat.PAUPER),
                    card.getBannedIn(),
                    card.isVintageRestricted(),
                    isNewCard,
                    skipReason
            ));
        }

        // The card table also holds cards that have no printing we may price any more (acorn only, playtest only, oversized only).
        // Dropping their season row would make them vanish from the API instead of answering "not legal" --> keep them.
        Set<UUID> draftedOracleIds = new HashSet<>(cards.size());
        for (SeasonDraftCardVO card : cards) {
            if (card.getSkipReason() != null) continue;

            draftedOracleIds.add(card.getOracleId());
            if (card.getPreviousOracleId() != null) draftedOracleIds.add(card.getPreviousOracleId());
        }
        for (Card existingCard : existingCards) {
            if (printings.getCardsByName().containsKey(existingCard.getName())) continue;
            if (draftedOracleIds.contains(existingCard.getOracleId())) continue;

            cards.add(new SeasonDraftCardVO(
                    existingCard.getOracleId(),
                    null,
                    existingCard.getName(),
                    existingCard.getNormalizedName(),
                    0,
                    Legality.NOT_LEGAL,
                    null, null, null, null, null, null,
                    null,
                    false,
                    false,
                    null
            ));
        }

        return new AssembledDraftVO(cards, buildReport(cards, existingCardsByOracleId, previousSeasonData, printings, metaShares, budgetPoints, prices, request, currentSeason, seasonDates));
    }

    private static BudgetPointsVO calculateBudgetPoints(MtgJsonPrintingsVO printings, Map<String, CardPrices> pricesByCardName) {
        Map<String, Cents> eurPricesByCardName = new HashMap<>(printings.getCardsByName().size());
        Map<String, Cents> usdPricesByCardName = new HashMap<>(printings.getCardsByName().size());
        double totalExchangeRate = 0;
        int exchangeRateCount = 0;

        for (String cardName : printings.getCardsByName().keySet()) {
            CardPrices cardPrices = pricesByCardName.get(cardName);
            // Basics are free and their prices would only spoil the exchange rate
            if (CasualChallengeRules.isBasicLand(cardName) || cardPrices == null) {
                eurPricesByCardName.put(cardName, Cents.of(0));
                usdPricesByCardName.put(cardName, Cents.of(0));
                continue;
            }

            eurPricesByCardName.put(cardName, BudgetPointsUtil.fromSeries(cardPrices.getEur()));
            usdPricesByCardName.put(cardName, BudgetPointsUtil.fromSeries(cardPrices.getUsd()));
            double eurAverage = averageCents(cardPrices.getEur());
            if (eurAverage > 0) {
                totalExchangeRate += averageCents(cardPrices.getUsd()) / eurAverage;
                exchangeRateCount++;
            }
        }

        double exchangeRate = exchangeRateCount > 0 ? totalExchangeRate / exchangeRateCount : 0;
        double adjustedExchangeRate = BudgetPointsUtil.adjustExchangeRate(exchangeRate);
        log.info("Average exchange rate is {}, adjusted to {}.", exchangeRate, adjustedExchangeRate);

        int pricesFixedByExchangeRateCount = 0;
        for (Map.Entry<String, Cents> entry : eurPricesByCardName.entrySet()) {
            if (entry.getValue().getAmount() != 0) continue;

            Cents usdPrice = usdPricesByCardName.get(entry.getKey());
            if (usdPrice.getAmount() == 0) continue;

            entry.setValue(BudgetPointsUtil.fromUsd(usdPrice, adjustedExchangeRate));
            pricesFixedByExchangeRateCount++;
        }
        log.info("Fixed {} card prices with the exchange rate.", pricesFixedByExchangeRateCount);

        return new BudgetPointsVO(eurPricesByCardName, exchangeRate, adjustedExchangeRate, pricesFixedByExchangeRateCount);
    }

    // The exchange rate is a ratio, so its two averages divide in plain doubles - just like they did in the python tool
    private static double averageCents(PriceSeries series) {
        if (series.pricedDays() == 0) return 0;

        return (double) series.sumOfCheapest().getAmount() / series.pricedDays();
    }

    private static SeasonDraftReportVO buildReport(
            List<SeasonDraftCardVO> cards,
            Map<UUID, Card> existingCardsByOracleId,
            List<CardSeasonData> previousSeasonData,
            MtgJsonPrintingsVO printings,
            MetaSharesVO metaShares,
            BudgetPointsVO budgetPoints,
            MtgJsonPricesVO prices,
            SeasonPreparationRequestVO request,
            Season currentSeason,
            SeasonDates seasonDates
    ) {
        Map<UUID, CardSeasonData> previousDataByOracleId = new HashMap<>(previousSeasonData.size());
        for (CardSeasonData cardSeasonData : previousSeasonData) {
            previousDataByOracleId.put(cardSeasonData.getCardOracleId(), cardSeasonData);
        }

        Map<Legality, Integer> legalities = new EnumMap<>(Legality.class);
        List<SeasonDraftReportVO.BanChangeVO> newBans = new ArrayList<>();
        List<SeasonDraftReportVO.BanChangeVO> unbans = new ArrayList<>();
        List<SeasonDraftReportVO.BanChangeVO> newExtended = new ArrayList<>();
        List<SeasonDraftReportVO.BanChangeVO> noLongerExtended = new ArrayList<>();
        List<SeasonDraftReportVO.BudgetPointChangeVO> budgetPointChanges = new ArrayList<>();
        List<SeasonDraftReportVO.BudgetPointChangeVO> increases = new ArrayList<>();
        List<SeasonDraftReportVO.BudgetPointChangeVO> decreases = new ArrayList<>();
        List<SeasonDraftReportVO.BudgetPointChangeVO> zeroBudgetPointCards = new ArrayList<>();
        List<SeasonDraftReportVO.LeftOutCardVO> skippedCards = new ArrayList<>();
        List<SeasonDraftReportVO.OracleIdChangeVO> oracleIdChanges = new ArrayList<>();
        List<SeasonDraftReportVO.RenamedCardVO> renamedCards = new ArrayList<>();
        List<SeasonDraftReportVO.RenamedCardVO> normalizedNameFixes = new ArrayList<>();
        List<SeasonDraftReportVO.LeftOutCardVO> keptCards = new ArrayList<>();
        Set<UUID> knownOracleIds = new HashSet<>(cards.size());
        int cardCount = 0;
        int newCardCount = 0;
        int cardsWithoutPrice = 0;

        for (SeasonDraftCardVO card : cards) {
            if (card.getSkipReason() != null) {
                skippedCards.add(new SeasonDraftReportVO.LeftOutCardVO(card.getName(), card.getOracleId(), card.getSkipReason()));
                continue;
            }

            cardCount++;
            legalities.merge(card.getLegality(), 1, Integer::sum);
            if (card.isNewCard() && card.getPreviousOracleId() == null) newCardCount++;
            if (card.getBudgetPoints() == 0) cardsWithoutPrice++;
            if (!printings.getCardsByName().containsKey(card.getName())) {
                keptCards.add(new SeasonDraftReportVO.LeftOutCardVO(card.getName(), card.getOracleId(), "no eligible printing this season, kept as not legal"));
            }

            Card existingCard = existingCardsByOracleId.get(card.getOracleId());
            if (card.getPreviousOracleId() != null) {
                oracleIdChanges.add(new SeasonDraftReportVO.OracleIdChangeVO(card.getName(), card.getPreviousOracleId(), card.getOracleId(), printings.getCardsByName().get(card.getName()).getFirstSetCode()));
                existingCard = existingCardsByOracleId.get(card.getPreviousOracleId()); // still sitting on its old oracle id --> it can be renamed on top of the remap
            }
            if (existingCard != null && !existingCard.getName().equals(card.getName())) {
                renamedCards.add(toRenamedCard(existingCard, card));
            } else if (existingCard != null && !existingCard.getNormalizedName().equals(card.getNormalizedName())) {
                // Same name, other normalized name: the old python tool turned apostrophes into a dash --> the commit repairs those rows on the way
                normalizedNameFixes.add(toRenamedCard(existingCard, card));
            }

            UUID previousOracleId = card.getPreviousOracleId() != null ? card.getPreviousOracleId() : card.getOracleId();
            knownOracleIds.add(previousOracleId);
            CardSeasonData previousData = previousDataByOracleId.get(previousOracleId);
            Legality previousLegality = previousData != null ? previousData.getLegality() : null;

            if (card.getLegality() == Legality.BANNED && previousLegality != Legality.BANNED) {
                newBans.add(toBanChange(card));
            } else if (card.getLegality() != Legality.BANNED && previousLegality == Legality.BANNED) {
                unbans.add(toBanChange(card));
            }
            if (card.getLegality() == Legality.EXTENDED && previousLegality != Legality.EXTENDED) {
                newExtended.add(toBanChange(card));
            } else if (card.getLegality() != Legality.EXTENDED && previousLegality == Legality.EXTENDED) {
                noLongerExtended.add(toBanChange(card));
            }

            if (previousData == null || previousData.getBudgetPoints() == null) continue;

            int previousBudgetPoints = previousData.getBudgetPoints();
            int change = card.getBudgetPoints() - previousBudgetPoints;
            SeasonDraftReportVO.BudgetPointChangeVO budgetPointChange = new SeasonDraftReportVO.BudgetPointChangeVO(card.getName(), previousBudgetPoints, card.getBudgetPoints(), change);
            if (change > 0) {
                increases.add(budgetPointChange);
            } else if (change < 0) {
                decreases.add(budgetPointChange);
            }
            if (isRelevantChange(change, previousBudgetPoints)) budgetPointChanges.add(budgetPointChange);
            if (card.getBudgetPoints() == 0 && previousBudgetPoints > 0) zeroBudgetPointCards.add(budgetPointChange);
        }

        for (MtgJsonCard card : printings.getCardsByName().values()) {
            // A card without an oracle id can't even have a draft row, but the reviewer should still hear about it
            if (card.getOracleId() == null && !CasualChallengeRules.isFlipStyleName(card.getName())) {
                skippedCards.add(new SeasonDraftReportVO.LeftOutCardVO(card.getName(), null, "no oracle id"));
            }
        }

        Map<String, String> skipReasonsByName = new HashMap<>(skippedCards.size());
        for (SeasonDraftReportVO.LeftOutCardVO skippedCard : skippedCards) {
            skipReasonsByName.put(skippedCard.getName(), skippedCard.getReason());
        }

        List<SeasonDraftReportVO.LeftOutCardVO> missingCards = new ArrayList<>();
        for (CardSeasonData cardSeasonData : previousSeasonData) {
            if (knownOracleIds.contains(cardSeasonData.getCardOracleId())) continue;

            Card existingCard = existingCardsByOracleId.get(cardSeasonData.getCardOracleId());
            String cardName = existingCard != null ? existingCard.getName() : null;
            missingCards.add(new SeasonDraftReportVO.LeftOutCardVO(cardName, cardSeasonData.getCardOracleId(), findMissingReason(cardName, skipReasonsByName)));
        }
        missingCards.addAll(keptCards);

        newBans.sort(Comparator.comparing(SeasonDraftReportVO.BanChangeVO::getName));
        unbans.sort(Comparator.comparing(SeasonDraftReportVO.BanChangeVO::getName));
        newExtended.sort(Comparator.comparing(SeasonDraftReportVO.BanChangeVO::getName));
        noLongerExtended.sort(Comparator.comparing(SeasonDraftReportVO.BanChangeVO::getName));
        budgetPointChanges.sort(Comparator.comparingInt((SeasonDraftReportVO.BudgetPointChangeVO budgetPointChange) -> Math.abs(budgetPointChange.getChange())).reversed());
        increases.sort(Comparator.comparingInt(SeasonDraftReportVO.BudgetPointChangeVO::getChange).reversed());
        decreases.sort(Comparator.comparingInt(SeasonDraftReportVO.BudgetPointChangeVO::getChange));

        SeasonDraftReportVO.CountsVO counts = new SeasonDraftReportVO.CountsVO(
                cardCount,
                newCardCount,
                cardsWithoutPrice,
                legalities,
                budgetPoints.getExchangeRate(),
                budgetPoints.getAdjustedExchangeRate(),
                budgetPoints.getPricesFixedByExchangeRateCount(),
                prices.getPricedDays(),
                metaShares.getTop50Rows(),
                metaShares.getTop150Rows(),
                newBans.size(),
                unbans.size(),
                newExtended.size(),
                noLongerExtended.size(),
                budgetPointChanges.size(),
                missingCards.size(),
                skippedCards.size(),
                oracleIdChanges.size(),
                renamedCards.size(),
                normalizedNameFixes.size()
        );

        int seasonNumber = currentSeason.getSeasonNumber() + 1;
        return new SeasonDraftReportVO(
                seasonNumber,
                RomanNumeral.of(seasonNumber),
                request.getStartDate(),
                request.getEndDate(),
                seasonDates.finalsFriday(request.getEndDate()),
                seasonDates.nextSeasonStart(request.getEndDate()),
                request.getPriceWindow().getStart(),
                request.getPriceWindow().getEnd(),
                printings.getMetaDate(),
                printings.getMetaVersion(),
                request.getMetaSource().toString(),
                currentSeason.getSeasonNumber(),
                LocalDateTime.now(Constants.TIMEZONE),
                request.getPreparedBy(),
                null,
                null,
                null,
                counts,
                metaShares.getDuplicateNames(),
                newBans,
                unbans,
                newExtended,
                noLongerExtended,
                budgetPointChanges,
                new ArrayList<>(increases.subList(0, Math.min(TOP_CHANGES, increases.size()))),
                new ArrayList<>(decreases.subList(0, Math.min(TOP_CHANGES, decreases.size()))),
                zeroBudgetPointCards,
                missingCards,
                skippedCards,
                oracleIdChanges,
                renamedCards,
                normalizedNameFixes,
                findSetsReleased(cards, printings, previousDataByOracleId, currentSeason),
                scryfallDecks(newBans, unbans, cards)
        );
    }

    // MTGJSON knows the upcoming sets long before they are out --> a set only counts once a card it printed first became playable. Commander sets, promos etc. count toward their main set, that's what the announcement lists.
    private static List<MtgSetVO> findSetsReleased(
            List<SeasonDraftCardVO> cards,
            MtgJsonPrintingsVO printings,
            Map<UUID, CardSeasonData> previousDataByOracleId,
            Season currentSeason
    ) {
        Map<String, MtgJsonSet> setsByCode = new HashMap<>(printings.getSets().size());
        for (MtgJsonSet mtgSet : printings.getSets()) {
            setsByCode.put(mtgSet.getCode(), mtgSet);
        }

        Map<String, Integer> newCardCountsBySetCode = new HashMap<>();
        for (SeasonDraftCardVO card : cards) {
            if (card.getSkipReason() != null || card.getLegality() == Legality.NOT_LEGAL) continue;

            UUID previousOracleId = card.getPreviousOracleId() != null ? card.getPreviousOracleId() : card.getOracleId();
            CardSeasonData previousData = previousDataByOracleId.get(previousOracleId);
            Legality previousLegality = previousData != null ? previousData.getLegality() : null;
            if (previousLegality != null && previousLegality != Legality.NOT_LEGAL) continue;

            MtgJsonCard mtgJsonCard = printings.getCardsByName().get(card.getName());
            if (mtgJsonCard == null) continue; // kept from the card table, no printing this season
            // An old card whose price only came back this season is no news
            if (mtgJsonCard.getFirstReleaseDate() == null || mtgJsonCard.getFirstReleaseDate().isBefore(currentSeason.getStartDate())) continue;

            newCardCountsBySetCode.merge(mtgJsonCard.getFirstSetCode(), 1, Integer::sum);
        }

        Map<String, Integer> newCardCountsByRootCode = new HashMap<>();
        Map<String, List<String>> childCodesByRootCode = new HashMap<>();
        for (Map.Entry<String, Integer> entry : newCardCountsBySetCode.entrySet()) {
            String rootCode = rootCodeOf(entry.getKey(), setsByCode);
            newCardCountsByRootCode.merge(rootCode, entry.getValue(), Integer::sum);
            List<String> childCodes = childCodesByRootCode.computeIfAbsent(rootCode, code -> new ArrayList<>());
            if (!entry.getKey().equals(rootCode)) childCodes.add(entry.getKey());
        }

        // The Commander decks sit on the commander set, not on the main set
        Map<String, List<String>> commanderDecksByRootCode = new HashMap<>();
        for (MtgJsonSet mtgSet : printings.getSets()) {
            String rootCode = rootCodeOf(mtgSet.getCode(), setsByCode);
            if (!newCardCountsByRootCode.containsKey(rootCode)) continue;

            List<String> commanderDecks = commanderDecksByRootCode.computeIfAbsent(rootCode, code -> new ArrayList<>());
            for (MtgJsonSet.MtgJsonDeck deck : mtgSet.getDecks()) {
                if (deck.getDeckType() != null && deck.getDeckType().contains("Commander")) commanderDecks.add(deck.getName());
            }
        }

        List<MtgSetVO> setsReleased = new ArrayList<>(newCardCountsByRootCode.size());
        for (MtgJsonSet mtgSet : printings.getSets()) {
            Integer newCardCount = newCardCountsByRootCode.get(mtgSet.getCode());
            if (newCardCount == null) continue;

            List<String> childCodes = childCodesByRootCode.get(mtgSet.getCode());
            childCodes.sort(Comparator.naturalOrder());
            List<MtgSetVO.ChildSetVO> childSets = new ArrayList<>(childCodes.size());
            for (String childCode : childCodes) {
                childSets.add(new MtgSetVO.ChildSetVO(setsByCode.get(childCode).getName(), childCode, newCardCountsBySetCode.get(childCode)));
            }
            setsReleased.add(new MtgSetVO(mtgSet.getName(), mtgSet.getCode(), mtgSet.getReleaseDate(), mtgSet.getSetType(), commanderDecksByRootCode.get(mtgSet.getCode()), childSets, newCardCount));
        }
        setsReleased.sort(Comparator.comparing(MtgSetVO::getReleaseDate).thenComparing(MtgSetVO::getCode));

        return setsReleased;
    }

    // A parent that isn't in the dump ends the walk, and so does a loop in the parent codes
    private static String rootCodeOf(String setCode, Map<String, MtgJsonSet> setsByCode) {
        Set<String> visitedCodes = new HashSet<>();
        String rootCode = setCode;
        MtgJsonSet mtgSet = setsByCode.get(rootCode);
        while (mtgSet != null && mtgSet.getParentCode() != null && setsByCode.containsKey(mtgSet.getParentCode()) && visitedCodes.add(rootCode)) {
            rootCode = mtgSet.getParentCode();
            mtgSet = setsByCode.get(rootCode);
        }

        return rootCode;
    }

    private static SeasonDraftReportVO.ScryfallDecksVO scryfallDecks(
            List<SeasonDraftReportVO.BanChangeVO> newBans,
            List<SeasonDraftReportVO.BanChangeVO> unbans,
            List<SeasonDraftCardVO> cards
    ) {
        List<String> newBanNames = new ArrayList<>(newBans.size());
        for (SeasonDraftReportVO.BanChangeVO newBan : newBans) {
            newBanNames.add(newBan.getName());
        }

        List<String> unbanNames = new ArrayList<>(unbans.size());
        for (SeasonDraftReportVO.BanChangeVO unban : unbans) {
            // A card that is still banned in paper or too expensive to play stays out of the unban deck
            if (unban.getBannedIn() != null || unban.isVintageRestricted()) continue;
            if (unban.getBudgetPoints() > CasualChallengeRules.MAX_BUDGET_POINTS) continue;

            unbanNames.add(unban.getName());
        }

        List<String> currentBanNames = new ArrayList<>();
        for (SeasonDraftCardVO card : cards) {
            if (card.getSkipReason() != null || card.getLegality() != Legality.BANNED) continue;
            if (card.getBudgetPoints() > CasualChallengeRules.MAX_BUDGET_POINTS) continue;

            currentBanNames.add(card.getName());
        }
        currentBanNames.sort(Comparator.naturalOrder());

        return new SeasonDraftReportVO.ScryfallDecksVO(
                String.join("\n", newBanNames),
                String.join("\n", unbanNames),
                String.join("\n", currentBanNames));
    }

    private static String findSkipReason(
            MtgJsonCard card,
            String normalizedName,
            Card cardWithSameName,
            Card cardWithSameOracleId,
            Map<String, Card> existingCardsByNormalizedName,
            Map<String, String> namesByNormalizedName,
            Map<UUID, String> namesByOracleId,
            Map<String, String> legalNamesByNormalizedName
    ) {
        String cardName = card.getName();
        UUID oracleId = card.getOracleId();

        // A card MTGJSON knows no vintage legality for never takes the name away from one it does, whichever of the two comes first
        String legalCardName = card.isVintageLegal() ? null : legalNamesByNormalizedName.get(normalizedName);
        if (legalCardName != null) return "duplicate normalized name '" + normalizedName + "' (first: '" + legalCardName + "')";

        String firstCardName = namesByNormalizedName.get(normalizedName);
        if (firstCardName != null) return "duplicate normalized name '" + normalizedName + "' (first: '" + firstCardName + "')";

        firstCardName = namesByOracleId.get(oracleId);
        if (firstCardName != null) return "duplicate oracle id '" + oracleId + "' (first: '" + firstCardName + "')";

        Card existingCard = existingCardsByNormalizedName.get(normalizedName);
        if (existingCard != null && !existingCard.getName().equals(cardName) && !existingCard.getOracleId().equals(oracleId)) {
            return "normalized name '" + normalizedName + "' already belongs to '" + existingCard.getName() + "'";
        }

        // A remap onto an oracle id that another card already holds would only blow up in the commit transaction
        if (cardWithSameName != null && !cardWithSameName.getOracleId().equals(oracleId)
                && cardWithSameOracleId != null && !cardWithSameOracleId.getName().equals(cardName)) {
            return "oracle id '" + oracleId + "' already belongs to '" + cardWithSameOracleId.getName() + "'";
        }

        return null;
    }

    private static SeasonDraftReportVO.RenamedCardVO toRenamedCard(Card existingCard, SeasonDraftCardVO card) {
        return new SeasonDraftReportVO.RenamedCardVO(
                card.getOracleId(),
                existingCard.getName(),
                existingCard.getNormalizedName(),
                card.getName(),
                card.getNormalizedName());
    }

    private static String findMissingReason(String cardName, Map<String, String> skipReasonsByName) {
        if (cardName == null) return "no card for the oracle id";

        String skipReason = skipReasonsByName.get(cardName);
        if (skipReason != null) return "skipped: " + skipReason;
        if (CasualChallengeRules.isFlipStyleName(cardName)) return "flip style";

        return "no eligible printing";
    }

    private static SeasonDraftReportVO.BanChangeVO toBanChange(SeasonDraftCardVO card) {
        Map<MtgFormat, BigDecimal> metaShares = new EnumMap<>(MtgFormat.class);
        if (card.getMetaShareStandard() != null) metaShares.put(MtgFormat.STANDARD, card.getMetaShareStandard());
        if (card.getMetaSharePioneer() != null) metaShares.put(MtgFormat.PIONEER, card.getMetaSharePioneer());
        if (card.getMetaShareModern() != null) metaShares.put(MtgFormat.MODERN, card.getMetaShareModern());
        if (card.getMetaShareLegacy() != null) metaShares.put(MtgFormat.LEGACY, card.getMetaShareLegacy());
        if (card.getMetaShareVintage() != null) metaShares.put(MtgFormat.VINTAGE, card.getMetaShareVintage());
        if (card.getMetaSharePauper() != null) metaShares.put(MtgFormat.PAUPER, card.getMetaSharePauper());

        return new SeasonDraftReportVO.BanChangeVO(card.getName(), card.getBudgetPoints(), metaShares, card.getBannedIn(), card.isVintageRestricted());
    }

    private static BigDecimal metaShareOf(Map<MtgFormat, BigDecimal> extendedBan, Map<MtgFormat, BigDecimal> ban, MtgFormat mtgFormat) {
        BigDecimal metaShare = extendedBan != null ? extendedBan.get(mtgFormat) : null;
        if (metaShare == null && ban != null) metaShare = ban.get(mtgFormat);
        if (metaShare == null) return null;

        return metaShare.setScale(3, RoundingMode.HALF_UP); // the ban files come with two decimals, the column holds three
    }

    private static boolean isRelevantChange(int change, int previousBudgetPoints) {
        int absoluteChange = Math.abs(change);
        return absoluteChange >= RELEVANT_CHANGE_IN_BUDGET_POINTS && absoluteChange * 100 >= previousBudgetPoints * RELEVANT_CHANGE_IN_PERCENT;
    }

}
