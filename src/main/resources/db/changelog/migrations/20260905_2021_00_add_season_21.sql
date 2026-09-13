-- liquibase formatted sql

-- changeset janik_nissen:20260406_0005_00_add_season_20.sql
UPDATE public.season
    SET end_date = '2026-09-12',
        updated_at = now()
    WHERE id = 20;
INSERT INTO public.season (id, season_number, start_date, end_date, updated_at)
VALUES
    (21, 21, '2026-09-13', '2026-11-21', now());
