SET search_path TO ai_registration;

CREATE TABLE IF NOT EXISTS platform_user (
    user_id VARCHAR(80) PRIMARY KEY,
    open_id VARCHAR(160),
    union_id VARCHAR(160),
    display_name VARCHAR(120),
    phone_masked VARCHAR(40),
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    source_channel VARCHAR(60) NOT NULL DEFAULT 'WECHAT_MINIAPP',
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_platform_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

ALTER TABLE platform_user
    ADD COLUMN IF NOT EXISTS phone_masked VARCHAR(40),
    ADD COLUMN IF NOT EXISTS source_channel VARCHAR(60) NOT NULL DEFAULT 'WECHAT_MINIAPP',
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE UNIQUE INDEX IF NOT EXISTS uniq_platform_user_open_id
    ON platform_user (open_id)
    WHERE open_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_platform_user_union_id
    ON platform_user (union_id)
    WHERE union_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_platform_user_status_created
    ON platform_user (status, created_at DESC);

COMMENT ON TABLE platform_user IS '平台用户主档，承载微信小程序或其他渠道进入系统的登录主体。';
COMMENT ON COLUMN platform_user.user_id IS '平台用户唯一标识，由认证服务或外部身份映射生成。';
COMMENT ON COLUMN platform_user.open_id IS '微信小程序 openid，同一小程序内唯一，可为空。';
COMMENT ON COLUMN platform_user.union_id IS '微信开放平台 unionid，同主体跨应用唯一，可为空。';
COMMENT ON COLUMN platform_user.display_name IS '用户展示名称，不作为实名就诊人信息使用。';
COMMENT ON COLUMN platform_user.phone_masked IS '脱敏手机号，仅用于页面提示和人工排查。';
COMMENT ON COLUMN platform_user.status IS '用户状态：ACTIVE 可用，DISABLED 停用。';
COMMENT ON COLUMN platform_user.source_channel IS '用户来源渠道，例如 WECHAT_MINIAPP、ADMIN_IMPORT。';
COMMENT ON COLUMN platform_user.last_login_at IS '最近一次登录时间。';
COMMENT ON COLUMN platform_user.created_at IS '记录创建时间。';
COMMENT ON COLUMN platform_user.updated_at IS '记录最后更新时间。';

CREATE TABLE IF NOT EXISTS patient_profile (
    patient_id VARCHAR(80) PRIMARY KEY,
    patient_name VARCHAR(120) NOT NULL,
    id_type VARCHAR(40) NOT NULL DEFAULT 'ID_CARD',
    id_number_masked VARCHAR(80) NOT NULL,
    phone_masked VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT true,
    verified_status VARCHAR(40) NOT NULL DEFAULT 'UNVERIFIED',
    source_channel VARCHAR(60) NOT NULL DEFAULT 'WECHAT_MINIAPP',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_patient_verified_status CHECK (verified_status IN ('UNVERIFIED', 'VERIFIED', 'REJECTED'))
);

ALTER TABLE patient_profile
    ADD COLUMN IF NOT EXISTS phone_masked VARCHAR(40),
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS verified_status VARCHAR(40) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN IF NOT EXISTS source_channel VARCHAR(60) NOT NULL DEFAULT 'WECHAT_MINIAPP',
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_patient_profile_active_verified
    ON patient_profile (active, verified_status);

COMMENT ON TABLE patient_profile IS '就诊人主档，保存可被用户绑定和用于挂号的患者基础信息。';
COMMENT ON COLUMN patient_profile.patient_id IS '就诊人唯一标识。';
COMMENT ON COLUMN patient_profile.patient_name IS '就诊人姓名。';
COMMENT ON COLUMN patient_profile.id_type IS '证件类型，例如 ID_CARD、PASSPORT、OTHER。';
COMMENT ON COLUMN patient_profile.id_number_masked IS '脱敏证件号码，避免明文身份证号进入业务查询链路。';
COMMENT ON COLUMN patient_profile.phone_masked IS '脱敏联系电话。';
COMMENT ON COLUMN patient_profile.active IS '就诊人是否可用，false 表示已解绑或停用。';
COMMENT ON COLUMN patient_profile.verified_status IS '实名核验状态：UNVERIFIED 未核验，VERIFIED 已核验，REJECTED 核验拒绝。';
COMMENT ON COLUMN patient_profile.source_channel IS '就诊人信息来源渠道。';
COMMENT ON COLUMN patient_profile.created_at IS '记录创建时间。';
COMMENT ON COLUMN patient_profile.updated_at IS '记录最后更新时间。';

CREATE TABLE IF NOT EXISTS user_patient_binding (
    binding_id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(80) NOT NULL REFERENCES platform_user(user_id),
    patient_id VARCHAR(80) NOT NULL REFERENCES patient_profile(patient_id),
    relation_code VARCHAR(40) NOT NULL DEFAULT 'SELF',
    is_default BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    bound_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE user_patient_binding
    ADD COLUMN IF NOT EXISTS relation_code VARCHAR(40) NOT NULL DEFAULT 'SELF',
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS bound_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE UNIQUE INDEX IF NOT EXISTS uniq_user_patient_active_binding
    ON user_patient_binding (user_id, patient_id)
    WHERE active = true;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_user_patient_default_active
    ON user_patient_binding (user_id)
    WHERE active = true AND is_default = true;

CREATE INDEX IF NOT EXISTS idx_user_patient_binding_user_default
    ON user_patient_binding (user_id, active, is_default DESC, bound_at ASC);

COMMENT ON TABLE user_patient_binding IS '平台用户与就诊人的绑定关系，一个用户可维护多个就诊人。';
COMMENT ON COLUMN user_patient_binding.binding_id IS '绑定关系自增主键。';
COMMENT ON COLUMN user_patient_binding.user_id IS '平台用户唯一标识，关联 platform_user。';
COMMENT ON COLUMN user_patient_binding.patient_id IS '就诊人唯一标识，关联 patient_profile。';
COMMENT ON COLUMN user_patient_binding.relation_code IS '与就诊人的关系，例如 SELF、FAMILY、OTHER。';
COMMENT ON COLUMN user_patient_binding.is_default IS '是否为当前用户默认就诊人。';
COMMENT ON COLUMN user_patient_binding.active IS '绑定关系是否有效。';
COMMENT ON COLUMN user_patient_binding.bound_at IS '用户绑定该就诊人的时间。';
COMMENT ON COLUMN user_patient_binding.created_at IS '记录创建时间。';
COMMENT ON COLUMN user_patient_binding.updated_at IS '记录最后更新时间。';

CREATE TABLE IF NOT EXISTS department (
    department_code VARCHAR(40) PRIMARY KEY,
    department_name VARCHAR(120) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    online_enabled BOOLEAN NOT NULL DEFAULT true,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE department
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS online_enabled BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_department_online_active
    ON department (online_enabled, active, sort_order, department_code);

COMMENT ON TABLE department IS '院内科室字典，供分诊、排班和挂号流程共同使用。';
COMMENT ON COLUMN department.department_code IS '科室编码，作为跨模块稳定业务键。';
COMMENT ON COLUMN department.department_name IS '科室名称。';
COMMENT ON COLUMN department.description IS '科室说明，用于后台维护和运营展示。';
COMMENT ON COLUMN department.active IS '科室是否启用。';
COMMENT ON COLUMN department.online_enabled IS '科室是否开放线上挂号。';
COMMENT ON COLUMN department.sort_order IS '科室展示排序，数字越小越靠前。';
COMMENT ON COLUMN department.created_at IS '记录创建时间。';
COMMENT ON COLUMN department.updated_at IS '记录最后更新时间。';

CREATE TABLE IF NOT EXISTS doctor (
    doctor_id VARCHAR(80) PRIMARY KEY,
    department_code VARCHAR(40) NOT NULL REFERENCES department(department_code),
    doctor_name VARCHAR(120) NOT NULL,
    title VARCHAR(80),
    specialty TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    online_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE doctor
    ADD COLUMN IF NOT EXISTS title VARCHAR(80),
    ADD COLUMN IF NOT EXISTS specialty TEXT,
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS online_enabled BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_doctor_department_active
    ON doctor (department_code, active, online_enabled, doctor_id);

COMMENT ON TABLE doctor IS '医生字典，承载排班医生与科室关系。';
COMMENT ON COLUMN doctor.doctor_id IS '医生唯一标识。';
COMMENT ON COLUMN doctor.department_code IS '所属科室编码，关联 department。';
COMMENT ON COLUMN doctor.doctor_name IS '医生姓名。';
COMMENT ON COLUMN doctor.title IS '医生职称。';
COMMENT ON COLUMN doctor.specialty IS '医生擅长方向。';
COMMENT ON COLUMN doctor.active IS '医生是否启用。';
COMMENT ON COLUMN doctor.online_enabled IS '医生是否开放线上挂号。';
COMMENT ON COLUMN doctor.created_at IS '记录创建时间。';
COMMENT ON COLUMN doctor.updated_at IS '记录最后更新时间。';

CREATE TABLE IF NOT EXISTS clinic_slot (
    slot_id BIGSERIAL PRIMARY KEY,
    department_code VARCHAR(40) NOT NULL REFERENCES department(department_code),
    doctor_id VARCHAR(80) NOT NULL REFERENCES doctor(doctor_id),
    clinic_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    source_type VARCHAR(40) NOT NULL DEFAULT 'LOCAL',
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    capacity INTEGER NOT NULL DEFAULT 0,
    remaining_slots INTEGER NOT NULL DEFAULT 0,
    registration_fee NUMERIC(10, 2) NOT NULL DEFAULT 0,
    room_no VARCHAR(80),
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_clinic_slot_status CHECK (status IN ('OPEN', 'CLOSED', 'SUSPENDED')),
    CONSTRAINT chk_clinic_slot_capacity CHECK (capacity >= 0),
    CONSTRAINT chk_clinic_slot_remaining CHECK (remaining_slots >= 0 AND remaining_slots <= capacity),
    CONSTRAINT chk_clinic_slot_time CHECK (end_time > start_time)
);

ALTER TABLE clinic_slot
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(40) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN IF NOT EXISTS status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN IF NOT EXISTS capacity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS remaining_slots INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS registration_fee NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS room_no VARCHAR(80),
    ADD COLUMN IF NOT EXISTS remarks TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE UNIQUE INDEX IF NOT EXISTS uniq_clinic_slot_business_key
    ON clinic_slot (department_code, doctor_id, clinic_date, start_time);

CREATE INDEX IF NOT EXISTS idx_clinic_slot_department_date
    ON clinic_slot (department_code, clinic_date, start_time);

CREATE INDEX IF NOT EXISTS idx_clinic_slot_doctor_date
    ON clinic_slot (doctor_id, clinic_date, start_time);

CREATE INDEX IF NOT EXISTS idx_clinic_slot_status_date
    ON clinic_slot (status, clinic_date, start_time);

COMMENT ON TABLE clinic_slot IS '门诊号源表，记录科室、医生、日期和时间段的可预约容量。';
COMMENT ON COLUMN clinic_slot.slot_id IS '号源自增主键。';
COMMENT ON COLUMN clinic_slot.department_code IS '科室编码，关联 department。';
COMMENT ON COLUMN clinic_slot.doctor_id IS '医生唯一标识，关联 doctor。';
COMMENT ON COLUMN clinic_slot.clinic_date IS '出诊日期。';
COMMENT ON COLUMN clinic_slot.start_time IS '号源开始时间。';
COMMENT ON COLUMN clinic_slot.end_time IS '号源结束时间。';
COMMENT ON COLUMN clinic_slot.source_type IS '号源来源，例如 LOCAL 本地维护，HIS_SYNC 医院系统同步。';
COMMENT ON COLUMN clinic_slot.status IS '号源状态：OPEN 可预约，CLOSED 关闭，SUSPENDED 暂停。';
COMMENT ON COLUMN clinic_slot.capacity IS '号源总容量。';
COMMENT ON COLUMN clinic_slot.remaining_slots IS '剩余可预约数量，预约时通过原子更新扣减。';
COMMENT ON COLUMN clinic_slot.registration_fee IS '挂号费用。';
COMMENT ON COLUMN clinic_slot.room_no IS '诊室或就诊地点。';
COMMENT ON COLUMN clinic_slot.remarks IS '号源备注。';
COMMENT ON COLUMN clinic_slot.created_at IS '记录创建时间。';
COMMENT ON COLUMN clinic_slot.updated_at IS '记录最后更新时间。';

CREATE TABLE IF NOT EXISTS registration_order (
    registration_id VARCHAR(80) PRIMARY KEY,
    user_id VARCHAR(80) NOT NULL REFERENCES platform_user(user_id),
    patient_id VARCHAR(80) NOT NULL REFERENCES patient_profile(patient_id),
    slot_id BIGINT REFERENCES clinic_slot(slot_id),
    department_code VARCHAR(40) NOT NULL REFERENCES department(department_code),
    doctor_id VARCHAR(80) NOT NULL REFERENCES doctor(doctor_id),
    clinic_date DATE NOT NULL,
    start_time TIME NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'BOOKED',
    confirmation_required BOOLEAN NOT NULL DEFAULT false,
    source_channel VARCHAR(60) NOT NULL DEFAULT 'AI_CHAT',
    chat_id VARCHAR(80),
    external_request_id VARCHAR(160),
    cancel_reason TEXT,
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_registration_order_status CHECK (status IN ('BOOKED', 'CANCELLED', 'RESCHEDULED'))
);

ALTER TABLE registration_order
    ADD COLUMN IF NOT EXISTS slot_id BIGINT,
    ADD COLUMN IF NOT EXISTS confirmation_required BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS source_channel VARCHAR(60) NOT NULL DEFAULT 'AI_CHAT',
    ADD COLUMN IF NOT EXISTS chat_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS external_request_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS cancel_reason TEXT,
    ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE UNIQUE INDEX IF NOT EXISTS uniq_registration_order_external_request
    ON registration_order (external_request_id)
    WHERE external_request_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_registration_order_user_date
    ON registration_order (user_id, clinic_date DESC, start_time DESC);

CREATE INDEX IF NOT EXISTS idx_registration_order_slot
    ON registration_order (slot_id);

CREATE INDEX IF NOT EXISTS idx_registration_order_status_date
    ON registration_order (status, clinic_date DESC);

COMMENT ON TABLE registration_order IS '挂号订单台账，记录 AI 挂号流程最终确认后的预约结果。';
COMMENT ON COLUMN registration_order.registration_id IS '挂号单号，业务主键。';
COMMENT ON COLUMN registration_order.user_id IS '发起挂号的平台用户唯一标识。';
COMMENT ON COLUMN registration_order.patient_id IS '本次挂号使用的就诊人唯一标识。';
COMMENT ON COLUMN registration_order.slot_id IS '本次预约锁定的号源主键，关联 clinic_slot。';
COMMENT ON COLUMN registration_order.department_code IS '挂号科室编码。';
COMMENT ON COLUMN registration_order.doctor_id IS '挂号医生唯一标识。';
COMMENT ON COLUMN registration_order.clinic_date IS '预约就诊日期。';
COMMENT ON COLUMN registration_order.start_time IS '预约开始时间。';
COMMENT ON COLUMN registration_order.status IS '挂号状态：BOOKED 已预约，CANCELLED 已取消，RESCHEDULED 已改约。';
COMMENT ON COLUMN registration_order.confirmation_required IS '是否仍需用户确认，正式落库订单通常为 false。';
COMMENT ON COLUMN registration_order.source_channel IS '订单来源渠道，例如 AI_CHAT、MANUAL_ADMIN。';
COMMENT ON COLUMN registration_order.chat_id IS '关联的对话会话 ID。';
COMMENT ON COLUMN registration_order.external_request_id IS '外部请求幂等键，用于防止重复创建挂号单。';
COMMENT ON COLUMN registration_order.cancel_reason IS '取消原因。';
COMMENT ON COLUMN registration_order.confirmed_at IS '用户确认并创建挂号的时间。';
COMMENT ON COLUMN registration_order.cancelled_at IS '取消挂号时间。';
COMMENT ON COLUMN registration_order.created_at IS '记录创建时间。';
COMMENT ON COLUMN registration_order.updated_at IS '记录最后更新时间。';

CREATE TABLE IF NOT EXISTS registration_audit_log (
    audit_id BIGSERIAL PRIMARY KEY,
    registration_id VARCHAR(80),
    operation_type VARCHAR(40) NOT NULL,
    operator_user_id VARCHAR(80),
    chat_id VARCHAR(80),
    source_service VARCHAR(120) NOT NULL,
    success BOOLEAN NOT NULL DEFAULT true,
    reason TEXT,
    trace_id VARCHAR(80),
    request_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    before_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    after_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE registration_audit_log
    ADD COLUMN IF NOT EXISTS registration_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS operator_user_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS chat_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS source_service VARCHAR(120) NOT NULL DEFAULT 'registration-mcp-server',
    ADD COLUMN IF NOT EXISTS success BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS reason TEXT,
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS request_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS response_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS before_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS after_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_registration_audit_registration
    ON registration_audit_log (registration_id, audit_id DESC);

CREATE INDEX IF NOT EXISTS idx_registration_audit_operator
    ON registration_audit_log (operator_user_id, audit_id DESC);

CREATE INDEX IF NOT EXISTS idx_registration_audit_trace
    ON registration_audit_log (trace_id, chat_id, audit_id DESC);

CREATE INDEX IF NOT EXISTS idx_registration_audit_operation_success
    ON registration_audit_log (operation_type, success, audit_id DESC);

COMMENT ON TABLE registration_audit_log IS '挂号操作审计日志，记录创建、查询、取消、改约等动作的请求响应与前后快照。';
COMMENT ON COLUMN registration_audit_log.audit_id IS '审计日志自增主键。';
COMMENT ON COLUMN registration_audit_log.registration_id IS '关联挂号单号，查询或失败场景可为空。';
COMMENT ON COLUMN registration_audit_log.operation_type IS '操作类型，例如 CREATE、QUERY、CANCEL、RESCHEDULE。';
COMMENT ON COLUMN registration_audit_log.operator_user_id IS '执行操作的平台用户唯一标识。';
COMMENT ON COLUMN registration_audit_log.chat_id IS '关联的对话会话 ID。';
COMMENT ON COLUMN registration_audit_log.source_service IS '写入审计日志的服务名。';
COMMENT ON COLUMN registration_audit_log.success IS '操作是否成功。';
COMMENT ON COLUMN registration_audit_log.reason IS '操作结果原因或失败信息摘要。';
COMMENT ON COLUMN registration_audit_log.trace_id IS '链路追踪 ID，对应请求头 X-Trace-Id。';
COMMENT ON COLUMN registration_audit_log.request_payload IS '操作请求快照，JSON 格式。';
COMMENT ON COLUMN registration_audit_log.response_payload IS '操作响应快照，JSON 格式。';
COMMENT ON COLUMN registration_audit_log.before_snapshot IS '操作前业务对象快照，JSON 格式。';
COMMENT ON COLUMN registration_audit_log.after_snapshot IS '操作后业务对象快照，JSON 格式。';
COMMENT ON COLUMN registration_audit_log.created_at IS '审计日志创建时间。';
