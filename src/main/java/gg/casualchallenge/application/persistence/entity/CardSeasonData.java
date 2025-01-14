package gg.casualchallenge.application.persistence.entity;

import gg.casualchallenge.application.model.type.Legality;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "card_season_data")
public class CardSeasonData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(name = "budget_points")
    private Integer budgetPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "legality")
    private Legality legality;

    @Column(name = "meta_share_standard")
    private BigDecimal metaShareStandard;

    @Column(name = "meta_share_pioneer")
    private BigDecimal metaSharePioneer;

    @Column(name = "meta_share_modern")
    private BigDecimal metaShareModern;

    @Column(name = "meta_share_legacy")
    private BigDecimal metaShareLegacy;

    @Column(name = "meta_share_vintage")
    private BigDecimal metaShareVintage;

    @Column(name = "meta_share_pauper")
    private BigDecimal metaSharePauper;
}
