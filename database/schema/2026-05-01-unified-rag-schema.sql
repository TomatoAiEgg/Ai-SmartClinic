CREATE SCHEMA IF NOT EXISTS ai_registration;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA ai_registration;
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA ai_registration;

SET search_path TO ai_registration;

CREATE TABLE IF NOT EXISTS knowledge_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    namespace VARCHAR(120) NOT NULL,
    source_id VARCHAR(160) NOT NULL,
    source_name VARCHAR(240) NOT NULL,
    document_type VARCHAR(80) NOT NULL,
    title VARCHAR(500) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    version VARCHAR(80) NOT NULL DEFAULT 'v1',
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (namespace, source_id, version)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_namespace_status
    ON knowledge_document (namespace, status);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES knowledge_document(id) ON DELETE CASCADE,
    namespace VARCHAR(120) NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_type VARCHAR(80) NOT NULL DEFAULT 'TEXT',
    title VARCHAR(500),
    content TEXT NOT NULL,
    token_count INTEGER,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding_model VARCHAR(120) NOT NULL,
    embedding_dimensions INTEGER NOT NULL,
    embedding vector(1536) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_namespace_enabled
    ON knowledge_chunk (namespace, enabled);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_metadata_gin
    ON knowledge_chunk USING gin (metadata);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_department_code
    ON knowledge_chunk ((metadata ->> 'departmentCode'));

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_action_tag
    ON knowledge_chunk ((metadata ->> 'actionTag'));

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_embedding_hnsw
    ON knowledge_chunk USING hnsw (embedding vector_cosine_ops);

COMMENT ON COLUMN knowledge_chunk.metadata IS
    'RAG chunk metadata. triage uses departmentCode/departmentName/emergency; guide uses sourceId/sourceName; registration policy uses policyType/actionTag/validFrom/validTo.';

CREATE TABLE IF NOT EXISTS knowledge_ingest_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    namespace VARCHAR(120) NOT NULL,
    source_id VARCHAR(160) NOT NULL,
    source_name VARCHAR(240),
    document_id UUID REFERENCES knowledge_document(id) ON DELETE SET NULL,
    status VARCHAR(40) NOT NULL,
    embedding_model VARCHAR(120),
    embedding_dimensions INTEGER,
    document_count INTEGER NOT NULL DEFAULT 0,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_knowledge_ingest_job_namespace_status
    ON knowledge_ingest_job (namespace, status, started_at DESC);

CREATE TABLE IF NOT EXISTS knowledge_retrieval_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trace_id VARCHAR(80),
    chat_id VARCHAR(80),
    namespace VARCHAR(120) NOT NULL,
    corpus_name VARCHAR(120) NOT NULL,
    query_text TEXT NOT NULL,
    top_k INTEGER NOT NULL,
    min_score DOUBLE PRECISION NOT NULL,
    status VARCHAR(40) NOT NULL,
    hit_count INTEGER NOT NULL,
    best_hit_id VARCHAR(160),
    best_score DOUBLE PRECISION,
    latency_ms BIGINT NOT NULL,
    error_message TEXT,
    hit_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_knowledge_retrieval_log_trace
    ON knowledge_retrieval_log (trace_id, chat_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_knowledge_retrieval_log_namespace_created
    ON knowledge_retrieval_log (namespace, created_at DESC);
