-- Access to a meliponário and its colmeias is a row in this table, nothing else.
-- meliponario.owner_id stays as the primary owner and grants no access by itself.
CREATE TABLE meliponario_membro (
  usuario_id     BIGINT      NOT NULL REFERENCES usuario(id),
  meliponario_id BIGINT      NOT NULL REFERENCES meliponario(id) ON DELETE CASCADE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (usuario_id, meliponario_id)
);

INSERT INTO meliponario_membro (usuario_id, meliponario_id)
SELECT owner_id, id FROM meliponario
ON CONFLICT DO NOTHING;
