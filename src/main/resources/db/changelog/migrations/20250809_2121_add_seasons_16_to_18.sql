-- liquibase formatted sql

-- changeset raoul_zander:20250809_2121_add_seasons_17_to_18.sql
INSERT INTO public.season (id, season_number, start_date, end_date)
VALUES
    (17, 17, '2025-03-15', '2025-06-06'),
    (18, 18, '2025-06-07', '2025-08-15');
