-- Etapa I
-- Adds sale/cutover chronology while preserving V1 through V4 unchanged.

CREATE OR REPLACE FUNCTION validate_sale_write()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_device_status varchar(32);
    v_purchased_at timestamptz(6);
    v_cutoff_at timestamptz(6);
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

    SELECT cutoff_at INTO v_cutoff_at FROM business_initialization FOR UPDATE;
    IF v_cutoff_at IS NOT NULL AND NEW.sold_at <= v_cutoff_at THEN
        RAISE EXCEPTION 'sale must occur after the business cutoff' USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'INSERT' AND EXISTS (
        SELECT 1
          FROM maintenance
         WHERE device_id = NEW.device_id
           AND status = 'ACTIVE'
           AND performed_at > NEW.sold_at
    ) THEN
        RAISE EXCEPTION 'sale date cannot precede active maintenance'
            USING ERRCODE = '23514';
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

CREATE OR REPLACE FUNCTION protect_cutoff_with_registered_operations()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.cutoff_at IS NOT DISTINCT FROM OLD.cutoff_at THEN
        RETURN NEW;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM device
         WHERE registration_origin = 'INITIAL_IMPORT'
           AND purchased_at > NEW.cutoff_at
    ) OR EXISTS (
        SELECT 1
          FROM maintenance
         WHERE (registration_origin = 'INITIAL_IMPORT' AND performed_at > NEW.cutoff_at)
            OR (registration_origin = 'OPERATIONAL' AND performed_at <= NEW.cutoff_at)
    ) OR EXISTS (
        SELECT 1
          FROM sale
         WHERE sold_at <= NEW.cutoff_at
    ) THEN
        RAISE EXCEPTION 'business cutoff conflicts with registered inventory, maintenance or sale'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;
