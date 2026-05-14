-- 평가 매핑 조회 성능 최적화를 위한 복합 인덱스 추가
-- 1. period_id, evaluator_id 기반의 필터링 성능 향상
-- 2. relation_type_code 조건 추가 시 인덱스 스캔(Index Seek) 유도
-- 3. is_deleted = 'n' 조건을 포함하여 유효한 데이터만 빠르게 검색

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_eval_mapping_search_51' AND object_id = OBJECT_ID('evaluator_mappings_51'))
BEGIN
    CREATE NONCLUSTERED INDEX idx_eval_mapping_search_51
    ON evaluator_mappings_51 (period_id, evaluator_id, is_deleted, relation_type_code);
    
    PRINT 'Index idx_eval_mapping_search_51 created successfully.';
END
ELSE
BEGIN
    PRINT 'Index idx_eval_mapping_search_51 already exists.';
END
