-- liquibase formatted sql

-- changeset raoul_zander:20261017_2149_add_card_season_price_details.sql
-- CardPriceDetailsVO as JSON, the commit moves it over to card_season_price_details
ALTER TABLE public.season_draft_card
    ADD COLUMN price_details jsonb;

-- Keyed by the season data row, not by oracle id:
-- remaps and removals of a season then take the details along without a statement of their own
CREATE TABLE IF NOT EXISTS public.card_season_price_details
(
    card_season_data_id bigint NOT NULL,
    details jsonb NOT NULL,

    PRIMARY KEY (card_season_data_id)
);

ALTER TABLE IF EXISTS public.card_season_price_details
    ADD FOREIGN KEY (card_season_data_id)
    REFERENCES public.card_season_data (id) MATCH SIMPLE
    ON UPDATE NO ACTION
       ON DELETE CASCADE;
