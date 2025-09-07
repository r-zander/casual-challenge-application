package gg.casualchallenge.application.api.legacy.mapper;

import gg.casualchallenge.application.api.legacy.datamodel.SeasonInfoDTO;
import gg.casualchallenge.application.api.legacy.datamodel.SeasonUrlsDTO;
import gg.casualchallenge.application.model.values.SeasonVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SeasonInfoMapper {
    SeasonInfoMapper INSTANCE = Mappers.getMapper(SeasonInfoMapper.class);
    
    @Mapping(target = "urls", source = "seasonUrls")
    SeasonInfoDTO toResponse(SeasonVO seasonVO, SeasonUrlsDTO seasonUrls);

}
