package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import gg.casualchallenge.application.dataprocessor.model.PriceWindowVO;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
public class SeasonPreparationRequestVO { // everything the caller sent, every field may be null until SeasonPreparationService fills the defaults
    LocalDate startDate;
    LocalDate endDate;
    PriceWindowVO priceWindow;
    MetaShareSource metaSource;
    List<BanDTO> uploadedBans; // only needed when metaSource is FILES
    List<BanDTO> uploadedExtendedBans;
    String preparedBy; // the name in the admin token
}
