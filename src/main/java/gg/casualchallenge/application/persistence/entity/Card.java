package gg.casualchallenge.application.persistence.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "card")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "oracle_id", nullable = false)
    private UUID oracleId;

    @Column(name = "name", nullable = false, length = 1023)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 1023)
    private String normalizedName;
}
