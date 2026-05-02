SET search_path TO ai_registration;

INSERT INTO platform_user (
    user_id, open_id, display_name, phone_masked, status, source_channel
) VALUES
    ('user-test-001', 'openid-user-test-001', '测试用户', '138****0001', 'ACTIVE', 'WECHAT_MINIAPP'),
    ('user-new', 'openid-user-new', '新用户', '139****0002', 'ACTIVE', 'WECHAT_MINIAPP')
ON CONFLICT (user_id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    phone_masked = EXCLUDED.phone_masked,
    status = EXCLUDED.status,
    updated_at = now();

INSERT INTO patient_profile (
    patient_id, patient_name, id_type, id_number_masked, phone_masked, active, verified_status, source_channel
) VALUES
    ('patient-test-001', '张三', 'ID_CARD', '440***********1234', '138****0001', true, 'VERIFIED', 'WECHAT_MINIAPP'),
    ('patient-test-002', '李四', 'ID_CARD', '440***********5678', '139****0002', true, 'VERIFIED', 'WECHAT_MINIAPP')
ON CONFLICT (patient_id) DO UPDATE SET
    patient_name = EXCLUDED.patient_name,
    id_number_masked = EXCLUDED.id_number_masked,
    phone_masked = EXCLUDED.phone_masked,
    active = EXCLUDED.active,
    verified_status = EXCLUDED.verified_status,
    updated_at = now();

INSERT INTO user_patient_binding (
    user_id, patient_id, relation_code, is_default, active
) VALUES
    ('user-test-001', 'patient-test-001', 'SELF', true, true),
    ('user-new', 'patient-test-002', 'SELF', true, true)
ON CONFLICT (user_id, patient_id) WHERE active = true DO UPDATE SET
    relation_code = EXCLUDED.relation_code,
    is_default = EXCLUDED.is_default,
    updated_at = now();

INSERT INTO department (
    department_code, department_name, description, active, online_enabled, sort_order
) VALUES
    ('RESP', '呼吸内科', '咳嗽、发热、气促、支气管炎等呼吸系统问题。', true, true, 10),
    ('GI', '消化内科', '腹痛、腹泻、反酸、胃肠不适等消化系统问题。', true, true, 20),
    ('DERM', '皮肤科', '皮疹、瘙痒、痤疮、湿疹等皮肤问题。', true, true, 30),
    ('PED', '儿科', '儿童常见病、发热咳嗽、消化不适等。', true, true, 40),
    ('GYN', '妇科', '妇科常见病、月经异常、孕前咨询等。', true, true, 50),
    ('OPH', '眼科', '视力下降、结膜炎、眼部不适等。', true, true, 60),
    ('NEURO', '神经内科', '头痛、头晕、麻木、睡眠问题等。', true, true, 70),
    ('GEN', '全科门诊', '常见病、慢病复诊、初步诊疗咨询。', true, true, 80),
    ('ER', '急诊科', '急危重症和突发严重症状处理。', true, false, 90)
ON CONFLICT (department_code) DO UPDATE SET
    department_name = EXCLUDED.department_name,
    description = EXCLUDED.description,
    active = EXCLUDED.active,
    online_enabled = EXCLUDED.online_enabled,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();

INSERT INTO doctor (
    doctor_id, department_code, doctor_name, title, specialty, active, online_enabled
) VALUES
    ('doc-101', 'RESP', '王医生', '主任医师', '咳嗽、发热、哮喘和慢性支气管炎。', true, true),
    ('doc-102', 'GI', '陈医生', '副主任医师', '胃痛、反酸、腹泻和消化不良。', true, true),
    ('doc-103', 'DERM', '刘医生', '主治医师', '湿疹、皮炎、痤疮和过敏性皮疹。', true, true),
    ('doc-104', 'PED', '赵医生', '副主任医师', '儿童发热、咳嗽、腹泻和常见感染。', true, true),
    ('doc-105', 'GYN', '孙医生', '主任医师', '妇科炎症、月经异常和孕前咨询。', true, true),
    ('doc-106', 'GEN', '周医生', '主治医师', '常见病初诊、慢病复诊和综合健康咨询。', true, true)
ON CONFLICT (doctor_id) DO UPDATE SET
    department_code = EXCLUDED.department_code,
    doctor_name = EXCLUDED.doctor_name,
    title = EXCLUDED.title,
    specialty = EXCLUDED.specialty,
    active = EXCLUDED.active,
    online_enabled = EXCLUDED.online_enabled,
    updated_at = now();

INSERT INTO clinic_slot (
    department_code, doctor_id, clinic_date, start_time, end_time,
    source_type, status, capacity, remaining_slots, registration_fee, room_no, remarks
) VALUES
    ('RESP', 'doc-101', CURRENT_DATE + 1, TIME '09:00', TIME '09:30', 'LOCAL', 'OPEN', 12, 12, 25.00, 'A201', '上午普通门诊'),
    ('RESP', 'doc-101', CURRENT_DATE + 1, TIME '10:00', TIME '10:30', 'LOCAL', 'OPEN', 12, 10, 25.00, 'A201', '上午普通门诊'),
    ('GI', 'doc-102', CURRENT_DATE + 2, TIME '09:30', TIME '10:00', 'LOCAL', 'OPEN', 10, 10, 25.00, 'B102', '消化内科普通门诊'),
    ('DERM', 'doc-103', CURRENT_DATE + 2, TIME '14:00', TIME '14:30', 'LOCAL', 'OPEN', 15, 15, 20.00, 'C305', '皮肤科下午门诊'),
    ('PED', 'doc-104', CURRENT_DATE + 3, TIME '09:00', TIME '09:30', 'LOCAL', 'OPEN', 15, 15, 20.00, 'D101', '儿科上午门诊'),
    ('GYN', 'doc-105', CURRENT_DATE + 3, TIME '15:00', TIME '15:30', 'LOCAL', 'OPEN', 8, 8, 30.00, 'E203', '妇科下午门诊'),
    ('GEN', 'doc-106', CURRENT_DATE + 1, TIME '16:00', TIME '16:30', 'LOCAL', 'OPEN', 20, 20, 15.00, 'F101', '全科综合门诊')
ON CONFLICT (department_code, doctor_id, clinic_date, start_time) DO UPDATE SET
    end_time = EXCLUDED.end_time,
    source_type = EXCLUDED.source_type,
    status = EXCLUDED.status,
    capacity = EXCLUDED.capacity,
    remaining_slots = EXCLUDED.remaining_slots,
    registration_fee = EXCLUDED.registration_fee,
    room_no = EXCLUDED.room_no,
    remarks = EXCLUDED.remarks,
    updated_at = now();
