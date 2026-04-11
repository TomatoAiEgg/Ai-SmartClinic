BEGIN;

CREATE SCHEMA IF NOT EXISTS ai_registration;

SET search_path TO ai_registration, public;

CREATE OR REPLACE FUNCTION ai_registration.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TABLE IF NOT EXISTS platform_user (
    user_id              VARCHAR(64) PRIMARY KEY,
    open_id              VARCHAR(128),
    union_id             VARCHAR(128),
    nickname             VARCHAR(64),
    display_name         VARCHAR(64),
    mobile_phone         VARCHAR(32),
    status               VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    last_login_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_platform_user_open_id
    ON platform_user (open_id)
    WHERE open_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS patient_profile (
    patient_id               VARCHAR(64) PRIMARY KEY,
    patient_name             VARCHAR(64) NOT NULL,
    gender_code              VARCHAR(16),
    birthday                 DATE,
    id_type                  VARCHAR(32) NOT NULL DEFAULT 'ID_CARD',
    id_number_encrypted      TEXT,
    id_number_masked         VARCHAR(64),
    phone_encrypted          TEXT,
    phone_masked             VARCHAR(32),
    emergency_contact_name   VARCHAR(64),
    emergency_contact_phone  VARCHAR(32),
    active                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_patient_binding (
    binding_id           BIGSERIAL PRIMARY KEY,
    user_id              VARCHAR(64) NOT NULL REFERENCES platform_user (user_id),
    patient_id           VARCHAR(64) NOT NULL REFERENCES patient_profile (patient_id),
    relation_code        VARCHAR(32) NOT NULL DEFAULT 'SELF'
        CHECK (relation_code IN ('SELF', 'CHILD', 'PARENT', 'SPOUSE', 'OTHER')),
    is_default           BOOLEAN NOT NULL DEFAULT FALSE,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    bound_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, patient_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_patient_default
    ON user_patient_binding (user_id)
    WHERE is_default = TRUE AND active = TRUE;

CREATE TABLE IF NOT EXISTS department (
    department_code      VARCHAR(32) PRIMARY KEY,
    department_name      VARCHAR(128) NOT NULL,
    category_code        VARCHAR(32) NOT NULL DEFAULT 'OUTPATIENT',
    description          TEXT,
    online_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    triage_priority      INTEGER NOT NULL DEFAULT 100,
    sort_order           INTEGER NOT NULL DEFAULT 100,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS doctor (
    doctor_id            VARCHAR(64) PRIMARY KEY,
    department_code      VARCHAR(32) NOT NULL REFERENCES department (department_code),
    doctor_name          VARCHAR(64) NOT NULL,
    title_name           VARCHAR(64),
    speciality           TEXT,
    intro                TEXT,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doctor_department
    ON doctor (department_code, active);

CREATE TABLE IF NOT EXISTS clinic_slot (
    slot_id              BIGSERIAL PRIMARY KEY,
    department_code      VARCHAR(32) NOT NULL REFERENCES department (department_code),
    doctor_id            VARCHAR(64) NOT NULL REFERENCES doctor (doctor_id),
    clinic_date          DATE NOT NULL,
    start_time           TIME NOT NULL,
    end_time             TIME NOT NULL,
    source_type          VARCHAR(32) NOT NULL DEFAULT 'OUTPATIENT',
    status               VARCHAR(16) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'FULL', 'CLOSED', 'CANCELLED')),
    capacity             INTEGER NOT NULL CHECK (capacity > 0),
    remaining_slots      INTEGER NOT NULL CHECK (remaining_slots >= 0),
    registration_fee     NUMERIC(10, 2) NOT NULL DEFAULT 0,
    room_no              VARCHAR(32),
    remarks              TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (doctor_id, clinic_date, start_time),
    CHECK (remaining_slots <= capacity),
    CHECK (end_time > start_time)
);

CREATE INDEX IF NOT EXISTS idx_clinic_slot_department_date
    ON clinic_slot (department_code, clinic_date, status);

CREATE INDEX IF NOT EXISTS idx_clinic_slot_doctor_date
    ON clinic_slot (doctor_id, clinic_date, status);

CREATE TABLE IF NOT EXISTS registration_order (
    registration_id         VARCHAR(32) PRIMARY KEY,
    user_id                 VARCHAR(64) NOT NULL REFERENCES platform_user (user_id),
    patient_id              VARCHAR(64) NOT NULL REFERENCES patient_profile (patient_id),
    slot_id                 BIGINT REFERENCES clinic_slot (slot_id),
    department_code         VARCHAR(32) NOT NULL REFERENCES department (department_code),
    doctor_id               VARCHAR(64) NOT NULL REFERENCES doctor (doctor_id),
    clinic_date             DATE NOT NULL,
    start_time              TIME NOT NULL,
    status                  VARCHAR(32) NOT NULL
        CHECK (status IN (
            'BOOKED',
            'CANCELLED',
            'RESCHEDULED',
            'REQUIRES_CONFIRMATION',
            'FAILED',
            'PENDING_REVIEW',
            'EXPIRED'
        )),
    confirmation_required   BOOLEAN NOT NULL DEFAULT TRUE,
    source_channel          VARCHAR(32) NOT NULL DEFAULT 'MINIAPP',
    chat_id                 VARCHAR(64),
    external_request_id     VARCHAR(64),
    cancel_reason           VARCHAR(256),
    confirmed_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_registration_user_status
    ON registration_order (user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_registration_patient_created_at
    ON registration_order (patient_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_registration_slot_id
    ON registration_order (slot_id);

CREATE TABLE IF NOT EXISTS registration_audit_log (
    audit_id             BIGSERIAL PRIMARY KEY,
    registration_id      VARCHAR(32) REFERENCES registration_order (registration_id),
    operation_type       VARCHAR(32) NOT NULL
        CHECK (operation_type IN ('CREATE', 'QUERY', 'CANCEL', 'RESCHEDULE', 'CONFIRM', 'ROUTE')),
    operator_user_id     VARCHAR(64),
    chat_id              VARCHAR(64),
    source_service       VARCHAR(64) NOT NULL,
    success              BOOLEAN NOT NULL DEFAULT TRUE,
    reason               TEXT,
    trace_id             VARCHAR(128),
    request_payload      JSONB NOT NULL DEFAULT '{}'::JSONB,
    response_payload     JSONB NOT NULL DEFAULT '{}'::JSONB,
    before_snapshot      JSONB NOT NULL DEFAULT '{}'::JSONB,
    after_snapshot       JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_registration_audit_registration
    ON registration_audit_log (registration_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_registration_audit_chat
    ON registration_audit_log (chat_id, created_at DESC);

DROP TRIGGER IF EXISTS trg_platform_user_updated_at ON platform_user;
CREATE TRIGGER trg_platform_user_updated_at
    BEFORE UPDATE ON platform_user
    FOR EACH ROW
    EXECUTE FUNCTION ai_registration.set_updated_at();

DROP TRIGGER IF EXISTS trg_patient_profile_updated_at ON patient_profile;
CREATE TRIGGER trg_patient_profile_updated_at
    BEFORE UPDATE ON patient_profile
    FOR EACH ROW
    EXECUTE FUNCTION ai_registration.set_updated_at();

DROP TRIGGER IF EXISTS trg_user_patient_binding_updated_at ON user_patient_binding;
CREATE TRIGGER trg_user_patient_binding_updated_at
    BEFORE UPDATE ON user_patient_binding
    FOR EACH ROW
    EXECUTE FUNCTION ai_registration.set_updated_at();

DROP TRIGGER IF EXISTS trg_department_updated_at ON department;
CREATE TRIGGER trg_department_updated_at
    BEFORE UPDATE ON department
    FOR EACH ROW
    EXECUTE FUNCTION ai_registration.set_updated_at();

DROP TRIGGER IF EXISTS trg_doctor_updated_at ON doctor;
CREATE TRIGGER trg_doctor_updated_at
    BEFORE UPDATE ON doctor
    FOR EACH ROW
    EXECUTE FUNCTION ai_registration.set_updated_at();

DROP TRIGGER IF EXISTS trg_clinic_slot_updated_at ON clinic_slot;
CREATE TRIGGER trg_clinic_slot_updated_at
    BEFORE UPDATE ON clinic_slot
    FOR EACH ROW
    EXECUTE FUNCTION ai_registration.set_updated_at();

DROP TRIGGER IF EXISTS trg_registration_order_updated_at ON registration_order;
CREATE TRIGGER trg_registration_order_updated_at
    BEFORE UPDATE ON registration_order
    FOR EACH ROW
    EXECUTE FUNCTION ai_registration.set_updated_at();

COMMENT ON SCHEMA ai_registration IS 'AI 挂号平台 PostgreSQL schema';
COMMENT ON TABLE platform_user IS '平台用户，通常对应小程序登录主体';
COMMENT ON TABLE patient_profile IS '就诊人档案，敏感证件/手机号建议由应用层加密后写入';
COMMENT ON TABLE user_patient_binding IS '用户与就诊人关系表，支持一个用户绑定多个就诊人';
COMMENT ON TABLE department IS '门诊科室字典';
COMMENT ON TABLE doctor IS '医生基础信息';
COMMENT ON TABLE clinic_slot IS '可预约号源表，schedule-mcp-server 主要读取该表';
COMMENT ON TABLE registration_order IS '挂号主表，registration-mcp-server 主要写该表';
COMMENT ON TABLE registration_audit_log IS '挂号操作审计日志';

COMMIT;
