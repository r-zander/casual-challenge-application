package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import gg.casualchallenge.application.dataprocessor.model.PriceWindowVO;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
public class SeasonPreparationRequestVO {
    LocalDate startDate;
    LocalDate endDate;
    PriceWindowVO priceWindow;
    MetaShareSource metaSource;
    List<BanDTO> bans; // only filled when metaSource is FILES
    List<BanDTO> extendedBans;
}
