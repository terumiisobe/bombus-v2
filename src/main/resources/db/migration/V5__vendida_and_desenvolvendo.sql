-- Restore canonical status name after V4 renamed desenvolvendo → em_desenvolvimento.
UPDATE status_colmeia SET name = 'desenvolvendo' WHERE name = 'em_desenvolvimento';

-- Sold hives: excluded from plain counts; codes stay on the row and are freed for reuse
-- via soft uniqueness (isCodeTaken ignores perdida/vendida), same as perdida.
INSERT INTO status_colmeia (id, name) VALUES
  (6, 'vendida');

SELECT setval(pg_get_serial_sequence('status_colmeia', 'id'), (SELECT MAX(id) FROM status_colmeia));
