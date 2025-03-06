package gg.casualchallenge.application.model.mapper;

import gg.casualchallenge.application.model.values.SeasonVO;
import gg.casualchallenge.application.persistence.entity.Season;
import org.mapstruct.factory.Mappers;

public interface SeasonMapper {
    SeasonMapper INSTANCE = Mappers.getMapper(SeasonMapper.class);

    SeasonVO toVO(Season season);


}
