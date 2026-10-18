package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.model.type.Legality;
import lombok.Value;

import java.util.UUID;

@Value
public class CardExplanationVO {
    String name;
    UUID oracleId;
    int seasonNumber;
    Integer budgetPoints;
    Legality legality;
    CardWithDataVO.ReasonVO reason;
    CardPriceDetailsVO priceDetails;
}
