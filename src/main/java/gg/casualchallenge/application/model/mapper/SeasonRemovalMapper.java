package gg.casualchallenge.application.model.mapper;

import gg.casualchallenge.application.api.datamodel.RemovedSeasonResponse;
import gg.casualchallenge.application.api.datamodel.SeasonRemovalPreviewResponse;
import gg.casualchallenge.application.model.values.RemovedSeasonVO;
import gg.casualchallenge.application.model.values.SeasonRemovalPreviewVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SeasonRemovalMapper {
    SeasonRemovalMapper INSTANCE = Mappers.getMapper(SeasonRemovalMapper.class);

    SeasonRemovalPreviewResponse toResponse(SeasonRemovalPreviewVO seasonRemovalPreviewVO);

    RemovedSeasonResponse toResponse(RemovedSeasonVO removedSeasonVO);
}
