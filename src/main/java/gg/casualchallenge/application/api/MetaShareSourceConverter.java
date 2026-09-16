package gg.casualchallenge.application.api;

import gg.casualchallenge.application.dataprocessor.model.MetaShareSource;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class MetaShareSourceConverter implements Converter<String, MetaShareSource> { // the parameter is written 'mtggoldfish' everywhere, but Spring only binds the constant itself

    @Override
    public MetaShareSource convert(String metaSource) {
        for (MetaShareSource metaShareSource : MetaShareSource.values()) {
            if (metaShareSource.name().equalsIgnoreCase(metaSource)) return metaShareSource;
        }

        throw new IllegalArgumentException("Unknown meta source '" + metaSource + "'.");
    }
}
