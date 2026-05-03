SET search_path TO ai_registration;

DO $$
DECLARE
    existing_constraint_name TEXT;
BEGIN
    FOR existing_constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'ai_registration.registration_order'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE registration_order DROP CONSTRAINT %I', existing_constraint_name);
    END LOOP;
END $$;

ALTER TABLE registration_order
    ADD CONSTRAINT chk_registration_order_status
    CHECK (status IN ('BOOKED', 'CANCELLED', 'RESCHEDULED', 'EXPIRED'));

COMMENT ON COLUMN registration_order.status IS
    '挂号状态：BOOKED 已预约，CANCELLED 已取消，RESCHEDULED 已改约，EXPIRED 已过号。';
