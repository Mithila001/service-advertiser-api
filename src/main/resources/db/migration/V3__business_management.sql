ALTER TABLE business ADD COLUMN unique_name VARCHAR(50);

-- Preserve pre-existing businesses using deterministic, valid identifiers.
UPDATE business SET unique_name = 'legacy_business_' || id;
UPDATE business SET city = 'Unknown' WHERE city IS NULL OR btrim(city) = '';

ALTER TABLE business
    ALTER COLUMN unique_name SET NOT NULL,
    ALTER COLUMN city SET NOT NULL,
    ADD CONSTRAINT business_unique_name_key UNIQUE (unique_name),
    ADD CONSTRAINT business_unique_name_format CHECK (unique_name ~ '^[a-z0-9_]{3,50}$'),
    ADD CONSTRAINT business_city_not_blank CHECK (btrim(city) <> '');
