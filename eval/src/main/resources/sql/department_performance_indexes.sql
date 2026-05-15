/*
 * 부서 시스템 성능 최적화를 위한 인덱스 전략
 */

-- 1. 부서별 사원 조회 및 인원 집계 최적화
-- (dept_id 기반 그룹화 및 필터링 성능 향상)
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_employees_51_dept_deleted')
BEGIN
    CREATE INDEX IX_employees_51_dept_deleted
    ON employees_51 (dept_id, is_deleted);
END
GO

-- 2. 계층형 부서 트리 탐색(Parent Lookup) 성능 보장
-- (상위 부서 ID 기반의 자식 부서 조회 성능 향상)
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_departments_51_parent_deleted')
BEGIN
    CREATE INDEX IX_departments_51_parent_deleted
    ON departments_51 (parent_dept_id, is_deleted);
END
GO

-- 3. 부서 리더 정보 JOIN 연산 비용 감소
-- (leader_id 기반의 사원 정보 JOIN 성능 향상)
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_departments_51_leader_deleted')
BEGIN
    CREATE INDEX IX_departments_51_leader_deleted
    ON departments_51 (leader_id, is_deleted);
END
GO
