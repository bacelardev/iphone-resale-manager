-- Etapa H
-- Protects maintenance registration origin and the persisted business cutover.
-- V1, V2 and V3 remain immutable.

CREATE OR REPLACE FUNCTION validate_maintenance_cutover_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_device_origin varchar(24);
    v_purchased_at timestamptz(6);
    v_initialization_status varchar(20);
    v_cutoff_at timestamptz(6);
BEGIN
    SELECT registration_origin, purchased_at
      INTO v_device_origin, v_purchased_at
      FROM device
     WHERE id = NEW.device_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'maintenance device does not exist' USING ERRCODE = '23503';
    END IF;

    SELECT status, cutoff_at
      INTO v_initialization_status, v_cutoff_at
      FROM business_initialization
     FOR UPDATE;

    IF NEW.registration_origin = 'INITIAL_IMPORT' THEN
        IF v_initialization_status IS DISTINCT FROM 'PREPARING' THEN
            RAISE EXCEPTION 'historical maintenance requires a preparing business initialization'
                USING ERRCODE = '23514';
        END IF;
        IF v_device_origin IS DISTINCT FROM 'INITIAL_IMPORT' THEN
            RAISE EXCEPTION 'historical maintenance requires an initial-import device'
                USING ERRCODE = '23514';
        END IF;
        IF NEW.performed_at < v_purchased_at OR NEW.performed_at > v_cutoff_at THEN
            RAISE EXCEPTION 'historical maintenance date is outside the business cutover window'
                USING ERRCODE = '23514';
        END IF;
    ELSIF v_cutoff_at IS NOT NULL AND NEW.performed_at <= v_cutoff_at THEN
        RAISE EXCEPTION 'operational maintenance must occur after the business cutoff'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_maintenance_cutover_insert
    BEFORE INSERT ON maintenance
    FOR EACH ROW EXECUTE FUNCTION validate_maintenance_cutover_insert();

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
    ) THEN
        RAISE EXCEPTION 'business cutoff conflicts with registered inventory or maintenance'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_business_initialization_cutoff_integrity
    BEFORE UPDATE OF cutoff_at ON business_initialization
    FOR EACH ROW EXECUTE FUNCTION protect_cutoff_with_registered_operations();
