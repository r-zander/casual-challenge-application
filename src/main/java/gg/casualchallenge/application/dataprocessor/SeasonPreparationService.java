package gg.casualchallenge.application.dataprocessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.casualchallenge.application.common.CardNameNormalizer;
import gg.casualchallenge.application.common.Constants;
import gg.casualchallenge.application.common.RomanNumeral;
import gg.casualchallenge.application.common.SeasonDates;
import gg.casualchallenge.application.dataprocessor.model.BudgetPointsVO;
import gg.casualchallenge.application.dataprocessor.model.CardPrices;
import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import gg.casualchallenge.application.dataprocessor.model.MetaSharesVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonCard;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPricesVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonPrintingsVO;
import gg.casualchallenge.application.dataprocessor.model.MtgJsonSet;
import gg.casualchallenge.application.dataprocessor.model.PreparedSeasonVO;
import gg.casualchallenge.application.dataprocessor.model.PriceWindowVO;
import gg.casualchallenge.application.dataprocessor.model.SeasonPreparationState;
import gg.casualchallenge.application.dataprocessor.model.Staple;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.values.MtgSetVO;
import gg.casualchallenge.application.model.values.SeasonDraftCardVO;
import gg.casualchallenge.application.model.values.SeasonDraftReportVO;
import gg.casualchallenge.application.model.values.SeasonDraftVO;
import gg.casualchallenge.application.model.values.SeasonPreparationJobVO;
import gg.casualchallenge.application.model.values.SeasonPreparationRequestVO;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private static final int TOTAL_STEPS = 5;

    private static final int MAX_SKIP_REASON_LENGTH = 255;

    private static final int SHUTDOWN_TIMEOUT_IN_SECONDS = 30;

    // A budget point change is only worth reading when it is big relatively and absolutely
    private static final int RELEVANT_CHANGE_IN_PERCENT = 50;
    private static final int RELEVANT_CHANGE_IN_BUDGET_POINTS = 100;
    private static final int TOP_CHANGES = 20;

    // Cards above this are out of reach anyway, so the Scryfall decks don't bother listing them
    private static final int MAX_BUDGET_POINTS = 2500;

    private static final Set<String> PLAYABLE_SET_TYPES = Set.of("expansion", "core", "masters", "draft_innovation", "commander");

    private final MtgJsonClient mtgJsonClient;
    private final MtgGoldfishClient mtgGoldfishClient;
    private final MtgTop8Client mtgTop8Client;
    private final SeasonRepository seasonRepository;
    private final CardRepository cardRepository;
    private final CardSeasonDataRepository cardSeasonDataRepository;
    private final SeasonDraftRepository seasonDraftRepository;
    private final ObjectMapper objectMapper;
    private final int priceWindowDays;

    private final ExecutorService jobExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "season-preparation");
        }
    });
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    private volatile SeasonPreparationJobVO job = new SeasonPreparationJobVO(SeasonPreparationState.IDLE, null, null, null, null);

    public SeasonPreparationService(
            MtgJsonClient mtgJsonClient,
            MtgGoldfishClient mtgGoldfishClient,
            MtgTop8Client mtgTop8Client,
            SeasonRepository seasonRepository,
            CardRepository cardRepository,
            CardSeasonDataRepository cardSeasonDataRepository,
            SeasonDraftRepository seasonDraftRepository,
            ObjectMapper objectMapper,
            @Value("${casual-challenge.season.price-window-days}") int priceWindowDays
    ) {
        this.mtgJsonClient = mtgJsonClient;
        this.mtgGoldfishClient = mtgGoldfishClient;
        this.mtgTop8Client = mtgTop8Client;
        this.seasonRepository = seasonRepository;
        this.cardRepository = cardRepository;
        this.cardSeasonDataRepository = cardSeasonDataRepository;
        this.seasonDraftRepository = seasonDraftRepository;
        this.objectMapper = objectMapper;
        this.priceWindowDays = priceWindowDays;
    }

    public synchronized SeasonPreparationJobVO prepare(SeasonPreparationRequestVO request) {
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new IllegalArgumentException("A season that starts on " + request.getStartDate() + " can't end on " + request.getEndDate() + ".");
        }
        if (request.getPriceWindow().getEnd().isAfter(request.getStartDate())) {
            throw new IllegalArgumentException("The price window has to end at the start of the season at the latest, but it ends on " + request.getPriceWindow().getEnd() + ".");
        }
        if (job.getState() == SeasonPreparationState.RUNNING) {
            throw new IllegalStateException("A season is already being prepared since " + job.getStartedAt() + ".");
        }

        cancelRequested.set(false);
        job = new SeasonPreparationJobVO(SeasonPreparationState.RUNNING, "Untap, Upkeep, Draw!", LocalDateTime.now(Constants.TIMEZONE), null, null);
        jobExecutor.submit(() -> runJob(request));

        return job;
    }

    public SeasonPreparationJobVO getJob() {
        return job;
    }

    public void cancel() {
        cancelRequested.set(true);
    }

    @PreDestroy
    public void shutdown() {
        cancelRequested.set(true); // the job only gives up between steps, interrupting it alone doesn't help
        jobExecutor.shutdownNow(); // the executor thread is not a daemon --> the JVM would wait for it on every deploy
        try {
            jobExecutor.awaitTermination(SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public SeasonPreparationRequestVO defaultRequest(LocalDate startDate) {
        Season currentSeason = seasonRepository.findCurrentSeason();
        if (currentSeason == null) {
            throw new IllegalStateException("There is no current season to continue.");
        }

        LocalDate endDate = SeasonDates.defaultEndDate(currentSeason.getEndDate());
        if (!endDate.isAfter(startDate)) {
            endDate = SeasonDates.defaultEndDate(startDate); // the previous season ended ages ago --> count the ten weeks from the new start instead
        }

        return new SeasonPreparationRequestVO(
                startDate,
                endDate,
                PriceWindowVO.of(startDate, priceWindowDays),
                MetaShareSource.MTGGOLDFISH,
                null,
                null
        );
    }

    private void runJob(SeasonPreparationRequestVO request) {
        try {
            log.info("0 / {} | Untap, Upkeep, Draw!", TOTAL_STEPS);
            SeasonDraftVO draft = prepareSeason(request);
            if (draft == null) {
                log.info("Preparing a new season was cancelled. Nothing was written.");
                job = new SeasonPreparationJobVO(SeasonPreparationState.CANCELLED, job.getStep(), job.getStartedAt(), LocalDateTime.now(Constants.TIMEZONE), null);
                return;
            }

            job = new SeasonPreparationJobVO(
                    SeasonPreparationState.DONE,
                    "Season " + draft.getSeasonNumber() + " is ready for review.",
                    job.getStartedAt(),
                    LocalDateTime.now(Constants.TIMEZONE),
                    null
            );
        } catch (Throwable throwable) {
            log.error("Preparing a new season failed.", throwable);
            job = new SeasonPreparationJobVO(
                    SeasonPreparationState.FAILED,
                    job.getStep(),
                    job.getStartedAt(),
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

        startStep(1, "Reading AllPrintings.json");
        MtgJsonPrintingsVO printings = mtgJsonClient.fetchPrintings();
        LocalDate lastPricedDay = printings.getMetaDate();
        if (lastPricedDay != null && request.getPriceWindow().getEnd().isAfter(lastPricedDay.plusDays(1))) {
            throw new IllegalStateException("MTGJSON has prices until " + lastPricedDay + ", the price window ends " + request.getPriceWindow().getEnd()
                    + " --> prepare on the season start date or pass an earlier priceWindowEnd.");
        }
        if (cancelRequested.get()) return null;

        startStep(2, "Reading AllPrices.json");
        MtgJsonPricesVO prices = mtgJsonClient.fetchPrices(printings.getPrintingsByUuid(), request.getPriceWindow());
        if (cancelRequested.get()) return null;

        startStep(3, "Reading meta shares from " + request.getMetaSource());
        MetaSharesVO metaShares = fetchMetaShares(request);
        if (cancelRequested.get()) return null;

        startStep(4, "Calculating budget points and assembling the draft - the big step");
        PreparedSeasonVO preparedSeason = assemble(
                printings,
                prices,
                metaShares,
                cardRepository.findAll(),
                cardSeasonDataRepository.findAllBySeason(currentSeason),
                request,
                currentSeason
        );
        if (cancelRequested.get()) return null;

        startStep(5, "Storing the season draft");
        SeasonDraftVO draft = toDraft(preparedSeason.getReport(), printings, request, currentSeason);
        seasonDraftRepository.replace(draft, preparedSeason.getCards());
        log.info("{} / {} | All done. Season {} is ready for review with {} cards.", TOTAL_STEPS, TOTAL_STEPS, draft.getSeasonNumber(), preparedSeason.getCards().size());

        return draft;
    }

    private void startStep(int step, String description) {
        log.info("{} / {} | {}", step, TOTAL_STEPS, description);
        job = new SeasonPreparationJobVO(SeasonPreparationState.RUNNING, description, job.getStartedAt(), null, null);
    }

    private MetaSharesVO fetchMetaShares(SeasonPreparationRequestVO request) {
        if (request.getMetaSource() == MetaShareSource.FILES) {
            return MetaSharesVO.fromBanFiles(request.getBans(), request.getExtendedBans());
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
                currentSeason.getUpdatedAt(),
                printings.getMetaDate() != null ? printings.getMetaDate().toString() : null,
                request.getMetaSource().toString(),
                report.getPreparedAt(),
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

    public static PreparedSeasonVO assemble(
            MtgJsonPrintingsVO printings,
            MtgJsonPricesVO prices,
            MetaSharesVO metaShares,
            List<Card> existingCards,
            List<CardSeasonData> previousSeasonData,
            SeasonPreparationRequestVO request,
            Season currentSeason
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

        List<SeasonDraftCardVO> cards = new ArrayList<>(printings.getCardsByName().size());
        Map<String, String> namesByNormalizedName = new HashMap<>(printings.getCardsByName().size());
        Map<UUID, String> namesByOracleId = new HashMap<>(printings.getCardsByName().size());

        for (MtgJsonCard card : printings.getCardsByName().values()) {
            String cardName = card.getName();
            if (CasualChallengeRules.isFlipStyleName(cardName) || card.getOracleId() == null) continue;

            Card cardWithSameName = existingCardsByName.get(cardName);
            Card cardWithSameOracleId = existingCardsByOracleId.get(card.getOracleId());
            String normalizedName = CasualChallengeRules.isCommaCard(cardName) ? CardNameNormalizer.somewhatNormalize(cardName) : CardNameNormalizer.normalize(cardName);
            String skipReason = findSkipReason(cardName, normalizedName, card.getOracleId(), cardWithSameName, cardWithSameOracleId, existingCardsByNormalizedName, namesByNormalizedName, namesByOracleId);
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
            Integer cardBudgetPoints = budgetPoints.getBudgetPointsByCardName().get(cardName);
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

        return new PreparedSeasonVO(cards, buildReport(cards, existingCardsByOracleId, previousSeasonData, printings, metaShares, budgetPoints, prices, request, currentSeason));
    }

    private static BudgetPointsVO calculateBudgetPoints(MtgJsonPrintingsVO printings, Map<String, CardPrices> pricesByCardName) {
        Map<String, Integer> eurCentsByCardName = new HashMap<>(printings.getCardsByName().size());
        Map<String, Integer> usdCentsByCardName = new HashMap<>(printings.getCardsByName().size());
        double totalExchangeRate = 0;
        int exchangeRateCount = 0;

        for (String cardName : printings.getCardsByName().keySet()) {
            CardPrices cardPrices = pricesByCardName.get(cardName);
            // Basics are free and their prices would only spoil the exchange rate
            if (CasualChallengeRules.isBasicLand(cardName) || cardPrices == null) {
                eurCentsByCardName.put(cardName, 0);
                usdCentsByCardName.put(cardName, 0);
                continue;
            }

            double eurAverage = cardPrices.getEur().average();
            double usdAverage = cardPrices.getUsd().average();
            eurCentsByCardName.put(cardName, BudgetPoints.fromAverage(eurAverage));
            usdCentsByCardName.put(cardName, BudgetPoints.fromAverage(usdAverage));
            if (eurAverage > 0) {
                totalExchangeRate += usdAverage / eurAverage;
                exchangeRateCount++;
            }
        }

        double exchangeRate = exchangeRateCount > 0 ? totalExchangeRate / exchangeRateCount : 0;
        double adjustedExchangeRate = BudgetPoints.adjustExchangeRate(exchangeRate);
        log.info("Average exchange rate is {}, adjusted to {}.", exchangeRate, adjustedExchangeRate);

        int pricesFixedByExchangeRate = 0;
        for (Map.Entry<String, Integer> entry : eurCentsByCardName.entrySet()) {
            if (entry.getValue() != 0) continue;

            int usdCents = usdCentsByCardName.get(entry.getKey());
            if (usdCents == 0) continue;

            entry.setValue(BudgetPoints.fromUsd(usdCents, adjustedExchangeRate));
            pricesFixedByExchangeRate++;
        }
        log.info("Fixed {} card prices with the exchange rate.", pricesFixedByExchangeRate);

        return new BudgetPointsVO(eurCentsByCardName, exchangeRate, adjustedExchangeRate, pricesFixedByExchangeRate);
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
            Season currentSeason
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
                budgetPoints.getPricesFixedByExchangeRate(),
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
                SeasonDates.finalsFriday(request.getEndDate()),
                SeasonDates.nextSeasonStart(request.getEndDate()),
                request.getPriceWindow().getStart(),
                request.getPriceWindow().getEnd(),
                printings.getMetaDate(),
                printings.getMetaVersion(),
                request.getMetaSource().toString(),
                currentSeason.getSeasonNumber(),
                LocalDateTime.now(Constants.TIMEZONE),
                counts,
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
                findSetsReleased(printings, request),
                scryfallDecks(newBans, unbans, cards)
        );
    }

    private static List<MtgSetVO> findSetsReleased(MtgJsonPrintingsVO printings, SeasonPreparationRequestVO request) {
        List<MtgSetVO> setsReleased = new ArrayList<>();
        for (MtgJsonSet mtgSet : printings.getSets()) {
            if (mtgSet.getReleaseDate() == null || mtgSet.isOnlineOnly()) continue;
            if (!PLAYABLE_SET_TYPES.contains(mtgSet.getType())) continue;
            if (mtgSet.getReleaseDate().isBefore(request.getStartDate()) || mtgSet.getReleaseDate().isAfter(request.getEndDate())) continue;

            List<String> commanderDecks = new ArrayList<>();
            for (MtgJsonSet.MtgJsonDeck deck : mtgSet.getDecks()) {
                if (deck.getType() != null && deck.getType().contains("Commander")) commanderDecks.add(deck.getName());
            }
            setsReleased.add(new MtgSetVO(mtgSet.getName(), mtgSet.getCode(), mtgSet.getReleaseDate(), mtgSet.getType(), commanderDecks));
        }

        return setsReleased;
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
            if (unban.getBudgetPoints() > MAX_BUDGET_POINTS) continue;

            unbanNames.add(unban.getName());
        }

        List<String> currentBanNames = new ArrayList<>();
        for (SeasonDraftCardVO card : cards) {
            if (card.getSkipReason() != null || card.getLegality() != Legality.BANNED) continue;
            if (card.getBudgetPoints() > MAX_BUDGET_POINTS) continue;

            currentBanNames.add(card.getName());
        }
        currentBanNames.sort(Comparator.naturalOrder());

        return new SeasonDraftReportVO.ScryfallDecksVO(
                String.join("\n", newBanNames),
                String.join("\n", unbanNames),
                String.join("\n", currentBanNames));
    }

    private static String findSkipReason(
            String cardName,
            String normalizedName,
            UUID oracleId,
            Card cardWithSameName,
            Card cardWithSameOracleId,
            Map<String, Card> existingCardsByNormalizedName,
            Map<String, String> namesByNormalizedName,
            Map<UUID, String> namesByOracleId
    ) {
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
