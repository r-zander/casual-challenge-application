package gg.casualchallenge.application.model.values;

import gg.casualchallenge.application.api.legacy.datamodel.BanDTO;
import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import gg.casualchallenge.application.dataprocessor.model.PriceWindowVO;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

/** Everything the caller sent, every field may be null until SeasonPreparationService fills the defaults. */
@Value
public class SeasonPreparationRequestVO {
    LocalDate startDate;
    LocalDate endDate;
    PriceWindowVO priceWindow;
    MetaShareSource metaSource;
    /** Only needed when metaSource is FILES */
    List<BanDTO> uploadedBans;
    List<BanDTO> uploadedExtendedBans;
    /** The name in the admin token */
    String preparedBy;
}
