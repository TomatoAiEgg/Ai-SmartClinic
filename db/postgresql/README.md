# PostgreSQL Init Scripts

These scripts prepare the current V1 backend domain model for PostgreSQL.

Current code status:
- `patient-mcp-server`, `schedule-mcp-server`, and `registration-mcp-server` still run with in-memory mock data.
- The SQL here is the database baseline for the next step, when those MCP services are switched to PostgreSQL.

Execution order:

```bash
psql -U postgres -d your_database -f db/postgresql/010_schema.sql
psql -U postgres -d your_database -f db/postgresql/020_seed_demo_data.sql
```

Tables included:
- `platform_user`
- `patient_profile`
- `user_patient_binding`
- `department`
- `doctor`
- `clinic_slot`
- `registration_order`
- `registration_audit_log`

Module mapping:
- `patient-mcp-server` -> `platform_user`, `patient_profile`, `user_patient_binding`
- `schedule-mcp-server` -> `department`, `doctor`, `clinic_slot`
- `registration-mcp-server` -> `registration_order`, `registration_audit_log`

Notes:
- The schema name is `ai_registration`.
- `patient_profile.id_number_encrypted` and `patient_profile.phone_encrypted` are reserved for application-layer encrypted data.
- Demo slots use `CURRENT_DATE + INTERVAL` so sample schedules stay near the current date.
