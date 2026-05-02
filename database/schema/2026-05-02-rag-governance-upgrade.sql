SET search_path TO ai_registration;

ALTER TABLE knowledge_document
    ALTER COLUMN status SET DEFAULT 'DRAFT';

COMMENT ON COLUMN knowledge_document.status IS
    'Knowledge release state. Supported lifecycle: DRAFT, ACTIVE, ARCHIVED. Legacy DISABLED should be treated as DRAFT.';

ALTER TABLE knowledge_ingest_job
    ALTER COLUMN status SET DEFAULT 'PENDING';

COMMENT ON COLUMN knowledge_ingest_job.status IS
    'Ingest lifecycle state: PENDING, RUNNING, SUCCEEDED, PARTIALLY_FAILED, FAILED.';

CREATE INDEX IF NOT EXISTS idx_knowledge_retrieval_log_status_created
    ON knowledge_retrieval_log (status, created_at DESC);
