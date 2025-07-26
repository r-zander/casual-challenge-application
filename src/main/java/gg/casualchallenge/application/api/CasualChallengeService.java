package gg.casualchallenge.application.api;

import gg.casualchallenge.application.model.mapper.CardMapper;
import gg.casualchallenge.application.model.type.AppliedRule;
import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.application.model.values.CardDataBatchVO;
import gg.casualchallenge.application.model.values.CardVO;
import gg.casualchallenge.application.model.values.CardWithDataVO;
import gg.casualchallenge.application.persistence.CardRepository;
import gg.casualchallenge.application.persistence.CardSeasonDataRepository;
import gg.casualchallenge.application.persistence.SeasonRepository;
import gg.casualchallenge.application.persistence.entity.Card;
import gg.casualchallenge.application.persistence.entity.CardSeasonData;
import gg.casualchallenge.application.persistence.entity.Season;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CasualChallengeService {

    private final SeasonRepository seasonRepository;
    private final CardRepository cardRepository;
    private final CardSeasonDataRepository cardSeasonDataRepository;

    private final Map<String, CardVO> cardCache = new HashMap<>();

    public CasualChallengeService(
            SeasonRepository seasonRepository,
            CardRepository cardRepository,
            CardSeasonDataRepository cardSeasonDataRepository
    ) {
        this.seasonRepository = seasonRepository;
        this.cardRepository = cardRepository;
        this.cardSeasonDataRepository = cardSeasonDataRepository;
    }

    @PostConstruct
    public void preloadCards() {
        log.info("Start Preloading Cache.");
        List<Card> allCards = cardRepository.findAll();
        cardCache.putAll(allCards.stream()
                .map(CardMapper.INSTANCE::toVO)
                .collect(Collectors.toMap(CardVO::getNormalizedName, cardVO -> cardVO)));
        log.info("Preloading Cache done. Loaded {} cards into memory.", cardCache.size());
    }

    /**
     * @param seasonNumber null = current season
     */
    public CardDataBatchVO getCardData(Integer seasonNumber, List<String> cardNames, boolean displayExtended) {
        Season season;
        if (seasonNumber == null) {
            season = this.seasonRepository.findCurrentSeason();
        } else {
            season = this.seasonRepository.findBySeasonNumber(seasonNumber);
        }

        Map<UUID, CardVO> foundCards = new HashMap<>();
        List<String> missingCardNames = new LinkedList<>();
        for (String cardName : cardNames) {
            CardVO cardVO = cardCache.get(normalizeCardName(cardName));
            if (cardVO == null) {
                missingCardNames.add(cardName);
            } else {
                foundCards.put(cardVO.getOracleId(), cardVO);
            }
        }

        List<CardSeasonData> cardDataList = cardSeasonDataRepository.findAllBySeasonAndCardOracleIdIn(season, foundCards.keySet());
        List<CardWithDataVO> cardWithDataVOs = new ArrayList<>(cardDataList.size());
        for (CardSeasonData cardSeasonData : cardDataList) {
            CardVO cardVO = foundCards.get(cardSeasonData.getCardOracleId());
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

        return new CardDataBatchVO(cardWithDataVOs, missingCardNames);
    }

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
    public static String normalizeCardName(String cardName) {
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
