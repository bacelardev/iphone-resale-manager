-- Etapa G
-- Prepares business cutover, initial inventory and future owner attribution.
-- V1 and V2 remain immutable.

ALTER TABLE device
    ADD COLUMN registration_origin varchar(24) NOT NULL DEFAULT 'OPERATIONAL',
    ADD CONSTRAINT ck_device_registration_origin
        CHECK (registration_origin IN ('OPERATIONAL', 'INITIAL_IMPORT'));

CREATE INDEX ix_device_initial_import
    ON device (purchased_at DESC)
    WHERE registration_origin = 'INITIAL_IMPORT';

ALTER TABLE maintenance
    ADD COLUMN registration_origin varchar(24) NOT NULL DEFAULT 'OPERATIONAL',
    ADD CONSTRAINT ck_maintenance_registration_origin
        CHECK (registration_origin IN ('OPERATIONAL', 'INITIAL_IMPORT'));

ALTER TABLE financial_transaction
    ADD COLUMN owner_user_id uuid,
    ADD CONSTRAINT fk_financial_owner_user FOREIGN KEY (owner_user_id)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT ck_financial_owner_shape CHECK (
        (type IN ('OWNER_CONTRIBUTION', 'OWNER_WITHDRAWAL') AND owner_user_id IS NOT NULL)
        OR (type NOT IN ('OWNER_CONTRIBUTION', 'OWNER_WITHDRAWAL') AND owner_user_id IS NULL)
    );

CREATE INDEX ix_financial_owner_occurred
    ON financial_transaction (owner_user_id, occurred_at DESC)
    WHERE owner_user_id IS NOT NULL;

CREATE TABLE business_initialization (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    status varchar(20) NOT NULL,
    cutoff_at timestamptz(6) NOT NULL,
    declared_cash_balance numeric(14,2),
    opening_balance_transaction_id uuid,
    completed_at timestamptz(6),
    completed_by uuid,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid NOT NULL,
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by uuid NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_business_initialization_status
        CHECK (status IN ('PREPARING', 'COMPLETED')),
    CONSTRAINT ck_business_initialization_cash
        CHECK (declared_cash_balance IS NULL OR declared_cash_balance >= 0),
    CONSTRAINT ck_business_initialization_lifecycle CHECK (
        (
            status = 'PREPARING'
            AND declared_cash_balance IS NULL
            AND opening_balance_transaction_id IS NULL
            AND completed_at IS NULL
            AND completed_by IS NULL
        )
        OR (
            status = 'COMPLETED'
            AND declared_cash_balance IS NOT NULL
            AND completed_at IS NOT NULL
            AND completed_by IS NOT NULL
        )
    ),
    CONSTRAINT ck_business_initialization_completed_after_created
        CHECK (completed_at IS NULL OR completed_at >= created_at),
    CONSTRAINT ck_business_initialization_version CHECK (version >= 0),
    CONSTRAINT fk_business_initialization_opening_balance FOREIGN KEY (opening_balance_transaction_id)
        REFERENCES financial_transaction (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_business_initialization_completed_by FOREIGN KEY (completed_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_business_initialization_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_business_initialization_updated_by FOREIGN KEY (updated_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

-- A constant-expression unique index is the database-level singleton guarantee.
CREATE UNIQUE INDEX uq_business_initialization_singleton
    ON business_initialization ((true));

CREATE INDEX ix_business_initialization_status
    ON business_initialization (status);

CREATE TABLE owner_capital_opening (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    business_initialization_id uuid NOT NULL,
    owner_user_id uuid NOT NULL,
    historical_contribution_amount numeric(14,2) NOT NULL,
    historical_withdrawal_amount numeric(14,2) NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid NOT NULL,
    CONSTRAINT uq_owner_capital_opening_initialization_owner
        UNIQUE (business_initialization_id, owner_user_id),
    CONSTRAINT ck_owner_capital_opening_contribution
        CHECK (historical_contribution_amount >= 0),
    CONSTRAINT ck_owner_capital_opening_withdrawal
        CHECK (historical_withdrawal_amount >= 0),
    CONSTRAINT ck_owner_capital_opening_non_zero
        CHECK (historical_contribution_amount > 0 OR historical_withdrawal_amount > 0),
    CONSTRAINT fk_owner_capital_opening_initialization FOREIGN KEY (business_initialization_id)
        REFERENCES business_initialization (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_owner_capital_opening_owner FOREIGN KEY (owner_user_id)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_owner_capital_opening_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE INDEX ix_owner_capital_opening_owner
    ON owner_capital_opening (owner_user_id);

ALTER TABLE audit_log DROP CONSTRAINT ck_audit_entity_type;
ALTER TABLE audit_log ADD CONSTRAINT ck_audit_entity_type CHECK (
    entity_type IN (
        'USER',
        'IPHONE_MODEL',
        'DEVICE_COLOR',
        'DEVICE',
        'DEVICE_PHOTO',
        'PART_CATALOG',
        'MAINTENANCE',
        'SALE',
        'FINANCIAL_TRANSACTION',
        'BUSINESS_INITIALIZATION'
    )
);

CREATE TRIGGER trg_business_initialization_update_metadata
    BEFORE UPDATE ON business_initialization
    FOR EACH ROW EXECUTE FUNCTION set_update_metadata();

CREATE OR REPLACE FUNCTION protect_registration_origin()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.registration_origin IS DISTINCT FROM OLD.registration_origin THEN
        RAISE EXCEPTION 'registration origin is immutable' USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_device_registration_origin_immutable
    BEFORE UPDATE OF registration_origin ON device
    FOR EACH ROW EXECUTE FUNCTION protect_registration_origin();

CREATE TRIGGER trg_maintenance_registration_origin_immutable
    BEFORE UPDATE OF registration_origin ON maintenance
    FOR EACH ROW EXECUTE FUNCTION protect_registration_origin();

CREATE OR REPLACE FUNCTION validate_operational_ledger_origin()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_registration_origin varchar(24);
BEGIN
    IF NEW.type = 'DEVICE_PURCHASE' THEN
        SELECT registration_origin INTO v_registration_origin
          FROM device
         WHERE id = NEW.device_id;
        IF v_registration_origin IS DISTINCT FROM 'OPERATIONAL' THEN
            RAISE EXCEPTION 'initial-import device cannot create a purchase ledger entry'
                USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.type = 'MAINTENANCE' THEN
        SELECT registration_origin INTO v_registration_origin
          FROM maintenance
         WHERE id = NEW.maintenance_id;
        IF v_registration_origin IS DISTINCT FROM 'OPERATIONAL' THEN
            RAISE EXCEPTION 'initial-import maintenance cannot create a maintenance ledger entry'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_financial_operational_registration_origin
    BEFORE INSERT ON financial_transaction
    FOR EACH ROW EXECUTE FUNCTION validate_operational_ledger_origin();

-- V1 requires one active purchase entry for every live device. Initial-import
-- devices predate cash tracking, so V3 narrows that invariant by origin.
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

    IF v_device.registration_origin = 'INITIAL_IMPORT' THEN
        IF v_count <> 0 THEN
            RAISE EXCEPTION 'initial-import device % cannot have a purchase ledger entry', p_device_id
                USING ERRCODE = '23514';
        END IF;
    ELSIF v_device.archived_at IS NULL THEN
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
    v_origin varchar(24);
    v_performed_at timestamptz(6);
    v_total numeric(14,2);
    v_count integer;
    v_amount numeric(14,2);
    v_date timestamptz(6);
BEGIN
    SELECT m.status, m.registration_origin, m.performed_at, sum(mi.cost)
      INTO v_status, v_origin, v_performed_at, v_total
      FROM maintenance m
      JOIN maintenance_item mi ON mi.maintenance_id = m.id
     WHERE m.id = p_maintenance_id
     GROUP BY m.status, m.registration_origin, m.performed_at;
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

    IF v_origin = 'INITIAL_IMPORT' THEN
        IF v_count <> 0 THEN
            RAISE EXCEPTION 'initial-import maintenance % cannot have a ledger entry', p_maintenance_id
                USING ERRCODE = '23514';
        END IF;
    ELSIF v_status = 'ACTIVE' THEN
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
