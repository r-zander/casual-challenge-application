package gg.casualchallenge.application.model.mapper;

import gg.casualchallenge.application.model.values.CardVO;
import gg.casualchallenge.application.api.datamodel.CardDTO;
import gg.casualchallenge.application.model.values.CardWithDataVO;
import gg.casualchallenge.application.persistence.entity.Card;
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
