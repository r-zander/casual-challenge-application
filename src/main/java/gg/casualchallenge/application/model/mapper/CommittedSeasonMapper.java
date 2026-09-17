package gg.casualchallenge.application.model.mapper;

import gg.casualchallenge.application.api.datamodel.CommittedSeasonResponse;
import gg.casualchallenge.application.model.values.CommittedSeasonVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CommittedSeasonMapper {
    CommittedSeasonMapper INSTANCE = Mappers.getMapper(CommittedSeasonMapper.class);

    CommittedSeasonResponse toResponse(CommittedSeasonVO committedSeasonVO);
}
