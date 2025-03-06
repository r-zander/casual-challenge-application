package gg.casualchallenge.application.model.values;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;

@RequiredArgsConstructor
@Setter
@Getter
public class CardDataBatchVO {
    final List<CardWithDataVO> found;
    final List<String> missing;
}
