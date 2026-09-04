-- Sprint 1 / Etapa B
-- PostgreSQL logical schema proposal.
-- This is the reviewed DDL reference; the Flyway V1 migration belongs to Etapa C.

BEGIN;

-- Required explicitly for environments where gen_random_uuid() is provided by
-- pgcrypto. PostgreSQL 15+ also exposes gen_random_uuid() as a core function.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SEQUENCE device_internal_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE app_user (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(120) NOT NULL,
    username varchar(50) NOT NULL,
    password_hash varchar(255) NOT NULL,
    role varchar(20) NOT NULL DEFAULT 'SOCIO',
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT ck_app_user_name_not_blank CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT ck_app_user_username_format CHECK (
        username = lower(btrim(username))
        AND username ~ '^[a-z0-9._-]{3,50}$'
    ),
    CONSTRAINT ck_app_user_password_hash_not_blank CHECK (btrim(password_hash) <> ''),
    CONSTRAINT ck_app_user_role CHECK (role IN ('SOCIO')),
    CONSTRAINT ck_app_user_version CHECK (version >= 0),
    CONSTRAINT fk_app_user_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_app_user_updated_by FOREIGN KEY (updated_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE iphone_model (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(60) NOT NULL,
    name varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    display_order integer NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_iphone_model_code UNIQUE (code),
    CONSTRAINT ck_iphone_model_code CHECK (code ~ '^[A-Z0-9_]+$'),
    CONSTRAINT ck_iphone_model_name CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT ck_iphone_model_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_iphone_model_version CHECK (version >= 0),
    CONSTRAINT fk_iphone_model_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_iphone_model_updated_by FOREIGN KEY (updated_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_iphone_model_name_ci ON iphone_model (lower(name));

CREATE TABLE device_color (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(50) NOT NULL,
    name varchar(80) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_device_color_code UNIQUE (code),
    CONSTRAINT ck_device_color_code CHECK (code ~ '^[A-Z0-9_]+$'),
    CONSTRAINT ck_device_color_name CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT ck_device_color_version CHECK (version >= 0),
    CONSTRAINT fk_device_color_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_device_color_updated_by FOREIGN KEY (updated_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_device_color_name_ci ON device_color (lower(name));

CREATE TABLE part_catalog (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(50) NOT NULL,
    name varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_part_catalog_code UNIQUE (code),
    CONSTRAINT ck_part_catalog_code CHECK (code ~ '^[A-Z0-9_]+$'),
    CONSTRAINT ck_part_catalog_name CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT ck_part_catalog_version CHECK (version >= 0),
    CONSTRAINT fk_part_catalog_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_part_catalog_updated_by FOREIGN KEY (updated_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_part_catalog_name_ci ON part_catalog (lower(name));

CREATE TABLE device (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    internal_code varchar(24) NOT NULL DEFAULT (
        'IPH-' || lpad(nextval('device_internal_code_seq')::text, 6, '0')
    ),
    model_id uuid NOT NULL,
    color_id uuid NOT NULL,
    storage_gb integer NOT NULL,
    purchase_price numeric(14,2) NOT NULL,
    purchased_at timestamptz(6) NOT NULL,
    face_id_working boolean NOT NULL,
    original_screen boolean NOT NULL,
    original_battery boolean NOT NULL,
    battery_health_percent integer NOT NULL,
    status varchar(32) NOT NULL,
    archived_at timestamptz(6),
    archived_by uuid,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid NOT NULL,
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by uuid NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_device_internal_code UNIQUE (internal_code),
    CONSTRAINT ck_device_internal_code CHECK (internal_code ~ '^IPH-[0-9]{6,}$'),
    CONSTRAINT ck_device_storage CHECK (
        storage_gb IN (64, 128, 256, 512, 1024, 2048)
    ),
    CONSTRAINT ck_device_purchase_price CHECK (purchase_price > 0),
    CONSTRAINT ck_device_battery_health CHECK (battery_health_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_device_status CHECK (
        status IN ('PENDENTE_MANUTENCAO', 'DISPONIVEL_VENDA', 'VENDIDO')
    ),
    CONSTRAINT ck_device_archive_fields CHECK (
        (archived_at IS NULL AND archived_by IS NULL)
        OR (archived_at IS NOT NULL AND archived_by IS NOT NULL)
    ),
    CONSTRAINT ck_device_archived_after_created CHECK (
        archived_at IS NULL OR archived_at >= created_at
    ),
    CONSTRAINT ck_device_version CHECK (version >= 0),
    CONSTRAINT fk_device_model FOREIGN KEY (model_id)
        REFERENCES iphone_model (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_device_color FOREIGN KEY (color_id)
        REFERENCES device_color (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_device_archived_by FOREIGN KEY (archived_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_device_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_device_updated_by FOREIGN KEY (updated_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

ALTER SEQUENCE device_internal_code_seq OWNED BY device.internal_code;

CREATE INDEX ix_device_active_status_created
    ON device (status, created_at DESC) WHERE archived_at IS NULL;
CREATE INDEX ix_device_active_model
    ON device (model_id) WHERE archived_at IS NULL;
CREATE INDEX ix_device_active_color
    ON device (color_id) WHERE archived_at IS NULL;
CREATE INDEX ix_device_purchased_at ON device (purchased_at DESC);

CREATE TABLE device_photo (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id uuid NOT NULL,
    storage_key varchar(512) NOT NULL,
    original_filename varchar(255) NOT NULL,
    mime_type varchar(100) NOT NULL,
    size_bytes bigint NOT NULL,
    position integer NOT NULL,
    removed_at timestamptz(6),
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid NOT NULL,
    CONSTRAINT uq_device_photo_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_device_photo_storage_key CHECK (btrim(storage_key) <> ''),
    CONSTRAINT ck_device_photo_filename CHECK (btrim(original_filename) <> ''),
    CONSTRAINT ck_device_photo_mime_type CHECK (btrim(mime_type) <> ''),
    CONSTRAINT ck_device_photo_size CHECK (size_bytes > 0),
    CONSTRAINT ck_device_photo_position CHECK (position BETWEEN 1 AND 4),
    CONSTRAINT ck_device_photo_removed_after_created CHECK (
        removed_at IS NULL OR removed_at >= created_at
    ),
    CONSTRAINT fk_device_photo_device FOREIGN KEY (device_id)
        REFERENCES device (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_device_photo_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_device_photo_active_position
    ON device_photo (device_id, position) WHERE removed_at IS NULL;
CREATE INDEX ix_device_photo_device_history
    ON device_photo (device_id, created_at DESC);

CREATE OR REPLACE FUNCTION protect_device_photo()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'device photos use logical removal' USING ERRCODE = '55000';
    END IF;
    IF OLD.removed_at IS NOT NULL
       OR NEW.removed_at IS NULL
       OR NEW.id IS DISTINCT FROM OLD.id
       OR NEW.device_id IS DISTINCT FROM OLD.device_id
       OR NEW.storage_key IS DISTINCT FROM OLD.storage_key
       OR NEW.original_filename IS DISTINCT FROM OLD.original_filename
       OR NEW.mime_type IS DISTINCT FROM OLD.mime_type
       OR NEW.size_bytes IS DISTINCT FROM OLD.size_bytes
       OR NEW.position IS DISTINCT FROM OLD.position
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.created_by IS DISTINCT FROM OLD.created_by THEN
        RAISE EXCEPTION 'device photo metadata is immutable; only logical removal is allowed'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_device_photo_protect
    BEFORE UPDATE OR DELETE ON device_photo
    FOR EACH ROW EXECUTE FUNCTION protect_device_photo();

CREATE TABLE maintenance (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id uuid NOT NULL,
    performed_at timestamptz(6) NOT NULL,
    responsible_user_id uuid NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    cancelled_at timestamptz(6),
    cancelled_by uuid,
    cancellation_reason varchar(500),
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid NOT NULL,
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by uuid NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_maintenance_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_maintenance_cancellation CHECK (
        (
            status = 'ACTIVE'
            AND cancelled_at IS NULL
            AND cancelled_by IS NULL
            AND cancellation_reason IS NULL
        )
        OR (
            status = 'CANCELLED'
            AND cancelled_at IS NOT NULL
            AND cancelled_by IS NOT NULL
            AND cancellation_reason IS NOT NULL
            AND btrim(cancellation_reason) <> ''
        )
    ),
    CONSTRAINT ck_maintenance_cancelled_after_created CHECK (
        cancelled_at IS NULL OR cancelled_at >= created_at
    ),
    CONSTRAINT ck_maintenance_version CHECK (version >= 0),
    CONSTRAINT fk_maintenance_device FOREIGN KEY (device_id)
        REFERENCES device (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_responsible FOREIGN KEY (responsible_user_id)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_cancelled_by FOREIGN KEY (cancelled_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_updated_by FOREIGN KEY (updated_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE INDEX ix_maintenance_device_performed
    ON maintenance (device_id, performed_at DESC);
CREATE INDEX ix_maintenance_active_period
    ON maintenance (performed_at DESC) WHERE status = 'ACTIVE';

CREATE TABLE maintenance_item (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    maintenance_id uuid NOT NULL,
    part_id uuid NOT NULL,
    details varchar(255),
    cost numeric(14,2) NOT NULL,
    position integer NOT NULL,
    CONSTRAINT uq_maintenance_item_position UNIQUE (maintenance_id, position),
    CONSTRAINT ck_maintenance_item_cost CHECK (cost >= 0),
    CONSTRAINT ck_maintenance_item_position CHECK (position > 0),
    CONSTRAINT ck_maintenance_item_details CHECK (
        details IS NULL OR btrim(details) <> ''
    ),
    CONSTRAINT fk_maintenance_item_maintenance FOREIGN KEY (maintenance_id)
        REFERENCES maintenance (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_item_part FOREIGN KEY (part_id)
        REFERENCES part_catalog (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE INDEX ix_maintenance_item_part ON maintenance_item (part_id);

CREATE TABLE sale (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id uuid NOT NULL,
    sale_price numeric(14,2) NOT NULL,
    sold_at timestamptz(6) NOT NULL,
    responsible_user_id uuid NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    cancelled_at timestamptz(6),
    cancelled_by uuid,
    cancellation_reason varchar(500),
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid NOT NULL,
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by uuid NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_sale_price CHECK (sale_price > 0),
    CONSTRAINT ck_sale_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_sale_cancellation CHECK (
        (
            status = 'ACTIVE'
            AND cancelled_at IS NULL
            AND cancelled_by IS NULL
            AND cancellation_reason IS NULL
        )
        OR (
            status = 'CANCELLED'
            AND cancelled_at IS NOT NULL
            AND cancelled_by IS NOT NULL
            AND cancellation_reason IS NOT NULL
            AND btrim(cancellation_reason) <> ''
        )
    ),
    CONSTRAINT ck_sale_cancelled_after_created CHECK (
        cancelled_at IS NULL OR cancelled_at >= created_at
    ),
    CONSTRAINT ck_sale_version CHECK (version >= 0),
    CONSTRAINT fk_sale_device FOREIGN KEY (device_id)
        REFERENCES device (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_sale_responsible FOREIGN KEY (responsible_user_id)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_sale_cancelled_by FOREIGN KEY (cancelled_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_sale_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_sale_updated_by FOREIGN KEY (updated_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_sale_active_device
    ON sale (device_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_sale_device_history ON sale (device_id, sold_at DESC);
CREATE INDEX ix_sale_active_period ON sale (sold_at DESC) WHERE status = 'ACTIVE';

CREATE TABLE financial_transaction (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    direction varchar(10) NOT NULL,
    type varchar(40) NOT NULL,
    amount numeric(14,2) NOT NULL,
    occurred_at timestamptz(6) NOT NULL,
    device_id uuid,
    maintenance_id uuid,
    sale_id uuid,
    reversal_of_id uuid,
    description varchar(500),
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid NOT NULL,
    CONSTRAINT ck_financial_direction CHECK (direction IN ('INFLOW', 'OUTFLOW')),
    CONSTRAINT ck_financial_type CHECK (
        type IN (
            'OPENING_BALANCE',
            'DEVICE_PURCHASE',
            'DEVICE_PURCHASE_REVERSAL',
            'MAINTENANCE',
            'MAINTENANCE_REVERSAL',
            'SALE',
            'SALE_REVERSAL',
            'OWNER_CONTRIBUTION',
            'OWNER_WITHDRAWAL',
            'MANUAL_ADJUSTMENT'
        )
    ),
    CONSTRAINT ck_financial_amount CHECK (amount > 0),
    CONSTRAINT ck_financial_description CHECK (
        description IS NULL OR btrim(description) <> ''
    ),
    CONSTRAINT ck_financial_direction_by_type CHECK (
        (type IN ('OPENING_BALANCE', 'OWNER_CONTRIBUTION', 'SALE', 'DEVICE_PURCHASE_REVERSAL', 'MAINTENANCE_REVERSAL') AND direction = 'INFLOW')
        OR (type IN ('OWNER_WITHDRAWAL', 'DEVICE_PURCHASE', 'MAINTENANCE', 'SALE_REVERSAL') AND direction = 'OUTFLOW')
        OR type = 'MANUAL_ADJUSTMENT'
    ),
    CONSTRAINT ck_financial_source_shape CHECK (
        (
            type IN ('DEVICE_PURCHASE', 'DEVICE_PURCHASE_REVERSAL')
            AND device_id IS NOT NULL
            AND maintenance_id IS NULL
            AND sale_id IS NULL
        )
        OR (
            type IN ('MAINTENANCE', 'MAINTENANCE_REVERSAL')
            AND device_id IS NULL
            AND maintenance_id IS NOT NULL
            AND sale_id IS NULL
        )
        OR (
            type IN ('SALE', 'SALE_REVERSAL')
            AND device_id IS NULL
            AND maintenance_id IS NULL
            AND sale_id IS NOT NULL
        )
        OR (
            type IN ('OPENING_BALANCE', 'OWNER_CONTRIBUTION', 'OWNER_WITHDRAWAL', 'MANUAL_ADJUSTMENT')
            AND device_id IS NULL
            AND maintenance_id IS NULL
            AND sale_id IS NULL
        )
    ),
    CONSTRAINT ck_financial_operational_origin_count CHECK (
        (
            type IN ('OPENING_BALANCE', 'OWNER_CONTRIBUTION', 'OWNER_WITHDRAWAL', 'MANUAL_ADJUSTMENT')
            AND num_nonnulls(device_id, maintenance_id, sale_id) = 0
        )
        OR (
            type NOT IN ('OPENING_BALANCE', 'OWNER_CONTRIBUTION', 'OWNER_WITHDRAWAL', 'MANUAL_ADJUSTMENT')
            AND num_nonnulls(device_id, maintenance_id, sale_id) = 1
        )
    ),
    CONSTRAINT ck_financial_reversal_shape CHECK (
        (type IN ('DEVICE_PURCHASE_REVERSAL', 'MAINTENANCE_REVERSAL', 'SALE_REVERSAL') AND reversal_of_id IS NOT NULL)
        OR (type IN ('OPENING_BALANCE', 'DEVICE_PURCHASE', 'MAINTENANCE', 'SALE', 'OWNER_CONTRIBUTION', 'OWNER_WITHDRAWAL') AND reversal_of_id IS NULL)
        OR type = 'MANUAL_ADJUSTMENT'
    ),
    CONSTRAINT ck_financial_manual_description CHECK (
        type NOT IN ('OPENING_BALANCE', 'OWNER_CONTRIBUTION', 'OWNER_WITHDRAWAL', 'MANUAL_ADJUSTMENT')
        OR (description IS NOT NULL AND btrim(description) <> '')
    ),
    CONSTRAINT fk_financial_device FOREIGN KEY (device_id)
        REFERENCES device (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_financial_maintenance FOREIGN KEY (maintenance_id)
        REFERENCES maintenance (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_financial_sale FOREIGN KEY (sale_id)
        REFERENCES sale (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_financial_reversal FOREIGN KEY (reversal_of_id)
        REFERENCES financial_transaction (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_financial_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_financial_reversal_once
    ON financial_transaction (reversal_of_id) WHERE reversal_of_id IS NOT NULL;
CREATE INDEX ix_financial_occurred ON financial_transaction (occurred_at DESC);
CREATE INDEX ix_financial_direction_occurred
    ON financial_transaction (direction, occurred_at DESC);
CREATE INDEX ix_financial_type_occurred
    ON financial_transaction (type, occurred_at DESC);
CREATE INDEX ix_financial_device
    ON financial_transaction (device_id) WHERE device_id IS NOT NULL;
CREATE INDEX ix_financial_maintenance
    ON financial_transaction (maintenance_id) WHERE maintenance_id IS NOT NULL;
CREATE INDEX ix_financial_sale
    ON financial_transaction (sale_id) WHERE sale_id IS NOT NULL;

CREATE TABLE audit_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_user_id uuid,
    action varchar(50) NOT NULL,
    entity_type varchar(40) NOT NULL,
    entity_id uuid NOT NULL,
    entity_reference varchar(120),
    summary varchar(500) NOT NULL,
    changes jsonb,
    request_id uuid,
    CONSTRAINT ck_audit_action CHECK (
        action IN (
            'CREATED',
            'UPDATED',
            'ARCHIVED',
            'ACTIVATED',
            'DEACTIVATED',
            'STATUS_CHANGED',
            'PHOTO_ADDED',
            'PHOTO_REMOVED',
            'MAINTENANCE_REGISTERED',
            'MAINTENANCE_CANCELLED',
            'SALE_REGISTERED',
            'SALE_CANCELLED',
            'FINANCIAL_TRANSACTION_CREATED'
        )
    ),
    CONSTRAINT ck_audit_entity_type CHECK (
        entity_type IN (
            'USER',
            'IPHONE_MODEL',
            'DEVICE_COLOR',
            'DEVICE',
            'DEVICE_PHOTO',
            'PART_CATALOG',
            'MAINTENANCE',
            'SALE',
            'FINANCIAL_TRANSACTION'
        )
    ),
    CONSTRAINT ck_audit_reference CHECK (
        entity_reference IS NULL OR btrim(entity_reference) <> ''
    ),
    CONSTRAINT ck_audit_summary CHECK (btrim(summary) <> ''),
    CONSTRAINT ck_audit_changes_object CHECK (
        changes IS NULL OR jsonb_typeof(changes) = 'object'
    ),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE INDEX ix_audit_entity_timeline
    ON audit_log (entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_actor_timeline
    ON audit_log (actor_user_id, occurred_at DESC) WHERE actor_user_id IS NOT NULL;
CREATE INDEX ix_audit_global_timeline ON audit_log (occurred_at DESC);
CREATE INDEX ix_audit_request ON audit_log (request_id) WHERE request_id IS NOT NULL;

-- Keeps timestamps and optimistic-lock versions consistent even for controlled SQL updates.
CREATE OR REPLACE FUNCTION set_update_metadata()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    NEW.version := OLD.version + 1;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_app_user_update_metadata
    BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION set_update_metadata();
CREATE TRIGGER trg_iphone_model_update_metadata
    BEFORE UPDATE ON iphone_model
    FOR EACH ROW EXECUTE FUNCTION set_update_metadata();
CREATE TRIGGER trg_device_color_update_metadata
    BEFORE UPDATE ON device_color
    FOR EACH ROW EXECUTE FUNCTION set_update_metadata();
CREATE TRIGGER trg_part_catalog_update_metadata
    BEFORE UPDATE ON part_catalog
    FOR EACH ROW EXECUTE FUNCTION set_update_metadata();
CREATE TRIGGER trg_device_update_metadata
    BEFORE UPDATE ON device
    FOR EACH ROW EXECUTE FUNCTION set_update_metadata();
CREATE TRIGGER trg_maintenance_update_metadata
    BEFORE UPDATE ON maintenance
    FOR EACH ROW EXECUTE FUNCTION set_update_metadata();
CREATE TRIGGER trg_sale_update_metadata
    BEFORE UPDATE ON sale
    FOR EACH ROW EXECUTE FUNCTION set_update_metadata();

-- Prevents an inactive catalog value from being selected for new/changed data.
CREATE OR REPLACE FUNCTION validate_device_catalogs()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR NEW.model_id IS DISTINCT FROM OLD.model_id)
       AND NOT EXISTS (SELECT 1 FROM iphone_model WHERE id = NEW.model_id AND active) THEN
        RAISE EXCEPTION 'device model must be active' USING ERRCODE = '23514';
    END IF;
    IF (TG_OP = 'INSERT' OR NEW.color_id IS DISTINCT FROM OLD.color_id)
       AND NOT EXISTS (SELECT 1 FROM device_color WHERE id = NEW.color_id AND active) THEN
        RAISE EXCEPTION 'device color must be active' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_device_validate_catalogs
    BEFORE INSERT OR UPDATE OF model_id, color_id ON device
    FOR EACH ROW EXECUTE FUNCTION validate_device_catalogs();

CREATE OR REPLACE FUNCTION validate_maintenance_item_part()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_code varchar(50);
    v_active boolean;
    v_device_status varchar(32);
BEGIN
    SELECT code, active INTO v_code, v_active
      FROM part_catalog
     WHERE id = NEW.part_id;

    IF NOT FOUND OR NOT v_active THEN
        RAISE EXCEPTION 'maintenance part must be active' USING ERRCODE = '23514';
    END IF;

    IF v_code = 'OTHER' AND nullif(btrim(NEW.details), '') IS NULL THEN
        RAISE EXCEPTION 'details are required for OTHER maintenance part'
            USING ERRCODE = '23514';
    END IF;

    SELECT d.status INTO v_device_status
      FROM maintenance m
      JOIN device d ON d.id = m.device_id
     WHERE m.id = NEW.maintenance_id
       AND m.status = 'ACTIVE';

    IF NOT FOUND OR v_device_status = 'VENDIDO' THEN
        RAISE EXCEPTION 'maintenance items require an active maintenance on an unsold device'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_maintenance_item_validate
    BEFORE INSERT ON maintenance_item
    FOR EACH ROW EXECUTE FUNCTION validate_maintenance_item_part();

CREATE OR REPLACE FUNCTION protect_maintenance_item()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'confirmed maintenance items are immutable; cancel the maintenance instead'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_maintenance_item_immutable
    BEFORE UPDATE OR DELETE ON maintenance_item
    FOR EACH ROW EXECUTE FUNCTION protect_maintenance_item();

CREATE OR REPLACE FUNCTION validate_maintenance_write()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_device_status varchar(32);
    v_purchased_at timestamptz(6);
BEGIN
    SELECT status, purchased_at INTO v_device_status, v_purchased_at
      FROM device
     WHERE id = NEW.device_id
       AND archived_at IS NULL
     FOR UPDATE;

    IF NOT FOUND OR v_device_status = 'VENDIDO' THEN
        RAISE EXCEPTION 'maintenance requires an active, unsold device'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.performed_at < v_purchased_at THEN
        RAISE EXCEPTION 'maintenance date cannot precede purchase date'
            USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF OLD.status = 'CANCELLED' THEN
            RAISE EXCEPTION 'cancelled maintenance is immutable' USING ERRCODE = '55000';
        END IF;
        IF NEW.device_id IS DISTINCT FROM OLD.device_id
           OR NEW.performed_at IS DISTINCT FROM OLD.performed_at
           OR NEW.responsible_user_id IS DISTINCT FROM OLD.responsible_user_id
           OR NEW.created_at IS DISTINCT FROM OLD.created_at
           OR NEW.created_by IS DISTINCT FROM OLD.created_by THEN
            RAISE EXCEPTION 'confirmed maintenance data is immutable; cancel and recreate it'
                USING ERRCODE = '55000';
        END IF;
        IF NEW.status <> 'CANCELLED' THEN
            RAISE EXCEPTION 'the only allowed maintenance update is cancellation'
                USING ERRCODE = '55000';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_maintenance_validate_write
    BEFORE INSERT OR UPDATE ON maintenance
    FOR EACH ROW EXECUTE FUNCTION validate_maintenance_write();

CREATE OR REPLACE FUNCTION validate_sale_write()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_device_status varchar(32);
    v_purchased_at timestamptz(6);
BEGIN
    SELECT status, purchased_at INTO v_device_status, v_purchased_at
      FROM device
     WHERE id = NEW.device_id
       AND archived_at IS NULL
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'sale requires an active device' USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'INSERT' AND v_device_status <> 'DISPONIVEL_VENDA' THEN
        RAISE EXCEPTION 'only an available device can be sold' USING ERRCODE = '23514';
    END IF;

    IF NEW.sold_at < v_purchased_at THEN
        RAISE EXCEPTION 'sale date cannot precede purchase date' USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF OLD.status = 'CANCELLED' THEN
            RAISE EXCEPTION 'cancelled sale is immutable' USING ERRCODE = '55000';
        END IF;
        IF NEW.device_id IS DISTINCT FROM OLD.device_id
           OR NEW.sale_price IS DISTINCT FROM OLD.sale_price
           OR NEW.sold_at IS DISTINCT FROM OLD.sold_at
           OR NEW.responsible_user_id IS DISTINCT FROM OLD.responsible_user_id
           OR NEW.created_at IS DISTINCT FROM OLD.created_at
           OR NEW.created_by IS DISTINCT FROM OLD.created_by THEN
            RAISE EXCEPTION 'confirmed sale data is immutable; cancel and recreate it'
                USING ERRCODE = '55000';
        END IF;
        IF NEW.status <> 'CANCELLED' THEN
            RAISE EXCEPTION 'the only allowed sale update is cancellation'
                USING ERRCODE = '55000';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_sale_validate_write
    BEFORE INSERT OR UPDATE ON sale
    FOR EACH ROW EXECUTE FUNCTION validate_sale_write();

CREATE OR REPLACE FUNCTION validate_device_status_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status = OLD.status THEN
        RETURN NEW;
    END IF;

    IF NOT (
        (OLD.status = 'PENDENTE_MANUTENCAO' AND NEW.status = 'DISPONIVEL_VENDA')
        OR (OLD.status = 'DISPONIVEL_VENDA' AND NEW.status IN ('PENDENTE_MANUTENCAO', 'VENDIDO'))
        OR (OLD.status = 'VENDIDO' AND NEW.status = 'DISPONIVEL_VENDA')
    ) THEN
        RAISE EXCEPTION 'invalid device status transition: % -> %', OLD.status, NEW.status
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_device_status_transition
    BEFORE UPDATE OF status ON device
    FOR EACH ROW EXECUTE FUNCTION validate_device_status_transition();

CREATE OR REPLACE FUNCTION validate_device_write()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.archived_at IS NOT NULL THEN
        RAISE EXCEPTION 'archived device is immutable in the MVP' USING ERRCODE = '55000';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.internal_code IS DISTINCT FROM OLD.internal_code
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.created_by IS DISTINCT FROM OLD.created_by THEN
        RAISE EXCEPTION 'device identity and creation metadata are immutable'
            USING ERRCODE = '55000';
    END IF;

    IF OLD.status = 'VENDIDO'
       AND (
           NEW.purchase_price IS DISTINCT FROM OLD.purchase_price
           OR NEW.purchased_at IS DISTINCT FROM OLD.purchased_at
       ) THEN
        RAISE EXCEPTION 'purchase value/date cannot change while the device has an active sale'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.archived_at IS NOT NULL THEN
        IF NEW.status = 'VENDIDO'
           OR EXISTS (
               SELECT 1 FROM maintenance
                WHERE device_id = NEW.id AND status = 'ACTIVE'
           )
           OR EXISTS (
               SELECT 1 FROM sale
                WHERE device_id = NEW.id AND status = 'ACTIVE'
           ) THEN
            RAISE EXCEPTION 'device must have no active maintenance or sale before archiving'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_device_validate_write
    BEFORE UPDATE ON device
    FOR EACH ROW EXECUTE FUNCTION validate_device_write();

-- Enforces 2..4 active photos at transaction end, allowing device and photos
-- to be inserted in separate statements inside the same transaction.
CREATE OR REPLACE FUNCTION assert_device_photo_count(p_device_id uuid)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    v_count integer;
BEGIN
    IF p_device_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM device WHERE id = p_device_id AND archived_at IS NULL
    ) THEN
        RETURN;
    END IF;

    SELECT count(*) INTO v_count
      FROM device_photo
     WHERE device_id = p_device_id
       AND removed_at IS NULL;

    IF v_count < 2 OR v_count > 4 THEN
        RAISE EXCEPTION 'active device % must have between 2 and 4 active photos; found %',
            p_device_id, v_count USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION check_device_photo_count()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'device' THEN
        PERFORM assert_device_photo_count(NEW.id);
    ELSE
        IF TG_OP = 'INSERT' THEN
            PERFORM assert_device_photo_count(NEW.device_id);
        ELSIF TG_OP = 'DELETE' THEN
            PERFORM assert_device_photo_count(OLD.device_id);
        ELSE
            PERFORM assert_device_photo_count(NEW.device_id);
            IF OLD.device_id IS DISTINCT FROM NEW.device_id THEN
                PERFORM assert_device_photo_count(OLD.device_id);
            END IF;
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_device_photo_count_from_device
    AFTER INSERT OR UPDATE ON device
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_device_photo_count();
CREATE CONSTRAINT TRIGGER ct_device_photo_count_from_photo
    AFTER INSERT OR UPDATE OR DELETE ON device_photo
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_device_photo_count();

-- Enforces at least one item in every maintenance at transaction end.
CREATE OR REPLACE FUNCTION assert_maintenance_has_item(p_maintenance_id uuid)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_maintenance_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM maintenance WHERE id = p_maintenance_id
    ) THEN
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM maintenance_item WHERE maintenance_id = p_maintenance_id
    ) THEN
        RAISE EXCEPTION 'maintenance % must contain at least one item', p_maintenance_id
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION check_maintenance_item_count()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'maintenance' THEN
        PERFORM assert_maintenance_has_item(NEW.id);
    ELSE
        IF TG_OP = 'INSERT' THEN
            PERFORM assert_maintenance_has_item(NEW.maintenance_id);
        ELSIF TG_OP = 'DELETE' THEN
            PERFORM assert_maintenance_has_item(OLD.maintenance_id);
        ELSE
            PERFORM assert_maintenance_has_item(NEW.maintenance_id);
            IF OLD.maintenance_id IS DISTINCT FROM NEW.maintenance_id THEN
                PERFORM assert_maintenance_has_item(OLD.maintenance_id);
            END IF;
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_maintenance_item_count_from_maintenance
    AFTER INSERT OR UPDATE ON maintenance
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_maintenance_item_count();
CREATE CONSTRAINT TRIGGER ct_maintenance_item_count_from_item
    AFTER INSERT OR UPDATE OR DELETE ON maintenance_item
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_maintenance_item_count();

-- Enforces the final relationship between device status and active sale.
CREATE OR REPLACE FUNCTION assert_device_sale_state(p_device_id uuid)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    v_status varchar(32);
    v_active_sales integer;
BEGIN
    SELECT status INTO v_status FROM device WHERE id = p_device_id;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    SELECT count(*) INTO v_active_sales
      FROM sale
     WHERE device_id = p_device_id
       AND status = 'ACTIVE';

    IF (v_status = 'VENDIDO' AND v_active_sales <> 1)
       OR (v_status <> 'VENDIDO' AND v_active_sales <> 0) THEN
        RAISE EXCEPTION 'device % status % is inconsistent with % active sale(s)',
            p_device_id, v_status, v_active_sales USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION check_device_sale_state()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'device' THEN
        PERFORM assert_device_sale_state(NEW.id);
    ELSE
        IF TG_OP = 'INSERT' THEN
            PERFORM assert_device_sale_state(NEW.device_id);
        ELSIF TG_OP = 'DELETE' THEN
            PERFORM assert_device_sale_state(OLD.device_id);
        ELSE
            PERFORM assert_device_sale_state(NEW.device_id);
            IF OLD.device_id IS DISTINCT FROM NEW.device_id THEN
                PERFORM assert_device_sale_state(OLD.device_id);
            END IF;
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_device_sale_state_from_device
    AFTER INSERT OR UPDATE ON device
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_device_sale_state();
CREATE CONSTRAINT TRIGGER ct_device_sale_state_from_sale
    AFTER INSERT OR UPDATE OR DELETE ON sale
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_device_sale_state();

-- Validates immutable ledger entries, reversals, source amounts and concurrency.
CREATE OR REPLACE FUNCTION validate_financial_transaction_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_original financial_transaction%ROWTYPE;
    v_source_amount numeric(14,2);
    v_source_date timestamptz(6);
    v_source_status varchar(20);
BEGIN
    IF NEW.reversal_of_id IS NOT NULL THEN
        SELECT * INTO v_original
          FROM financial_transaction
         WHERE id = NEW.reversal_of_id
         FOR UPDATE;

        IF NOT FOUND OR v_original.reversal_of_id IS NOT NULL THEN
            RAISE EXCEPTION 'reversal must reference an original transaction'
                USING ERRCODE = '23514';
        END IF;
        IF NEW.direction = v_original.direction OR NEW.amount <> v_original.amount THEN
            RAISE EXCEPTION 'reversal direction/amount does not match original transaction'
                USING ERRCODE = '23514';
        END IF;
        IF NEW.device_id IS DISTINCT FROM v_original.device_id
           OR NEW.maintenance_id IS DISTINCT FROM v_original.maintenance_id
           OR NEW.sale_id IS DISTINCT FROM v_original.sale_id THEN
            RAISE EXCEPTION 'reversal source does not match original transaction'
                USING ERRCODE = '23514';
        END IF;
        IF NOT (
            (v_original.type = 'DEVICE_PURCHASE' AND NEW.type = 'DEVICE_PURCHASE_REVERSAL')
            OR (v_original.type = 'MAINTENANCE' AND NEW.type = 'MAINTENANCE_REVERSAL')
            OR (v_original.type = 'SALE' AND NEW.type = 'SALE_REVERSAL')
            OR (
                v_original.type IN ('OPENING_BALANCE', 'OWNER_CONTRIBUTION', 'OWNER_WITHDRAWAL', 'MANUAL_ADJUSTMENT')
                AND NEW.type = 'MANUAL_ADJUSTMENT'
            )
        ) THEN
            RAISE EXCEPTION 'invalid reversal type % for original type %', NEW.type, v_original.type
                USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.type = 'DEVICE_PURCHASE' THEN
        SELECT purchase_price, purchased_at
          INTO v_source_amount, v_source_date
          FROM device WHERE id = NEW.device_id FOR UPDATE;
        IF NEW.amount <> v_source_amount OR NEW.occurred_at <> v_source_date THEN
            RAISE EXCEPTION 'device purchase entry must match device price/date'
                USING ERRCODE = '23514';
        END IF;
        IF EXISTS (
            SELECT 1 FROM financial_transaction original
             WHERE original.type = 'DEVICE_PURCHASE'
               AND original.device_id = NEW.device_id
               AND NOT EXISTS (
                   SELECT 1 FROM financial_transaction reversal
                    WHERE reversal.reversal_of_id = original.id
               )
        ) THEN
            RAISE EXCEPTION 'device already has an active purchase entry'
                USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.type = 'MAINTENANCE' THEN
        SELECT status, performed_at
          INTO v_source_status, v_source_date
          FROM maintenance
         WHERE id = NEW.maintenance_id
         FOR UPDATE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'maintenance entry requires an existing maintenance'
                USING ERRCODE = '23514';
        END IF;
        SELECT sum(cost) INTO v_source_amount
          FROM maintenance_item
         WHERE maintenance_id = NEW.maintenance_id;
        IF v_source_status <> 'ACTIVE'
           OR NEW.amount <> v_source_amount OR NEW.occurred_at <> v_source_date THEN
            RAISE EXCEPTION 'maintenance entry must match active maintenance total/date'
                USING ERRCODE = '23514';
        END IF;
        IF EXISTS (
            SELECT 1 FROM financial_transaction original
             WHERE original.type = 'MAINTENANCE'
               AND original.maintenance_id = NEW.maintenance_id
               AND NOT EXISTS (
                   SELECT 1 FROM financial_transaction reversal
                    WHERE reversal.reversal_of_id = original.id
               )
        ) THEN
            RAISE EXCEPTION 'maintenance already has an active financial entry'
                USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.type = 'SALE' THEN
        SELECT status, sold_at, sale_price
          INTO v_source_status, v_source_date, v_source_amount
          FROM sale WHERE id = NEW.sale_id FOR UPDATE;
        IF NOT FOUND OR v_source_status <> 'ACTIVE'
           OR NEW.amount <> v_source_amount OR NEW.occurred_at <> v_source_date THEN
            RAISE EXCEPTION 'sale entry must match active sale price/date'
                USING ERRCODE = '23514';
        END IF;
        IF EXISTS (
            SELECT 1 FROM financial_transaction original
             WHERE original.type = 'SALE'
               AND original.sale_id = NEW.sale_id
               AND NOT EXISTS (
                   SELECT 1 FROM financial_transaction reversal
                    WHERE reversal.reversal_of_id = original.id
               )
        ) THEN
            RAISE EXCEPTION 'sale already has an active financial entry'
                USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.type = 'OPENING_BALANCE' THEN
        PERFORM pg_advisory_xact_lock(hashtext('iphone_resale_opening_balance'));
        IF EXISTS (
            SELECT 1 FROM financial_transaction original
             WHERE original.type = 'OPENING_BALANCE'
               AND NOT EXISTS (
                   SELECT 1 FROM financial_transaction reversal
                    WHERE reversal.reversal_of_id = original.id
               )
        ) THEN
            RAISE EXCEPTION 'an active opening balance already exists'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_financial_validate_insert
    BEFORE INSERT ON financial_transaction
    FOR EACH ROW EXECUTE FUNCTION validate_financial_transaction_insert();

CREATE OR REPLACE FUNCTION reject_immutable_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is immutable; append a reversal/correction instead', TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_financial_immutable
    BEFORE UPDATE OR DELETE ON financial_transaction
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
CREATE TRIGGER trg_audit_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();

CREATE OR REPLACE FUNCTION reject_hard_delete()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% does not support hard delete in the MVP', TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_app_user_no_delete
    BEFORE DELETE ON app_user
    FOR EACH ROW EXECUTE FUNCTION reject_hard_delete();
CREATE TRIGGER trg_iphone_model_no_delete
    BEFORE DELETE ON iphone_model
    FOR EACH ROW EXECUTE FUNCTION reject_hard_delete();
CREATE TRIGGER trg_device_color_no_delete
    BEFORE DELETE ON device_color
    FOR EACH ROW EXECUTE FUNCTION reject_hard_delete();
CREATE TRIGGER trg_part_catalog_no_delete
    BEFORE DELETE ON part_catalog
    FOR EACH ROW EXECUTE FUNCTION reject_hard_delete();
CREATE TRIGGER trg_device_no_delete
    BEFORE DELETE ON device
    FOR EACH ROW EXECUTE FUNCTION reject_hard_delete();
CREATE TRIGGER trg_maintenance_no_delete
    BEFORE DELETE ON maintenance
    FOR EACH ROW EXECUTE FUNCTION reject_hard_delete();
CREATE TRIGGER trg_sale_no_delete
    BEFORE DELETE ON sale
    FOR EACH ROW EXECUTE FUNCTION reject_hard_delete();

-- Deferred ledger completeness checks: every live source has exactly one
-- unreversed entry matching its current economic value, and cancelled sources
-- have no unreversed entry.
CREATE OR REPLACE FUNCTION assert_device_purchase_ledger(p_device_id uuid)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    v_device device%ROWTYPE;
    v_count integer;
    v_amount numeric(14,2);
    v_date timestamptz(6);
BEGIN
    SELECT * INTO v_device FROM device WHERE id = p_device_id;
    IF NOT FOUND THEN RETURN; END IF;

    SELECT count(*), max(original.amount), max(original.occurred_at)
      INTO v_count, v_amount, v_date
      FROM financial_transaction original
     WHERE original.type = 'DEVICE_PURCHASE'
       AND original.device_id = p_device_id
       AND NOT EXISTS (
           SELECT 1 FROM financial_transaction reversal
            WHERE reversal.reversal_of_id = original.id
       );

    IF v_device.archived_at IS NULL THEN
        IF v_count <> 1 OR v_amount <> v_device.purchase_price OR v_date <> v_device.purchased_at THEN
            RAISE EXCEPTION 'device % purchase ledger is incomplete or inconsistent', p_device_id
                USING ERRCODE = '23514';
        END IF;
    ELSIF v_count <> 0 THEN
        RAISE EXCEPTION 'archived device % must not have an unreversed purchase entry', p_device_id
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION assert_maintenance_ledger(p_maintenance_id uuid)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    v_status varchar(20);
    v_performed_at timestamptz(6);
    v_total numeric(14,2);
    v_count integer;
    v_amount numeric(14,2);
    v_date timestamptz(6);
BEGIN
    SELECT m.status, m.performed_at, sum(mi.cost)
      INTO v_status, v_performed_at, v_total
      FROM maintenance m
      JOIN maintenance_item mi ON mi.maintenance_id = m.id
     WHERE m.id = p_maintenance_id
     GROUP BY m.status, m.performed_at;
    IF NOT FOUND THEN RETURN; END IF;

    SELECT count(*), max(original.amount), max(original.occurred_at)
      INTO v_count, v_amount, v_date
      FROM financial_transaction original
     WHERE original.type = 'MAINTENANCE'
       AND original.maintenance_id = p_maintenance_id
       AND NOT EXISTS (
           SELECT 1 FROM financial_transaction reversal
            WHERE reversal.reversal_of_id = original.id
       );

    IF v_status = 'ACTIVE' THEN
        IF (v_total = 0 AND v_count <> 0)
           OR (v_total > 0 AND (v_count <> 1 OR v_amount <> v_total OR v_date <> v_performed_at)) THEN
            RAISE EXCEPTION 'maintenance % ledger is incomplete or inconsistent', p_maintenance_id
                USING ERRCODE = '23514';
        END IF;
    ELSIF v_count <> 0 THEN
        RAISE EXCEPTION 'cancelled maintenance % has an unreversed entry', p_maintenance_id
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION assert_sale_ledger(p_sale_id uuid)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    v_sale sale%ROWTYPE;
    v_count integer;
    v_amount numeric(14,2);
    v_date timestamptz(6);
BEGIN
    SELECT * INTO v_sale FROM sale WHERE id = p_sale_id;
    IF NOT FOUND THEN RETURN; END IF;

    SELECT count(*), max(original.amount), max(original.occurred_at)
      INTO v_count, v_amount, v_date
      FROM financial_transaction original
     WHERE original.type = 'SALE'
       AND original.sale_id = p_sale_id
       AND NOT EXISTS (
           SELECT 1 FROM financial_transaction reversal
            WHERE reversal.reversal_of_id = original.id
       );

    IF v_sale.status = 'ACTIVE' THEN
        IF v_count <> 1 OR v_amount <> v_sale.sale_price OR v_date <> v_sale.sold_at THEN
            RAISE EXCEPTION 'sale % ledger is incomplete or inconsistent', p_sale_id
                USING ERRCODE = '23514';
        END IF;
    ELSIF v_count <> 0 THEN
        RAISE EXCEPTION 'cancelled sale % has an unreversed entry', p_sale_id
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION check_source_ledger()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'device' THEN
        PERFORM assert_device_purchase_ledger(NEW.id);
    ELSIF TG_TABLE_NAME = 'maintenance' THEN
        PERFORM assert_maintenance_ledger(NEW.id);
    ELSIF TG_TABLE_NAME = 'maintenance_item' THEN
        IF TG_OP = 'INSERT' THEN
            PERFORM assert_maintenance_ledger(NEW.maintenance_id);
        ELSIF TG_OP = 'DELETE' THEN
            PERFORM assert_maintenance_ledger(OLD.maintenance_id);
        ELSE
            PERFORM assert_maintenance_ledger(NEW.maintenance_id);
            IF OLD.maintenance_id IS DISTINCT FROM NEW.maintenance_id THEN
                PERFORM assert_maintenance_ledger(OLD.maintenance_id);
            END IF;
        END IF;
    ELSIF TG_TABLE_NAME = 'sale' THEN
        PERFORM assert_sale_ledger(NEW.id);
    ELSIF TG_TABLE_NAME = 'financial_transaction' THEN
        IF NEW.device_id IS NOT NULL THEN
            PERFORM assert_device_purchase_ledger(NEW.device_id);
        ELSIF NEW.maintenance_id IS NOT NULL THEN
            PERFORM assert_maintenance_ledger(NEW.maintenance_id);
        ELSIF NEW.sale_id IS NOT NULL THEN
            PERFORM assert_sale_ledger(NEW.sale_id);
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_ledger_from_device
    AFTER INSERT OR UPDATE ON device
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_source_ledger();
CREATE CONSTRAINT TRIGGER ct_ledger_from_maintenance
    AFTER INSERT OR UPDATE ON maintenance
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_source_ledger();
CREATE CONSTRAINT TRIGGER ct_ledger_from_maintenance_item
    AFTER INSERT OR UPDATE OR DELETE ON maintenance_item
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_source_ledger();
CREATE CONSTRAINT TRIGGER ct_ledger_from_sale
    AFTER INSERT OR UPDATE ON sale
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_source_ledger();
CREATE CONSTRAINT TRIGGER ct_ledger_from_financial
    AFTER INSERT ON financial_transaction
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_source_ledger();

COMMIT;
