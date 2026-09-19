-- liquibase formatted sql

-- changeset raoul_zander:20260919_1520_add_github_and_removal_to_season_draft.sql
ALTER TABLE public.season_draft
    ADD COLUMN previous_season_end_date date, -- what the previous season's end date was before the commit moved it, a removal puts it back
    ADD COLUMN migration_branch character varying(255),
    ADD COLUMN pull_request_url character varying(1023),
    ADD COLUMN removed_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN removed_by character varying(255);
