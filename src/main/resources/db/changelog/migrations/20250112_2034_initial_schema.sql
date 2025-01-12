-- liquibase formatted sql

-- changeset raoul_zander:20250112_2034_initial_schema.sql
CREATE TYPE public.legality AS ENUM ('legal', 'not_legal', 'extended', 'banned');

CREATE TABLE IF NOT EXISTS public.season
(
    id serial NOT NULL,
    season_number integer NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX season_number ON public.season (season_number);
CREATE INDEX season_start_date_index ON public.season (START_DATE DESC);

-- CREATE INDEX start_date_index ON public.season start_date

CREATE TABLE IF NOT EXISTS public.card
(
    id serial NOT NULL,
    oracle_id uuid NOT NULL,
    name character varying(1023) NOT NULL,
    normalized_name character varying(1023) NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX card_oracle_id_index ON public.card (oracle_id);
CREATE UNIQUE INDEX card_name_index ON public.card (name ASC);
CREATE UNIQUE INDEX card_normalized_name_index ON public.card (normalized_name ASC);

CREATE TABLE IF NOT EXISTS public.card_season_data
(
    id bigserial NOT NULL,
    season_id integer NOT NULL,
    card_id integer NOT NULL,
    budget_points integer,
    legality legality,
    meta_share_standard numeric(4, 3),
    meta_share_pioneer numeric(4, 3),
    meta_share_modern numeric(4, 3),
    meta_share_legacy numeric(4, 3),
    meta_share_vintage numeric(4, 3),
    meta_share_pauper numeric(4, 3),
    PRIMARY KEY (id)
);

ALTER TABLE IF EXISTS public.card_season_data
    ADD FOREIGN KEY (season_id)
    REFERENCES public.season (id) MATCH SIMPLE
    ON UPDATE NO ACTION
       ON DELETE NO ACTION
    NOT VALID;


ALTER TABLE IF EXISTS public.card_season_data
    ADD FOREIGN KEY (card_id)
    REFERENCES public.card (id) MATCH SIMPLE
    ON UPDATE NO ACTION
       ON DELETE NO ACTION
    NOT VALID;

CREATE UNIQUE INDEX card_season_data_ids_index
    ON public.card_season_data (season_id ASC, card_id);
