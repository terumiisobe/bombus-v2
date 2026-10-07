-- Enrich status history rows as minimal visit / observation records.
-- Single timeline timestamp remains recorded_at (no separate visited_at).
-- No source column: all traffic is WhatsApp today.

ALTER TABLE colmeia_status_historico
  ADD COLUMN recorded_by_user_id BIGINT NULL,
  ADD COLUMN note VARCHAR(280) NULL;

ALTER TABLE colmeia_status_historico
  ADD CONSTRAINT fk_colmeia_status_historico_recorded_by
    FOREIGN KEY (recorded_by_user_id) REFERENCES usuario(id);
