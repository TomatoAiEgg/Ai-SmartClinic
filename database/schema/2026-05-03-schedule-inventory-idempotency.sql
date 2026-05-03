SET search_path TO ai_registration;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_slot_inventory_audit_success_operation
    ON clinic_slot_inventory_audit_log (operation_id, operation_type, operation_source)
    WHERE operation_id IS NOT NULL
      AND operation_source IS NOT NULL
      AND success = true;
