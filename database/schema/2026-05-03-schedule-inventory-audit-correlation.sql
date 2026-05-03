SET search_path TO ai_registration;

ALTER TABLE clinic_slot_inventory_audit_log
    ADD COLUMN IF NOT EXISTS operation_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS operation_source VARCHAR(80);

CREATE INDEX IF NOT EXISTS idx_slot_inventory_audit_operation_id
    ON clinic_slot_inventory_audit_log (operation_id, audit_id DESC)
    WHERE operation_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_slot_inventory_audit_success_operation
    ON clinic_slot_inventory_audit_log (operation_id, operation_type, operation_source)
    WHERE operation_id IS NOT NULL
      AND operation_source IS NOT NULL
      AND success = true;

COMMENT ON COLUMN clinic_slot_inventory_audit_log.operation_id IS
    '业务操作幂等键，例如挂号确认 ID 或订单外部请求 ID。';

COMMENT ON COLUMN clinic_slot_inventory_audit_log.operation_source IS
    '业务操作来源，例如 REGISTRATION_CREATE、REGISTRATION_CANCEL。';
