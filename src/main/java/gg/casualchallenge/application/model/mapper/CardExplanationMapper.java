package gg.casualchallenge.application.model.mapper;

import gg.casualchallenge.application.api.datamodel.CardExplanationResponse;
import gg.casualchallenge.application.model.values.CardExplanationVO;
import gg.casualchallenge.application.model.values.CardPriceDetailsVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Mapper
public interface CardExplanationMapper {
    CardExplanationMapper INSTANCE = Mappers.getMapper(CardExplanationMapper.class);

    CardExplanationResponse toResponse(CardExplanationVO cardExplanationVO);

    List<CardExplanationResponse.PrintingPriceDTO> toPrintingPriceDTOs(List<CardPriceDetailsVO.PrintingPriceVO> printingPriceVOs);

    List<CardExplanationResponse.IgnoredPrintingDTO> toIgnoredPrintingDTOs(List<CardPriceDetailsVO.IgnoredPrintingVO> ignoredPrintingVOs);

    default CardExplanationResponse.PriceDetailsDTO toDTO(CardPriceDetailsVO priceDetailsVO) {
        return new CardExplanationResponse.PriceDetailsDTO(
                priceDetailsVO.getPriceWindowStart(),
                priceDetailsVO.getPriceWindowEnd(),
                priceDetailsVO.getPricedBy(),
                priceDetailsVO.getAdjustedExchangeRate(),
                toDTO(priceDetailsVO.getCardmarket(), priceDetailsVO.getPriceWindowStart()),
                toDTO(priceDetailsVO.getTcgplayer(), priceDetailsVO.getPriceWindowStart()),
                toIgnoredPrintingDTOs(priceDetailsVO.getIgnoredPrintings())
        );
    }

    default CardExplanationResponse.MarketPricesDTO toDTO(CardPriceDetailsVO.MarketPricesVO marketPricesVO, LocalDate priceWindowStart) {
        List<Integer> cheapestCentsPerDay = marketPricesVO.getCheapestCentsPerDay();
        List<CardExplanationResponse.DayPriceDTO> days = new ArrayList<>(cheapestCentsPerDay.size());
        for (int day = 0; day < cheapestCentsPerDay.size(); day++) {
            Integer printingIndex = marketPricesVO.getCheapestPrintingPerDay().get(day);
            CardPriceDetailsVO.PrintingPriceVO printing = cheapestCentsPerDay.get(day) != null ? marketPricesVO.getPrintings().get(printingIndex) : null;
            days.add(new CardExplanationResponse.DayPriceDTO(
                    priceWindowStart.plusDays(day),
                    cheapestCentsPerDay.get(day),
                    printing != null ? printing.getSetCode() : null,
                    printing != null ? printing.getNumber() : null,
                    printing != null ? printing.getFinish() : null
            ));
        }

        return new CardExplanationResponse.MarketPricesDTO(
                marketPricesVO.getPricedDays(),
                marketPricesVO.getAverageCents(),
                marketPricesVO.isOnlyFlatPrintings(),
                toPrintingPriceDTOs(marketPricesVO.getPrintings()),
                days
        );
    }
}
