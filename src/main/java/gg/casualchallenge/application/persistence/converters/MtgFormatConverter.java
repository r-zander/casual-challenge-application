package gg.casualchallenge.application.persistence.converters;

import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.application.model.type.MtgFormat;
import gg.casualchallenge.library.persistence.EnumValueConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MtgFormatConverter extends EnumValueConverter<MtgFormat> {
    public MtgFormatConverter() {
        super(MtgFormat.class);
    }
}
