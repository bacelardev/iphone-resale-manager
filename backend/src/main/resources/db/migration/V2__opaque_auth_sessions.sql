-- Etapa E — opaque access-token sessions.
-- Raw bearer tokens are never persisted; only their SHA-256 hash is stored.

CREATE TABLE auth_session (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    token_hash varchar(64) NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz(6) NOT NULL,
    revoked_at timestamptz(6),
    CONSTRAINT uq_auth_session_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_auth_session_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_auth_session_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_auth_session_revocation CHECK (
        revoked_at IS NULL OR revoked_at >= created_at
    ),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE INDEX ix_auth_session_user_active
    ON auth_session (user_id, expires_at DESC)
    WHERE revoked_at IS NULL;

CREATE INDEX ix_auth_session_expiry_active
    ON auth_session (expires_at)
    WHERE revoked_at IS NULL;

CREATE OR REPLACE FUNCTION protect_auth_session()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'auth sessions are revoked, never deleted'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.user_id IS DISTINCT FROM OLD.user_id
       OR NEW.token_hash IS DISTINCT FROM OLD.token_hash
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.expires_at IS DISTINCT FROM OLD.expires_at
       OR OLD.revoked_at IS NOT NULL
       OR NEW.revoked_at IS NULL THEN
        RAISE EXCEPTION 'auth session is immutable; only first revocation is allowed'
            USING ERRCODE = '55000';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_auth_session_protect
    BEFORE UPDATE OR DELETE ON auth_session
    FOR EACH ROW EXECUTE FUNCTION protect_auth_session();
