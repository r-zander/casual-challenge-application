-- liquibase formatted sql

-- changeset janik_nissen:20260406_0005_00_add_season_20.sql
UPDATE public.season
    SET end_date = '2026-04-04',
        updated_at = now()
    WHERE id = 19;
INSERT INTO public.season (id, season_number, start_date, end_date, updated_at)
VALUES
    (20, 20, '2026-04-05', '2026-06-07', now());
