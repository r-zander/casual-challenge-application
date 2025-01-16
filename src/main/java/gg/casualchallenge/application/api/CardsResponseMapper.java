package gg.casualchallenge.application.api;

import gg.casualchallenge.application.mapper.CardMapper;
import gg.casualchallenge.application.model.response.CardsResponse;
import gg.casualchallenge.application.model.values.CardDataBatchVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CardsResponseMapper {
    CardsResponseMapper INSTANCE = Mappers.getMapper(CardsResponseMapper.class);

    CardsResponse toResponse(CardDataBatchVO cardDataBatchVO);
}
