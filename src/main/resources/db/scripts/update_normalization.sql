UPDATE public.card
SET normalized_name = REGEXP_REPLACE(
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
)
WHERE name LIKE '%''%';
