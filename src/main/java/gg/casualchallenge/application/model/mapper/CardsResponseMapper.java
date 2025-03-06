package gg.casualchallenge.application.model.mapper;

import gg.casualchallenge.application.api.datamodel.CardsResponse;
import gg.casualchallenge.application.model.values.CardDataBatchVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CardsResponseMapper {
    CardsResponseMapper INSTANCE = Mappers.getMapper(CardsResponseMapper.class);

    CardsResponse toResponse(CardDataBatchVO cardDataBatchVO);
}
