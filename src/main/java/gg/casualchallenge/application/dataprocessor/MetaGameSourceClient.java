package gg.casualchallenge.application.dataprocessor;

import gg.casualchallenge.application.dataprocessor.model.Staple;
import gg.casualchallenge.application.model.type.MtgFormat;

import java.util.List;

public interface MetaGameSourceClient {

    List<Staple> fetchTop50Staples(MtgFormat mtgFormat);
    List<Staple> fetchTop150Staples(MtgFormat mtgFormat);

}
