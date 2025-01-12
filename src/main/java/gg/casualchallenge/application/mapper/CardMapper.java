package gg.casualchallenge.application.mapper;

import gg.casualchallenge.application.model.values.CardVO;
import gg.casualchallenge.application.model.response.CardDTO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

@Mapper
public interface CardMapper {
    CardMapper INSTANCE = Mappers.getMapper(CardMapper.class);

    CardDTO toDTO(CardVO cardVO);

    List<CardDTO> toDTOList(List<CardVO> cardVOs);
}
