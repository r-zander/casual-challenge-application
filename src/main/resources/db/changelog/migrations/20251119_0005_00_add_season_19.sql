-- liquibase formatted sql

-- changeset raoul_zander:20251119_0005_00_add_season_19.sql
UPDATE public.season
    SET end_date = '2025-11-16'
    WHERE id = 18;
INSERT INTO public.season (id, season_number, start_date, end_date, updated_at)
VALUES
    (19, 19, '2025-11-17', '2025-01-30', now());
