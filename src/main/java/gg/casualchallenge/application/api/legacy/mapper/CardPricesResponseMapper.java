package gg.casualchallenge.application.api.legacy.mapper;

import gg.casualchallenge.application.api.legacy.values.CardBudgetPointsVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;

@Mapper
public interface CardPricesResponseMapper {
    CardPricesResponseMapper INSTANCE = Mappers.getMapper(CardPricesResponseMapper.class);

    default Map<String, Integer> toResponse(List<CardBudgetPointsVO> cardBudgetPointsVOs) {
        return cardBudgetPointsVOs.stream()
                .filter(vo -> vo.getCardName() != null && vo.getBudgetPoints() != null)
                .collect(java.util.stream.Collectors.toMap(
                        CardBudgetPointsVO::getCardName,
                        CardBudgetPointsVO::getBudgetPoints,
                        (existing, replacement) -> existing,
                        java.util.TreeMap::new
                ));
    }
}
