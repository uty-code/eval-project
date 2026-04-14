-- ==========================================
-- _51 테이블 데이터 초기화 스크립트
-- 테이블 구조(스키마)는 유지하고 데이터만 삭제
-- FK 참조 역순으로 삭제
-- ==========================================

-- 1. 최하위 자식 데이터 (평가 결과물)
delete from evaluation_histories_51;
delete from evidences_51;
delete from interviews_51;
delete from final_grades_51;

-- 2. 평가 수행 데이터
delete from evaluations_51;

-- 3. 매핑 및 평가 기준 데이터
delete from evaluator_mappings_51;
delete from evaluation_elements_51;
delete from evaluation_periods_51;

-- 4. 사용자 및 조직 데이터
delete from employee_roles_51;
delete from employees_51;
delete from departments_51;

-- 5. 기초 마스터 데이터
delete from positions_51;
delete from roles_51;
delete from common_codes_51;

-- identity 시드 초기화 (auto increment를 1부터 다시 시작)
dbcc checkident('departments_51', reseed, 0);
dbcc checkident('positions_51', reseed, 0);
dbcc checkident('roles_51', reseed, 0);
dbcc checkident('employees_51', reseed, 999);
-- 다음 emp_id = 1000
dbcc checkident('evaluation_periods_51', reseed, 0);
dbcc checkident('evaluation_elements_51', reseed, 0);
dbcc checkident('evaluator_mappings_51', reseed, 0);
dbcc checkident('evaluations_51', reseed, 0);
dbcc checkident('evaluation_histories_51', reseed, 0);
dbcc checkident('interviews_51', reseed, 0);
dbcc checkident('evidences_51', reseed, 0);
dbcc checkident('final_grades_51', reseed, 0);
dbcc checkident('common_codes_51', reseed, 0);

-- 삭제 확인
select N'데이터 초기화 완료' as STATUS;
