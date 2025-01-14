package gg.casualchallenge.application.dataprocessor.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Setter
@Getter
@AllArgsConstructor
@ToString
public class Staple {
    private String cardName;
    private BigDecimal percentageOfDecks;
}
