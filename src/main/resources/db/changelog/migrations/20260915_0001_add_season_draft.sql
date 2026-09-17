-- liquibase formatted sql

-- changeset raoul_zander:20260915_0001_add_season_draft.sql
CREATE TABLE IF NOT EXISTS public.season_draft
(
    id serial NOT NULL,
    season_number integer NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    price_window_start date NOT NULL,
    price_window_end date NOT NULL,
    previous_season_id integer NOT NULL,
    previous_season_updated_at TIMESTAMP WITHOUT TIME ZONE,
    mtgjson_date character varying(31),
    meta_source character varying(31),
    prepared_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    committed_at TIMESTAMP WITHOUT TIME ZONE,
    report text NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.season_draft_card
(
    id bigserial NOT NULL,
    season_draft_id integer NOT NULL,
    oracle_id uuid NOT NULL,
    previous_oracle_id uuid,
    name character varying(1023) NOT NULL,
    normalized_name character varying(1023) NOT NULL,
    budget_points integer,
    legality legality,
    meta_share_standard numeric(4, 3),
    meta_share_pioneer numeric(4, 3),
    meta_share_modern numeric(4, 3),
    meta_share_legacy numeric(4, 3),
    meta_share_vintage numeric(4, 3),
    meta_share_pauper numeric(4, 3),
    banned_in mtg_format,
    vintage_restricted boolean NOT NULL,
    is_new_card boolean NOT NULL,
    skip_reason character varying(255),

    PRIMARY KEY (id)
);

ALTER TABLE IF EXISTS public.season_draft_card
    ADD FOREIGN KEY (season_draft_id)
    REFERENCES public.season_draft (id) MATCH SIMPLE
    ON UPDATE NO ACTION
       ON DELETE CASCADE;

CREATE INDEX season_draft_card_season_draft_id_index ON public.season_draft_card (season_draft_id);

-- Every season so far was inserted with an explicit id, so the sequence never moved and would collide on the first insert.
SELECT setval('season_id_seq', (SELECT MAX(id) FROM public.season));
