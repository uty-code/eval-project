-- ==========================================
-- _51 테이블 데이터 초기화 스크립트 (교정본)
-- ==========================================

-- 1. 최하위 자식 데이터 (평가 결과물 및 로그)
delete from evidences_51;
delete from interviews_51;
delete from login_logs_51; -- [추가] 사원 삭제 전 로그 먼저 삭제

-- 2. 평가 수행 데이터
delete from evaluations_51;

-- 3. 매핑 및 평가 기준 데이터
delete from evaluator_mappings_51;
delete from evaluation_elements_51;
delete from evaluation_periods_51;

-- 4. 사용자 및 조직 데이터 (상호 참조 해결을 위해 leader_id 먼저 null 처리)
update departments_51 set leader_id = null;
delete from employee_roles_51;
delete from employees_51;
delete from departments_51;

-- 5. 기초 마스터 데이터
delete from positions_51;
delete from roles_51;
delete from common_codes_51;

-- identity 시드 초기화
dbcc checkident('departments_51', reseed, 0);
dbcc checkident('positions_51', reseed, 0);
dbcc checkident('roles_51', reseed, 0);
dbcc checkident('login_logs_51', reseed, 0);

dbcc checkident('evaluation_periods_51', reseed, 0);
dbcc checkident('evaluation_elements_51', reseed, 0);
dbcc checkident('evaluator_mappings_51', reseed, 0);
dbcc checkident('evaluations_51', reseed, 0);
dbcc checkident('interviews_51', reseed, 0);
dbcc checkident('evidences_51', reseed, 0);
dbcc checkident('common_codes_51', reseed, 0);

-- 삭제 확인
select N'데이터 초기화 완료' as STATUS;
