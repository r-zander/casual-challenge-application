-- liquibase formatted sql

-- changeset raoul_zander:20250906_1149_add_updated_at_to_season.sql
ALTER TABLE public.season ADD COLUMN updated_at TIMESTAMP;

-- Update seasons 0-16 with 2025-01-12
UPDATE public.season SET updated_at = '2025-01-12 20:34:00' WHERE season_number >= 0 AND season_number <= 16;

-- Update season 17 with 2025-08-09
UPDATE public.season SET updated_at = '2025-08-09 23:45:00' WHERE season_number = 17;

-- Update season 18 with 2025-09-05
UPDATE public.season SET updated_at = '2025-09-05 11:49:00' WHERE season_number = 18;
