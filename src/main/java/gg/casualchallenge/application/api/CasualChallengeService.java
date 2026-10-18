package gg.casualchallenge.application.api;

import gg.casualchallenge.application.common.CardNameNormalizer;
import gg.casualchallenge.application.model.mapper.CardMapper;
import gg.casualchallenge.application.model.type.AppliedRule;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.values.CardDataBatchVO;
import gg.casualchallenge.application.model.values.CardExplanationVO;
import gg.casualchallenge.application.model.values.CardPriceDetailsVO;
import gg.casualchallenge.application.model.values.CardVO;
import gg.casualchallenge.application.model.values.CardWithDataVO;
import gg.casualchallenge.application.model.values.SeasonDraftCardVO;
import gg.casualchallenge.application.model.values.SeasonDraftVO;
import gg.casualchallenge.application.persistence.CardRepository;
import gg.casualchallenge.application.persistence.CardSeasonDataRepository;
import gg.casualchallenge.application.persistence.SeasonDraftRepository;
import gg.casualchallenge.application.persistence.SeasonRepository;
import gg.casualchallenge.application.persistence.entity.Card;
import gg.casualchallenge.application.persistence.entity.CardSeasonData;
import gg.casualchallenge.application.persistence.entity.Season;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CasualChallengeService {

    private final SeasonRepository seasonRepository;
    private final CardRepository cardRepository;
    private final CardSeasonDataRepository cardSeasonDataRepository;
    private final SeasonDraftRepository seasonDraftRepository;

    // Requests wait for a season commit instead of answering from a cache without the new cards
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();

    private final Map<String, CardVO> cardCacheByNormalizedName = new HashMap<>();
    private final Map<UUID, CardVO> cardCacheByOracleId = new HashMap<>();

    public CasualChallengeService(
            SeasonRepository seasonRepository,
            CardRepository cardRepository,
            CardSeasonDataRepository cardSeasonDataRepository,
            SeasonDraftRepository seasonDraftRepository
    ) {
        this.seasonRepository = seasonRepository;
        this.cardRepository = cardRepository;
        this.cardSeasonDataRepository = cardSeasonDataRepository;
        this.seasonDraftRepository = seasonDraftRepository;
    }

    @PostConstruct
    public void preloadCards() {
        cacheLock.writeLock().lock();
        try {
            log.info("Start Preloading Cache.");
            List<Card> allCards = cardRepository.findAll();
            List<CardVO> cardVOs = allCards.stream()
                    .map(CardMapper.INSTANCE::toVO).toList();
            Map<String, CardVO> cardsByNormalizedName = cardVOs.stream()
                    .collect(Collectors.toMap(CardVO::getNormalizedName, cardVO -> cardVO));
            Map<UUID, CardVO> cardsByOracleId = cardVOs.stream()
                    .collect(Collectors.toMap(CardVO::getOracleId, cardVO -> cardVO));
            cardCacheByNormalizedName.clear();
            cardCacheByNormalizedName.putAll(cardsByNormalizedName);
            cardCacheByOracleId.clear();
            cardCacheByOracleId.putAll(cardsByOracleId);
            log.info("Preloading Cache done. Loaded {} cards into memory.", cardCacheByNormalizedName.size());
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    public void lockCards() {
        cacheLock.writeLock().lock();
    }

    public void unlockCards() {
        cacheLock.writeLock().unlock();
    }

    /**
     * @param seasonNumber null = current season
     */
    public CardDataBatchVO getCardData(Integer seasonNumber, List<String> cardNames, boolean displayExtended) {
        cacheLock.readLock().lock();
        try {
            Season season = getSeason(seasonNumber);

            Map<UUID, CardVO> foundCards = new HashMap<>();
            List<String> missingCardNames = new LinkedList<>();
            for (String cardName : cardNames) {
                CardVO cardVO = cardCacheByNormalizedName.get(CardNameNormalizer.normalize(cardName));
                if (cardVO == null) {
                    missingCardNames.add(cardName);
                } else {
                    foundCards.put(cardVO.getOracleId(), cardVO);
                }
            }

            List<CardSeasonData> cardDataList = cardSeasonDataRepository.findAllBySeasonAndCardOracleIdIn(season, foundCards.keySet());

            return new CardDataBatchVO(
                    buildCardWithDataVOs(displayExtended, cardDataList, foundCards),
                    missingCardNames
            );
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    public CardDataBatchVO getAllCardData(Integer seasonNumber, boolean displayExtended) {
        cacheLock.readLock().lock();
        try {
            Season season = getSeason(seasonNumber);

            List<CardSeasonData> cardDataList = cardSeasonDataRepository.findAllBySeason(season);

            return new CardDataBatchVO(
                    buildCardWithDataVOs(displayExtended, cardDataList, cardCacheByOracleId),
                    Collections.emptyList()
            );
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /** @param seasonNumber null = current season */
    public CardExplanationVO explainCard(Integer seasonNumber, String cardName) {
        cacheLock.readLock().lock();
        try {
            Season season = getSeason(seasonNumber);
            if (season == null) {
                if (seasonNumber == null) throw new IllegalArgumentException("There is no current season.");
                throw new IllegalArgumentException("There is no season " + seasonNumber + ".");
            }

            CardVO cardVO = cardCacheByNormalizedName.get(CardNameNormalizer.normalize(cardName));
            if (cardVO == null) throw new IllegalArgumentException("There is no card '" + cardName + "'.");

            List<CardSeasonData> cardDataList =
                    cardSeasonDataRepository.findAllBySeasonAndCardOracleIdIn(season, List.of(cardVO.getOracleId()));
            if (cardDataList.isEmpty()) {
                throw new IllegalArgumentException("'" + cardVO.getName() + "' has no data"
                        + " in season " + season.getSeasonNumber() + ".");
            }

            if (isBasicLand(cardVO.getName())) {
                throw new IllegalArgumentException("'" + cardVO.getName() + "' is a basic land,"
                        + " its budget points are always 0 - there are no prices to explain.");
            }

            CardSeasonData cardSeasonData = cardDataList.get(0);
            CardPriceDetailsVO priceDetails = seasonDraftRepository.findPriceDetails(cardSeasonData.getId());
            if (priceDetails == null) {
                throw new IllegalArgumentException("'" + cardVO.getName() + "' exists,"
                        + " but has no price details for season " + season.getSeasonNumber() + "."
                        + " Try running POST /admin/v1/season/" + season.getSeasonNumber() + "/price-details"
                        + " to generate them.");
            }

            return toExplanation(
                    cardVO.getName(),
                    cardVO.getOracleId(),
                    season.getSeasonNumber(),
                    cardSeasonData,
                    priceDetails
            );
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    public CardExplanationVO explainDraftCard(String cardName) {
        SeasonDraftVO draft = seasonDraftRepository.findUncommittedDraft();
        if (draft == null) throw new IllegalArgumentException("There is no draft to explain.");

        String normalizedName = CardNameNormalizer.normalize(cardName);
        SeasonDraftCardVO draftCard = seasonDraftRepository.findDraftCard(draft.getId(), normalizedName);
        if (draftCard == null) throw new IllegalArgumentException("'" + cardName + "' is not in the draft.");
        if (isBasicLand(draftCard.getName())) {
            throw new IllegalArgumentException("'" + draftCard.getName() + "' is a basic land,"
                    + " its budget points are always 0 - there are no prices to explain.");
        }
        if (draftCard.getPriceDetails() == null) {
            throw new IllegalArgumentException("'" + draftCard.getName() + "' is in the draft,"
                    + " but has no price details. Prepare the season again to get them.");
        }

        // The draft knows its new cards before the card cache does, so the draft card is all there is to go by
        CardSeasonData cardSeasonData = new CardSeasonData();
        cardSeasonData.setCardOracleId(draftCard.getOracleId());
        cardSeasonData.setBudgetPoints(draftCard.getBudgetPoints());
        cardSeasonData.setLegality(draftCard.getLegality());
        cardSeasonData.setMetaShareStandard(draftCard.getMetaShareStandard());
        cardSeasonData.setMetaSharePioneer(draftCard.getMetaSharePioneer());
        cardSeasonData.setMetaShareModern(draftCard.getMetaShareModern());
        cardSeasonData.setMetaShareLegacy(draftCard.getMetaShareLegacy());
        cardSeasonData.setMetaShareVintage(draftCard.getMetaShareVintage());
        cardSeasonData.setMetaSharePauper(draftCard.getMetaSharePauper());
        cardSeasonData.setBannedIn(draftCard.getBannedIn());
        cardSeasonData.setVintageRestricted(draftCard.isVintageRestricted());

        return toExplanation(
                draftCard.getName(),
                draftCard.getOracleId(),
                draft.getSeasonNumber(),
                cardSeasonData,
                draftCard.getPriceDetails()
        );
    }

    private CardExplanationVO toExplanation(
            String cardName,
            UUID oracleId,
            int seasonNumber,
            CardSeasonData cardSeasonData,
            CardPriceDetailsVO priceDetails
    ) {
        return new CardExplanationVO(
                cardName,
                oracleId,
                seasonNumber,
                cardSeasonData.getBudgetPoints(),
                cardSeasonData.getLegality(),
                determineReason(cardName, cardSeasonData, true),
                priceDetails
        );
    }

    private List<CardWithDataVO> buildCardWithDataVOs(boolean displayExtended, List<CardSeasonData> cardDataList, Map<UUID, CardVO> cardCacheByOracleId) {
        List<CardWithDataVO> cardWithDataVOs = new ArrayList<>(cardDataList.size());
        for (CardSeasonData cardSeasonData : cardDataList) {
            CardVO cardVO = cardCacheByOracleId.get(cardSeasonData.getCardOracleId());
            if (cardVO == null) throw new IllegalStateException("Card '" + cardSeasonData.getCardOracleId() + "' has season data but isn't in the card cache. Call POST /admin/v1/cards/reload.");
            cardWithDataVOs.add(new CardWithDataVO(
                    cardVO.getId(),
                    cardVO.getOracleId(),
                    cardVO.getName(),
                    cardVO.getNormalizedName(),

                    cardSeasonData.getBudgetPoints(),
                    filterLegality(cardSeasonData.getLegality(), displayExtended),
                    determineReason(cardVO.getName(), cardSeasonData, displayExtended)
            ));
        }

        return cardWithDataVOs;
    }

    private Season getSeason(Integer seasonNumber) {
        Season season;
        if (seasonNumber == null) {
            season = this.seasonRepository.findCurrentSeason();
        } else {
            season = this.seasonRepository.findBySeasonNumber(seasonNumber);
        }
        return season;
    }

    private Legality filterLegality(Legality legality, boolean displayExtended) {
        if (!displayExtended && legality == Legality.EXTENDED) {
            return Legality.LEGAL;
        }

        return legality;
    }

    private CardWithDataVO.ReasonVO determineReason(String cardName, CardSeasonData cardSeasonData, boolean displayExtended) {
        Map<MtgFormat, BigDecimal> metaShares = new HashMap<>();
        if (cardSeasonData.getMetaShareStandard() != null) {
            metaShares.put(MtgFormat.STANDARD, cardSeasonData.getMetaShareStandard());
        }
        if (cardSeasonData.getMetaSharePioneer() != null) {
            metaShares.put(MtgFormat.PIONEER, cardSeasonData.getMetaSharePioneer());
        }
        if (cardSeasonData.getMetaSharePauper() != null) {
            metaShares.put(MtgFormat.PAUPER, cardSeasonData.getMetaSharePauper());
        }
        if (cardSeasonData.getMetaShareModern() != null) {
            metaShares.put(MtgFormat.MODERN, cardSeasonData.getMetaShareModern());
        }
        if (cardSeasonData.getMetaShareLegacy() != null) {
            metaShares.put(MtgFormat.LEGACY, cardSeasonData.getMetaShareLegacy());
        }
        if (cardSeasonData.getMetaShareVintage() != null) {
            metaShares.put(MtgFormat.VINTAGE, cardSeasonData.getMetaShareVintage());
        }

        Set<MtgFormat> bannedIn;
        if (cardSeasonData.getBannedIn() != null) {
            bannedIn = EnumSet.of(cardSeasonData.getBannedIn());
        } else {
            bannedIn = EnumSet.noneOf(MtgFormat.class);
        }

        return new CardWithDataVO.ReasonVO(
                determineAppliedRules(
                        cardName,
                        cardSeasonData.getLegality(),
                        cardSeasonData.getBudgetPoints(),
                        cardSeasonData.isVintageRestricted(),
                        bannedIn,
                        displayExtended
                ),
                metaShares,
                bannedIn
        );
    }

    // TODO this should probably be just stored in the Database alongside the other data
    private Set<AppliedRule> determineAppliedRules(
            String cardName,
            Legality legality,
            Integer budgetPoints,
            boolean isVintageRestricted,
            Set<MtgFormat> bannedIn,
            boolean displayExtended
    ) {
        if (isBasicLand(cardName)) {
            return EnumSet.of(AppliedRule.NONSNOW_BASIC);
        }

        if (budgetPoints == null || budgetPoints == 0) {
            return EnumSet.of(AppliedRule.MISSING_BUDGET_POINTS);
        } else if (legality == Legality.NOT_LEGAL) {
            return EnumSet.of(AppliedRule.NOT_VINTAGE_LEGAL);
        }

        if (isVintageRestricted) {
            return EnumSet.of(AppliedRule.VINTAGE_RESTRICTED);
        }

        if (legality == Legality.BANNED) {
            if (bannedIn.isEmpty()) {
                return EnumSet.of(AppliedRule.TOURNAMENT_STAPLE);
            }

            return EnumSet.of(AppliedRule.PAPER_BAN);
        }

        if (displayExtended && legality == Legality.EXTENDED) {
            return EnumSet.of(AppliedRule.TOURNAMENT_STAPLE);
        }

        return EnumSet.of(AppliedRule.NO_BANS, AppliedRule.VINTAGE_LEGAL);
    }

    private boolean isBasicLand(String cardName) {
        switch (cardName) {
            case "Plains":
            case "Island":
            case "Swamp":
            case "Mountain":
            case "Forest":
            case "Wastes":
                return true;
        }

        return false;
    }
}
