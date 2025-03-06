package gg.casualchallenge.application.model.mapper;

import gg.casualchallenge.application.api.datamodel.CreatedSeasonResponse;
import gg.casualchallenge.application.model.values.CreatedSeasonVO;
import gg.casualchallenge.application.model.values.SeasonVO;
import gg.casualchallenge.application.persistence.entity.Season;
import org.mapstruct.factory.Mappers;

public interface NewSeasonMapper {
    NewSeasonMapper INSTANCE = Mappers.getMapper(NewSeasonMapper.class);

    // TODO
    CreatedSeasonResponse toDTO(CreatedSeasonVO vo);


}
