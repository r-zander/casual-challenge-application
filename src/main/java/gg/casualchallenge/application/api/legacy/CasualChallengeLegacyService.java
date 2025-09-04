package gg.casualchallenge.application.api.legacy;

import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.api.legacy.values.BannedCardVO;
import gg.casualchallenge.application.api.legacy.values.CardBudgetPointsVO;
import gg.casualchallenge.application.api.legacy.datamodel.LegacyMtgFormat;
import gg.casualchallenge.application.persistence.CardRepository;
import gg.casualchallenge.application.persistence.CardSeasonDataRepository;
import gg.casualchallenge.application.persistence.SeasonRepository;
import gg.casualchallenge.application.persistence.entity.Card;
import gg.casualchallenge.application.persistence.entity.CardSeasonData;
import gg.casualchallenge.application.persistence.entity.Season;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CasualChallengeLegacyService {

    private final CardSeasonDataRepository cardSeasonDataRepository;
    private final CardRepository cardRepository;
    private final SeasonRepository seasonRepository;

    public List<BannedCardVO> findAllBannedCards(int seasonNumber) {
        Season season = seasonRepository.findBySeasonNumber(seasonNumber);
        List<CardSeasonData> bannedCards = cardSeasonDataRepository.findAllByMetaBan(season, Legality.BANNED);
        Map<UUID, Card> cardMap = getCardMap(bannedCards);
        
        return bannedCards.stream()
                .map(cardSeasonData -> {
                    Card card = cardMap.get(cardSeasonData.getCardOracleId());
                    return new BannedCardVO(
                            cardSeasonData.getCardOracleId(),
                            card != null ? card.getName() : null,
                            extractMetaShares(cardSeasonData)
                    );
                })
                .collect(Collectors.toList());
    }

    public List<BannedCardVO> findAllExtendedBannedCards(int seasonNumber) {
        Season season = seasonRepository.findBySeasonNumber(seasonNumber);
        List<CardSeasonData> extendedBannedCards = cardSeasonDataRepository.findAllByMetaBan(season, Legality.EXTENDED);
        Map<UUID, Card> cardMap = getCardMap(extendedBannedCards);
        
        return extendedBannedCards.stream()
                .map(cardSeasonData -> {
                    Card card = cardMap.get(cardSeasonData.getCardOracleId());
                    return new BannedCardVO(
                            cardSeasonData.getCardOracleId(),
                            card != null ? card.getName() : null,
                            extractMetaShares(cardSeasonData)
                    );
                })
                .collect(Collectors.toList());
    }

    public List<CardBudgetPointsVO> findAllCardsWithBudgetPoints(int seasonNumber) {
        Season season = seasonRepository.findBySeasonNumber(seasonNumber);
        List<CardSeasonData> allCards = cardSeasonDataRepository.findAllBySeason(season);
        Map<UUID, Card> cardMap = getCardMap(allCards);
        
        return allCards.stream()
                .map(cardSeasonData -> {
                    Card card = cardMap.get(cardSeasonData.getCardOracleId());
                    return new CardBudgetPointsVO(
                            cardSeasonData.getCardOracleId(),
                            card != null ? card.getName() : null,
                            cardSeasonData.getBudgetPoints()
                    );
                })
                .collect(Collectors.toList());
    }

    private Map<UUID, Card> getCardMap(List<CardSeasonData> cardSeasonDataList) {
        List<UUID> oracleIds = cardSeasonDataList.stream()
                .map(CardSeasonData::getCardOracleId)
                .collect(Collectors.toList());
        
        return cardRepository.findAll().stream()
                .filter(card -> oracleIds.contains(card.getOracleId()))
                .collect(Collectors.toMap(Card::getOracleId, Function.identity()));
    }

    private Map<LegacyMtgFormat, BigDecimal> extractMetaShares(CardSeasonData cardSeasonData) {
        Map<LegacyMtgFormat, BigDecimal> metaShares = new HashMap<>();
        
        if (cardSeasonData.getMetaShareStandard() != null) {
            metaShares.put(LegacyMtgFormat.STANDARD, cardSeasonData.getMetaShareStandard());
        }
        if (cardSeasonData.getMetaSharePioneer() != null) {
            metaShares.put(LegacyMtgFormat.PIONEER, cardSeasonData.getMetaSharePioneer());
        }
        if (cardSeasonData.getMetaShareModern() != null) {
            metaShares.put(LegacyMtgFormat.MODERN, cardSeasonData.getMetaShareModern());
        }
        if (cardSeasonData.getMetaShareLegacy() != null) {
            metaShares.put(LegacyMtgFormat.LEGACY, cardSeasonData.getMetaShareLegacy());
        }
        if (cardSeasonData.getMetaShareVintage() != null) {
            metaShares.put(LegacyMtgFormat.VINTAGE, cardSeasonData.getMetaShareVintage());
        }
        if (cardSeasonData.getMetaSharePauper() != null) {
            metaShares.put(LegacyMtgFormat.PAUPER, cardSeasonData.getMetaSharePauper());
        }
        
        return metaShares;
    }
}
