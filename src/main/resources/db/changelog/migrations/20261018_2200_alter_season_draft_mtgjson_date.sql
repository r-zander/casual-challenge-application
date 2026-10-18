-- liquibase formatted sql

-- changeset raoul_zander:20261018_2200_alter_season_draft_mtgjson_date.sql
-- Every value so far was written by LocalDate.toString() --> the cast takes all of them
ALTER TABLE public.season_draft ALTER COLUMN mtgjson_date TYPE date USING mtgjson_date::date;
