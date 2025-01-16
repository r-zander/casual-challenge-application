package gg.casualchallenge.application.mapper;

import gg.casualchallenge.application.model.values.CardVO;
import gg.casualchallenge.application.model.response.CardDTO;
import gg.casualchallenge.application.model.values.CardWithDataVO;
import gg.casualchallenge.application.persistence.entity.Card;
import gg.casualchallenge.application.persistence.entity.CardSeasonData;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

@Mapper
public interface CardMapper {
    CardMapper INSTANCE = Mappers.getMapper(CardMapper.class);

    CardVO toVO(Card card);

    CardDTO toDTO(CardWithDataVO cardVO);

    List<CardDTO> toDTOList(List<CardWithDataVO> cardVOs);
}
