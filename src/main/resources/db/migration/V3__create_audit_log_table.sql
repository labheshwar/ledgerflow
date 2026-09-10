CREATE TABLE audit_log (
    id            BIGSERIAL PRIMARY KEY,
    entity_type   VARCHAR(50)  NOT NULL,
    entity_id     BIGINT       NOT NULL,
    action        VARCHAR(50)  NOT NULL,
    before_state  JSONB,
    after_state   JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);

-- Append-only at the database level, not just by application convention:
-- reject any UPDATE or DELETE outright.
CREATE FUNCTION reject_audit_log_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();

CREATE TRIGGER audit_log_no_delete
    BEFORE DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();
