package gg.casualchallenge.application.api.legacy.mapper;

import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.api.legacy.values.BannedCardVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface BansResponseMapper {
    BansResponseMapper INSTANCE = Mappers.getMapper(BansResponseMapper.class);

    @Mapping(source = "cardName", target = "name")
    @Mapping(source = "metaShares", target = "formats")
    BanDTO toResponse(BannedCardVO bannedCardVO);

    List<BanDTO> toResponse(List<BannedCardVO> bannedCardVOs);
}
