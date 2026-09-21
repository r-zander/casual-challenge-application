-- liquibase formatted sql

-- changeset raoul_zander:20260921_1044_00_add_season_21.sql
UPDATE public.season
    SET end_date = '2026-09-20',
        updated_at = now()
    WHERE id = 20;
INSERT INTO public.season (id, season_number, start_date, end_date, updated_at)
VALUES
    (21, 21, '2026-09-21', '2026-11-21', now())
ON CONFLICT (id) DO NOTHING;
SELECT setval('season_id_seq', (SELECT MAX(id) FROM public.season));

-- card_season_data references card.oracle_id without ON UPDATE CASCADE --> both updates have to be one statement
WITH remapped_card AS (UPDATE public.card SET oracle_id = '11db8545-eca6-43f5-b9e8-f302acef53a5'::uuid WHERE oracle_id = '86b47725-1764-4716-993d-e4dfcea2346c'::uuid) UPDATE public.card_season_data SET card_oracle_id = '11db8545-eca6-43f5-b9e8-f302acef53a5'::uuid WHERE card_oracle_id = '86b47725-1764-4716-993d-e4dfcea2346c'::uuid; -- Joven and Chandler
WITH remapped_card AS (UPDATE public.card SET oracle_id = 'f0ee45b5-697b-48f8-b1a6-cb3ae75e4a29'::uuid WHERE oracle_id = '0e99efaf-6402-44c5-ae8b-1f5bb1b68333'::uuid) UPDATE public.card_season_data SET card_oracle_id = 'f0ee45b5-697b-48f8-b1a6-cb3ae75e4a29'::uuid WHERE card_oracle_id = '0e99efaf-6402-44c5-ae8b-1f5bb1b68333'::uuid; -- Red Herring
WITH remapped_card AS (UPDATE public.card SET oracle_id = '9af4a832-d634-47aa-91ed-79d44fe08864'::uuid WHERE oracle_id = '191634b4-42e5-499f-a1a8-1d0407626be8'::uuid) UPDATE public.card_season_data SET card_oracle_id = '9af4a832-d634-47aa-91ed-79d44fe08864'::uuid WHERE card_oracle_id = '191634b4-42e5-499f-a1a8-1d0407626be8'::uuid; -- Pick Your Poison

UPDATE public.card SET name = 'Antiquities on the Loose', normalized_name = 'antiquities-on-the-loose' WHERE oracle_id = '9c45682f-48b5-4e58-9277-ff4211f389ae'::uuid; -- was '"2 Seconds After Opening the Tuna Can"' / '2-seconds-after-opening-the-tuna-can'
UPDATE public.card SET name = 'Restoration Seminar', normalized_name = 'restoration-seminar' WHERE oracle_id = 'fd0a27a9-7d7e-4a16-9dd0-0715e6c97b36'::uuid; -- was '"DIY Golem"' / 'diy-golem'
UPDATE public.card SET name = 'With Great Power . . .', normalized_name = 'with-great-power' WHERE oracle_id = 'dfedb968-f27c-4117-aff6-da707dd43e82'::uuid; -- was 'With Great Power...' / 'with-great-power'
UPDATE public.card SET name = 'Rashel, Fist of Torm', normalized_name = 'rashel-fist-of-torm' WHERE oracle_id = 'c0c39f80-cf12-482e-a7c4-757db85125b0'::uuid; -- was 'Xenk, Paladin Unbroken' / 'xenk-paladin-unbroken'
UPDATE public.card SET name = 'Mathise, Surge Channeler', normalized_name = 'mathise-surge-channeler' WHERE oracle_id = '61be7df8-cc78-4822-9e70-f226a8a96c9d'::uuid; -- was 'Simon, Wild Magic Sorcerer' / 'simon-wild-magic-sorcerer'
UPDATE public.card SET name = 'Evin, Waterdeep Opportunist', normalized_name = 'evin-waterdeep-opportunist' WHERE oracle_id = 'f688f839-c7c2-45a9-8c60-9fdb030803e6'::uuid; -- was 'Forge, Neverwinter Charlatan' / 'forge-neverwinter-charlatan'
UPDATE public.card SET name = 'Jurin, Leading the Charge', normalized_name = 'jurin-leading-the-charge' WHERE oracle_id = 'de384867-8bb4-4edf-b542-970538718fdd'::uuid; -- was 'Holga, Relentless Rager' / 'holga-relentless-rager'
UPDATE public.card SET name = 'Casal, Lurkwood Pathfinder // Casal, Pathbreaker Owlbear', normalized_name = 'casal-lurkwood-pathfinder-casal-pathbreaker-owlbear' WHERE oracle_id = 'b7bdb688-f8ee-4d22-a679-37a2ecd390a1'::uuid; -- was 'Doric, Nature''s Warden // Doric, Owlbear Avenger' / 'doric-natures-warden-doric-owlbear-avenger'
UPDATE public.card SET name = 'Bohn, Beguiling Balladeer', normalized_name = 'bohn-beguiling-balladeer' WHERE oracle_id = 'ab31b652-ddf2-480f-a955-e8b5c87728f9'::uuid; -- was 'Edgin, Larcenous Lutenist' / 'edgin-larcenous-lutenist'
