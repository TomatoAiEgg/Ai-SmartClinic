BEGIN;

SET search_path TO ai_registration, public;

INSERT INTO platform_user (user_id, nickname, display_name, mobile_phone)
VALUES
    ('user-001', 'alex', 'Alex Chen', '13800000001'),
    ('user-002', 'jamie', 'Jamie Lin', '13800000002')
ON CONFLICT (user_id) DO UPDATE
SET nickname = EXCLUDED.nickname,
    display_name = EXCLUDED.display_name,
    mobile_phone = EXCLUDED.mobile_phone;

INSERT INTO patient_profile (
    patient_id,
    patient_name,
    gender_code,
    birthday,
    id_type,
    id_number_masked,
    phone_masked
)
VALUES
    ('patient-001', 'Alex Chen', 'M', DATE '1992-03-12', 'ID_CARD', '110***********1234', '138****0001'),
    ('patient-002', 'Jamie Lin', 'F', DATE '1995-07-21', 'ID_CARD', '310***********5678', '138****0002')
ON CONFLICT (patient_id) DO UPDATE
SET patient_name = EXCLUDED.patient_name,
    gender_code = EXCLUDED.gender_code,
    birthday = EXCLUDED.birthday,
    id_type = EXCLUDED.id_type,
    id_number_masked = EXCLUDED.id_number_masked,
    phone_masked = EXCLUDED.phone_masked;

INSERT INTO user_patient_binding (user_id, patient_id, relation_code, is_default, active)
VALUES
    ('user-001', 'patient-001', 'SELF', TRUE, TRUE),
    ('user-002', 'patient-002', 'SELF', TRUE, TRUE)
ON CONFLICT (user_id, patient_id) DO UPDATE
SET relation_code = EXCLUDED.relation_code,
    is_default = EXCLUDED.is_default,
    active = EXCLUDED.active;

UPDATE user_patient_binding
SET is_default = FALSE
WHERE user_id = 'user-001'
  AND patient_id <> 'patient-001';

UPDATE user_patient_binding
SET is_default = FALSE
WHERE user_id = 'user-002'
  AND patient_id <> 'patient-002';

INSERT INTO department (department_code, department_name, description, triage_priority, sort_order)
VALUES
    ('RESP', 'Respiratory Medicine', '呼吸系统相关门诊', 10, 10),
    ('GEN',  'General Medicine',     '全科门诊',         20, 20),
    ('DERM', 'Dermatology',          '皮肤相关门诊',     30, 30),
    ('GI',   'Gastroenterology',     '消化系统门诊',     40, 40),
    ('PED',  'Pediatrics',           '儿童门诊',         50, 50),
    ('GYN',  'Gynecology',           '妇科门诊',         60, 60)
ON CONFLICT (department_code) DO UPDATE
SET department_name = EXCLUDED.department_name,
    description = EXCLUDED.description,
    triage_priority = EXCLUDED.triage_priority,
    sort_order = EXCLUDED.sort_order;

INSERT INTO doctor (doctor_id, department_code, doctor_name, title_name, speciality, intro)
VALUES
    ('doc-101', 'RESP', 'Dr. Rivera', 'Chief Physician', 'Respiratory infections', 'Focus on fever and cough cases'),
    ('doc-106', 'RESP', 'Dr. Murphy', 'Associate Chief Physician', 'Chronic respiratory disease', 'Focus on asthma and chronic cough'),
    ('doc-102', 'GEN',  'Dr. Park',   'Attending Physician', 'General outpatient service', 'General initial diagnosis'),
    ('doc-103', 'DERM', 'Dr. Patel',  'Attending Physician', 'Skin allergy and rash', 'Focus on common dermatology'),
    ('doc-104', 'GI',   'Dr. Khan',   'Associate Chief Physician', 'Digestive disorders', 'Focus on stomach and bowel discomfort'),
    ('doc-105', 'PED',  'Dr. Gomez',  'Attending Physician', 'Child fever and cough', 'Focus on common pediatrics'),
    ('doc-107', 'GYN',  'Dr. Lopez',  'Attending Physician', 'Routine gynecology', 'Focus on routine outpatient cases')
ON CONFLICT (doctor_id) DO UPDATE
SET department_code = EXCLUDED.department_code,
    doctor_name = EXCLUDED.doctor_name,
    title_name = EXCLUDED.title_name,
    speciality = EXCLUDED.speciality,
    intro = EXCLUDED.intro;

WITH seed_slot AS (
    SELECT *
    FROM (
        VALUES
            ('RESP', 'doc-101', CURRENT_DATE + INTERVAL '1 day', TIME '09:00', TIME '09:30', 6, 6, 18.00, 'A101'),
            ('RESP', 'doc-106', CURRENT_DATE + INTERVAL '1 day', TIME '14:30', TIME '15:00', 4, 4, 22.00, 'A102'),
            ('GEN',  'doc-102', CURRENT_DATE + INTERVAL '1 day', TIME '10:30', TIME '11:00', 8, 8, 12.00, 'B201'),
            ('DERM', 'doc-103', CURRENT_DATE + INTERVAL '1 day', TIME '14:00', TIME '14:30', 4, 4, 20.00, 'C301'),
            ('GI',   'doc-104', CURRENT_DATE + INTERVAL '2 day', TIME '09:30', TIME '10:00', 3, 3, 20.00, 'D102'),
            ('PED',  'doc-105', CURRENT_DATE + INTERVAL '2 day', TIME '15:00', TIME '15:30', 5, 5, 16.00, 'E202'),
            ('GYN',  'doc-107', CURRENT_DATE + INTERVAL '2 day', TIME '13:30', TIME '14:00', 4, 4, 18.00, 'F105')
    ) AS t(department_code, doctor_id, clinic_date, start_time, end_time, capacity, remaining_slots, registration_fee, room_no)
)
INSERT INTO clinic_slot (
    department_code,
    doctor_id,
    clinic_date,
    start_time,
    end_time,
    source_type,
    status,
    capacity,
    remaining_slots,
    registration_fee,
    room_no
)
SELECT
    department_code,
    doctor_id,
    clinic_date::DATE,
    start_time,
    end_time,
    'OUTPATIENT',
    'OPEN',
    capacity,
    remaining_slots,
    registration_fee,
    room_no
FROM seed_slot
ON CONFLICT (doctor_id, clinic_date, start_time) DO UPDATE
SET department_code = EXCLUDED.department_code,
    end_time = EXCLUDED.end_time,
    source_type = EXCLUDED.source_type,
    status = EXCLUDED.status,
    capacity = EXCLUDED.capacity,
    remaining_slots = EXCLUDED.remaining_slots,
    registration_fee = EXCLUDED.registration_fee,
    room_no = EXCLUDED.room_no;

COMMIT;
