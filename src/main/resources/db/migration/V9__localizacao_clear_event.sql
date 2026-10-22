-- Clear events on append-only localizacao: both coords NULL means "no current pin"
-- (prior pin rows kept for history). Live pin = latest row has both coords set.
-- Soft uniqueness (isCodeTaken) holds a disposed hive's code only while a live pin exists.

ALTER TABLE localizacao
  ALTER COLUMN latitude DROP NOT NULL,
  ALTER COLUMN longitude DROP NOT NULL;

ALTER TABLE localizacao
  ADD CONSTRAINT localizacao_coords_both_or_neither CHECK (
    (latitude IS NULL AND longitude IS NULL)
    OR (latitude IS NOT NULL AND longitude IS NOT NULL)
  );

COMMENT ON TABLE localizacao IS
  'Append-only hive locations. Current = latest by recorded_at, id. '
  'Both latitude/longitude NULL = clear event (no live pin). '
  'Both set = live pin. Never mix one null.';
