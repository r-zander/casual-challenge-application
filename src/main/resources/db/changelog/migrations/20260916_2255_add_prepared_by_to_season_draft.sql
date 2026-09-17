-- liquibase formatted sql

-- changeset raoul_zander:20260916_2255_add_prepared_by_to_season_draft.sql
ALTER TABLE public.season_draft ADD COLUMN prepared_by character varying(255), ADD COLUMN committed_by character varying(255);
