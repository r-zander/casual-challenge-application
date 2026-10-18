package gg.casualchallenge.application.model.mapper;

import gg.casualchallenge.application.api.datamodel.RebuiltPriceDetailsResponse;
import gg.casualchallenge.application.model.values.RebuiltPriceDetailsVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface RebuiltPriceDetailsMapper {
    RebuiltPriceDetailsMapper INSTANCE = Mappers.getMapper(RebuiltPriceDetailsMapper.class);

    RebuiltPriceDetailsResponse toResponse(RebuiltPriceDetailsVO rebuiltPriceDetailsVO);
}
