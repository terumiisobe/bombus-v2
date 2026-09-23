-- Add desconhecido to the canonical colmeia status vocabulary.
-- Preserves the 1–4 ids from V2; uses id 5 for the new row.

INSERT INTO status_colmeia (id, name) VALUES
  (5, 'desconhecido');

SELECT setval(pg_get_serial_sequence('status_colmeia', 'id'), (SELECT MAX(id) FROM status_colmeia));
