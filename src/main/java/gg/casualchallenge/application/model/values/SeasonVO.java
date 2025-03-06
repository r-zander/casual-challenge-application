package gg.casualchallenge.application.model.values;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Value;
import org.springframework.lang.NonNull;

import java.time.LocalDate;

@Value
public class SeasonVO {
    int id;
    int seasonNumber;
    LocalDate startDate;
    LocalDate endDate;
}
