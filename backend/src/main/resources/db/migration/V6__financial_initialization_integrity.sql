-- Etapa J
-- Completes financial initialization integrity without modifying V1 through V5.

CREATE OR REPLACE FUNCTION protect_owner_capital_opening()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_status varchar(20);
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'owner capital opening is immutable'
            USING ERRCODE = '55000';
    END IF;

    SELECT status INTO v_status
      FROM business_initialization
     WHERE id = NEW.business_initialization_id
     FOR UPDATE;

    IF v_status IS DISTINCT FROM 'PREPARING' THEN
        RAISE EXCEPTION 'owner capital opening requires a preparing initialization'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_owner_capital_opening_protect
    BEFORE INSERT OR UPDATE OR DELETE ON owner_capital_opening
    FOR EACH ROW EXECUTE FUNCTION protect_owner_capital_opening();

CREATE OR REPLACE FUNCTION validate_stage_j_financial_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_status varchar(20);
    v_cutoff_at timestamptz(6);
BEGIN
    SELECT status, cutoff_at INTO v_status, v_cutoff_at
      FROM business_initialization
     FOR UPDATE;

    IF NEW.type = 'OPENING_BALANCE' THEN
        IF v_status IS DISTINCT FROM 'PREPARING'
           OR NEW.occurred_at IS DISTINCT FROM v_cutoff_at THEN
            RAISE EXCEPTION 'opening balance must match a preparing initialization cutoff'
                USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.type IN ('OWNER_CONTRIBUTION', 'OWNER_WITHDRAWAL', 'MANUAL_ADJUSTMENT') THEN
        IF v_status IS DISTINCT FROM 'COMPLETED'
           OR NEW.occurred_at <= v_cutoff_at THEN
            RAISE EXCEPTION 'manual financial operation requires the operational period'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_financial_stage_j_integrity
    BEFORE INSERT ON financial_transaction
    FOR EACH ROW EXECUTE FUNCTION validate_stage_j_financial_insert();

CREATE OR REPLACE FUNCTION protect_business_initialization_completion()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_opening financial_transaction%ROWTYPE;
BEGIN
    IF OLD.status = 'COMPLETED' THEN
        IF NEW IS DISTINCT FROM OLD THEN
            RAISE EXCEPTION 'completed business initialization is immutable'
                USING ERRCODE = '55000';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.status = 'COMPLETED' THEN
        IF NEW.cutoff_at IS DISTINCT FROM OLD.cutoff_at
           OR NEW.declared_cash_balance IS NULL
           OR NEW.declared_cash_balance < 0
           OR NEW.completed_at IS NULL
           OR NEW.completed_by IS NULL THEN
            RAISE EXCEPTION 'business initialization completion is inconsistent'
                USING ERRCODE = '23514';
        END IF;

        IF NEW.declared_cash_balance = 0
           AND NEW.opening_balance_transaction_id IS NOT NULL THEN
            RAISE EXCEPTION 'zero cash must not create an opening balance transaction'
                USING ERRCODE = '23514';
        END IF;

        IF NEW.declared_cash_balance > 0 THEN
            IF NEW.opening_balance_transaction_id IS NULL THEN
                RAISE EXCEPTION 'positive cash requires an opening balance transaction'
                    USING ERRCODE = '23514';
            END IF;
            SELECT * INTO v_opening
              FROM financial_transaction
             WHERE id = NEW.opening_balance_transaction_id
             FOR UPDATE;
            IF NOT FOUND
               OR v_opening.type <> 'OPENING_BALANCE'
               OR v_opening.direction <> 'INFLOW'
               OR v_opening.amount <> NEW.declared_cash_balance
               OR v_opening.occurred_at <> NEW.cutoff_at
               OR v_opening.owner_user_id IS NOT NULL
               OR v_opening.reversal_of_id IS NOT NULL THEN
                RAISE EXCEPTION 'opening balance transaction does not match declared cash'
                    USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_business_initialization_completion_integrity
    BEFORE UPDATE ON business_initialization
    FOR EACH ROW EXECUTE FUNCTION protect_business_initialization_completion();

CREATE OR REPLACE FUNCTION validate_initial_import_device_after_completion()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_status varchar(20);
    v_cutoff_at timestamptz(6);
BEGIN
    IF NEW.registration_origin <> 'INITIAL_IMPORT' THEN
        RETURN NEW;
    END IF;

    SELECT status, cutoff_at INTO v_status, v_cutoff_at
      FROM business_initialization
     FOR UPDATE;

    IF v_status IS DISTINCT FROM 'PREPARING'
       OR NEW.purchased_at > v_cutoff_at THEN
        RAISE EXCEPTION 'historical device requires a preparing initialization and valid cutoff'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_device_initial_import_completion_guard
    BEFORE INSERT ON device
    FOR EACH ROW EXECUTE FUNCTION validate_initial_import_device_after_completion();
