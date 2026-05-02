SET search_path TO ai_registration;

CREATE TABLE IF NOT EXISTS registration_workflow_execution_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id VARCHAR(80) NOT NULL,
    confirmation_id VARCHAR(80),
    trace_id VARCHAR(80),
    chat_id VARCHAR(80),
    user_id VARCHAR(80),
    workflow_id VARCHAR(120) NOT NULL,
    intent VARCHAR(40) NOT NULL,
    node_id VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_registration_workflow_execution
    ON registration_workflow_execution_log (execution_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_registration_workflow_trace
    ON registration_workflow_execution_log (trace_id, chat_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_registration_workflow_confirmation
    ON registration_workflow_execution_log (confirmation_id, created_at DESC);
