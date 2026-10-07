-- Restore canonical status name after V4 renamed desenvolvendo → em_desenvolvimento.
UPDATE status_colmeia SET name = 'desenvolvendo' WHERE name = 'em_desenvolvimento';

-- Sold hives: excluded from plain counts and release their code (same as perdida).
INSERT INTO status_colmeia (id, name) VALUES
  (6, 'vendida');

SELECT setval(pg_get_serial_sequence('status_colmeia', 'id'), (SELECT MAX(id) FROM status_colmeia));

-- Free codes held by hives whose current status already releases the code (perdida today;
-- vendida going forward). Soft uniqueness in the app also ignores these statuses.
UPDATE colmeia c
SET code = NULL
WHERE EXISTS (
  SELECT 1
  FROM (
    SELECT DISTINCT ON (h.colmeia_id) h.colmeia_id, h.status_id
    FROM colmeia_status_historico h
    ORDER BY h.colmeia_id, h.recorded_at DESC, h.id DESC
  ) cur
  JOIN status_colmeia s ON s.id = cur.status_id
  WHERE cur.colmeia_id = c.id
    AND s.name IN ('perdida', 'vendida')
);
