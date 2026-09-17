-- liquibase formatted sql

-- changeset raoul_zander:20260916_2259_update_normalized_names.sql
-- db/scripts/update_normalization.sql for every database: the python tool turned apostrophes into dashes, while the API strips them
WITH fixed AS (
    SELECT id, REGEXP_REPLACE(
        LOWER(
                REGEXP_REPLACE(
                        REGEXP_REPLACE(
                                REGEXP_REPLACE(
                                        TRANSLATE(name, $$ÁÀÂÄÃÅáàâäãåÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÖÕóòôöõÚÙÛÜúùûüÑñÇçʼ’‘´`$$,
                                     $$AAAAAAaaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuNnCc'''''$$),
                    '''', '', 'g'  -- Step 2: strip single quotes
                ),
                '[^a-zA-Z0-9]', '-', 'g'  -- Step 3: non-alphanumeric -> dash
            ),
            '-+', '-', 'g'  -- Step 4: collapse multiple dashes
        )
    ),
    '(^-)|(-$)', '', 'g'  -- Step 5: trim leading/trailing dashes
    ) AS normalized_name
    FROM public.card
    WHERE name LIKE '%''%'
)
UPDATE public.card
SET normalized_name = fixed.normalized_name
FROM fixed
WHERE card.id = fixed.id
    AND card.normalized_name <> fixed.normalized_name
    AND NOT EXISTS (SELECT 1 FROM public.card other WHERE other.normalized_name = fixed.normalized_name); -- a name that is taken already stays as it is, the unique index would fail the whole migration otherwise
