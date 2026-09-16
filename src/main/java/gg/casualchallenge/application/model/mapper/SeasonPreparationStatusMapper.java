package gg.casualchallenge.application.model.mapper;

import gg.casualchallenge.application.api.datamodel.SeasonPreparationStatusResponse;
import gg.casualchallenge.application.model.values.SeasonPreparationStatusVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SeasonPreparationStatusMapper {
    SeasonPreparationStatusMapper INSTANCE = Mappers.getMapper(SeasonPreparationStatusMapper.class);

    SeasonPreparationStatusResponse toResponse(SeasonPreparationStatusVO seasonPreparationStatusVO);
}
