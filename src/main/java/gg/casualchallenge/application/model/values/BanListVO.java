package gg.casualchallenge.application.model.values;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
public class BanListVO {
    int seasonNumber;
    LocalDate startDate;
    LocalDate endDate;
    boolean seasonEnded;
    List<BanListCardVO> bans;
    List<BanListCardVO> extendedBans;
}
