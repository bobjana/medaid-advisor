-- V3__Vector_store_improvements.sql
-- Replace IVFFlat with HNSW index on vector_store for better recall without parameter tuning
-- Note: vector_store table is created by Spring AI (initialize-schema: true)
-- This migration runs on top of Spring AI's schema initialization

-- Drop IVFFlat index if Spring AI created one (name may vary)
DROP INDEX IF EXISTS vector_store_embedding_idx;
DROP INDEX IF EXISTS spring_ai_vector_store_embedding_idx;

-- Create HNSW index for better recall
-- m=16: connections per node (default), ef_construction=64: build quality
-- Only create if vector_store table exists (Spring AI may not have run yet)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'vector_store') THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_indexes 
            WHERE tablename = 'vector_store' 
            AND indexname = 'vector_store_embedding_hnsw_idx'
        ) THEN
            EXECUTE 'CREATE INDEX vector_store_embedding_hnsw_idx ON vector_store 
                     USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)';
        END IF;
    END IF;
END $$;
