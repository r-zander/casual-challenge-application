package gg.casualchallenge.application.persistence.converters;

import gg.casualchallenge.application.model.type.Legality;
import gg.casualchallenge.library.persistence.EnumValueConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LegalityConverter extends EnumValueConverter<Legality> {
    public LegalityConverter() {
        super(Legality.class);
    }
}
