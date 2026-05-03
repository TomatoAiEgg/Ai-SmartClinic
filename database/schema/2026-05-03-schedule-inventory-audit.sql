SET search_path TO ai_registration;

CREATE TABLE IF NOT EXISTS clinic_slot_inventory_audit_log (
    audit_id BIGSERIAL PRIMARY KEY,
    operation_type VARCHAR(40) NOT NULL,
    trace_id VARCHAR(80),
    department_code VARCHAR(40) NOT NULL,
    doctor_id VARCHAR(80) NOT NULL,
    clinic_date DATE NOT NULL,
    start_time TIME NOT NULL,
    operation_id VARCHAR(160),
    operation_source VARCHAR(80),
    success BOOLEAN NOT NULL DEFAULT true,
    reason TEXT,
    remaining_before INTEGER,
    remaining_after INTEGER,
    source_service VARCHAR(120) NOT NULL DEFAULT 'schedule-mcp-server',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_clinic_slot_inventory_audit_operation CHECK (operation_type IN ('RESERVE', 'RELEASE'))
);

CREATE INDEX IF NOT EXISTS idx_slot_inventory_audit_trace
    ON clinic_slot_inventory_audit_log (trace_id, audit_id DESC);

CREATE INDEX IF NOT EXISTS idx_slot_inventory_audit_slot
    ON clinic_slot_inventory_audit_log (department_code, doctor_id, clinic_date, start_time, audit_id DESC);

CREATE INDEX IF NOT EXISTS idx_slot_inventory_audit_operation_success
    ON clinic_slot_inventory_audit_log (operation_type, success, audit_id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uniq_slot_inventory_audit_success_operation
    ON clinic_slot_inventory_audit_log (operation_id, operation_type, operation_source)
    WHERE operation_id IS NOT NULL
      AND operation_source IS NOT NULL
      AND success = true;

COMMENT ON TABLE clinic_slot_inventory_audit_log IS '号源库存变更审计日志，记录 reserve/release 的请求链路、结果和剩余号源变化。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.audit_id IS '号源库存审计自增主键。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.operation_type IS '库存操作类型：RESERVE 预占号源，RELEASE 释放号源。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.trace_id IS '链路追踪 ID，对应请求头 X-Trace-Id。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.department_code IS '科室编码。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.doctor_id IS '医生唯一标识。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.clinic_date IS '号源出诊日期。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.start_time IS '号源开始时间。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.operation_id IS '业务操作幂等键，例如挂号确认 ID 或订单外部请求 ID。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.operation_source IS '业务操作来源，例如 REGISTRATION_CREATE、REGISTRATION_CANCEL。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.success IS '库存操作是否成功。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.reason IS '库存操作结果原因或失败信息摘要。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.remaining_before IS '操作前剩余可预约数量，无法读取时为空。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.remaining_after IS '操作后剩余可预约数量，失败时为空。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.source_service IS '写入审计日志的服务名。';
COMMENT ON COLUMN clinic_slot_inventory_audit_log.created_at IS '审计日志创建时间。';
