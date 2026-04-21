-- ==========================================
-- EES 시드 데이터
-- 기획서 기준: 직급 체계, 권한 체계, 관리자 계정 초기화
-- ==========================================

-- ==========================================
-- 1. 직급 마스터 데이터
-- 사원(1) → 대리(2) → 과장(3) → 차장(4) → 부장(5) → 이사(6, 임원급)
-- ==========================================
if not exists (select 1
from positions_51
where position_name = N'사원')
    insert into positions_51
    (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
values
    (N'사원', 1, 1.00, 'n', 0, getdate(), 1);

if not exists (select 1
from positions_51
where position_name = N'대리')
    insert into positions_51
    (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
values
    (N'대리', 2, 1.20, 'n', 0, getdate(), 1);

if not exists (select 1
from positions_51
where position_name = N'과장')
    insert into positions_51
    (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
values
    (N'과장', 3, 1.50, 'n', 0, getdate(), 1);

if not exists (select 1
from positions_51
where position_name = N'차장')
    insert into positions_51
    (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
values
    (N'차장', 4, 1.70, 'n', 0, getdate(), 1);

if not exists (select 1
from positions_51
where position_name = N'부장')
    insert into positions_51
    (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
values
    (N'부장', 5, 2.00, 'n', 0, getdate(), 1);

if not exists (select 1
from positions_51
where position_name = N'이사')
    insert into positions_51
    (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
values
    (N'이사', 6, 2.50, 'n', 0, getdate(), 1);

-- 시스템 관리자 전용 직급 (시스템 계정용, 평가 대상 아님)
if not exists (select 1
from positions_51
where position_name = N'관리자')
    insert into positions_51
    (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
values
    (N'관리자', 99, 1.00, 'n', 0, getdate(), 1);

-- ==========================================
-- 2. 권한(Role) 마스터 데이터
-- 기획서 기준 역할: 일반사원 / 부서장(1차평가) / 임원(최종확정) / 관리자
-- ==========================================
if not exists (select 1
from roles_51
where role_name = 'ROLE_USER')
    insert into roles_51
    (role_name, description, is_deleted, version, created_at, created_by)
values
    ('ROLE_USER', N'일반 사원 - 본인 평가 진행', 'n', 0, getdate(), 1);

if not exists (select 1
from roles_51
where role_name = 'ROLE_MANAGER')
    insert into roles_51
    (role_name, description, is_deleted, version, created_at, created_by)
values
    ('ROLE_MANAGER', N'부서장 - 부서원 1차 평가 진행', 'n', 0, getdate(), 1);

if not exists (select 1
from roles_51
where role_name = 'ROLE_EXECUTIVE')
    insert into roles_51
    (role_name, description, is_deleted, version, created_at, created_by)
values
    ('ROLE_EXECUTIVE', N'임원 - 최종 등급 확정 권한', 'n', 0, getdate(), 1);

if not exists (select 1
from roles_51
where role_name = 'ROLE_ADMIN')
    insert into roles_51
    (role_name, description, is_deleted, version, created_at, created_by)
values
    ('ROLE_ADMIN', N'시스템 관리자 - 전체 기능 접근', 'n', 0, getdate(), 1);

-- ==========================================
-- 3. 관리자 계정 전용 부서 (시스템 계정용 FK 필수)
-- ==========================================
if not exists (select 1
from departments_51
where dept_name = N'관리부서')
    insert into departments_51
    (dept_name, is_active, is_deleted, version, created_at, created_by)
values
    (N'관리부서', 'y', 'n', 0, getdate(), 1);

-- ==========================================
-- 4. 시스템 관리자 계정 (비밀번호: admin123)
-- 사번은 identity(1000,1) 기준으로 1000부터 자동 부여
-- ==========================================
if not exists (select 1
from employees_51
where email = 'admin@ees.com')
    insert into employees_51
    (emp_id, dept_id, position_id, password, name, email, phone, status_code, hire_date, is_deleted, version, created_at, created_by)
select
    1000,
    d.dept_id,
    p.position_id,
    '$2a$10$TplzaryA5s6EJNkOmU9PYeKD3D/QORwO9.3ee/i7TuZR6/17GIzCO',
    N'시스템관리자',
    'admin@ees.com',
    '010-0000-0000',
    'EMPLOYED',
    '2020-01-01',
    'n', 0, getdate(), 1
from departments_51 d, positions_51 p
where d.dept_name = N'관리부서'
    and p.position_name = N'관리자';

-- ==========================================
-- 5. 관리자 권한 매핑 (서브쿼리로 안전하게 처리)
-- ==========================================
if not exists (
    select 1
from employee_roles_51 er
    join employees_51 e on er.emp_id = e.emp_id
where e.email = 'admin@ees.com'
)
    insert into employee_roles_51
    (emp_id, role_id, is_deleted, version, created_at, created_by)
select e.emp_id, r.role_id, 'n', 0, getdate(), 1
from employees_51 e, roles_51 r
where e.email = 'admin@ees.com'
    and r.role_name = 'ROLE_ADMIN';

-- ==========================================
-- 6. 공통 코드 (Common Codes)
-- 평가 단계 / 차수 (EVAL_RELATION_TYPE)
-- ==========================================
if not exists (select 1
from common_codes_51
where group_code = 'EVAL_RELATION_TYPE' and code_value = 'SELF')
    insert into common_codes_51
    (group_code, code_value, code_name, description, is_deleted, version, created_at, created_by)
values
    ('EVAL_RELATION_TYPE', 'SELF', N'1차 본인 평가', N'피평가자 본인이 스스로를 평가하는 1차 평가입니다.', 'n', 0, getdate(), 1);

if not exists (select 1
from common_codes_51
where group_code = 'EVAL_RELATION_TYPE' and code_value = 'MANAGER')
    insert into common_codes_51
    (group_code, code_value, code_name, description, is_deleted, version, created_at, created_by)
values
    ('EVAL_RELATION_TYPE', 'MANAGER', N'2차 부서장 평가', N'직속 부서장에 의한 2차 평가입니다.', 'n', 0, getdate(), 1);

if not exists (select 1
from common_codes_51
where group_code = 'EVAL_RELATION_TYPE' and code_value = 'EXECUTIVE')
    insert into common_codes_51
    (group_code, code_value, code_name, description, is_deleted, version, created_at, created_by)
values
    ('EVAL_RELATION_TYPE', 'EXECUTIVE', N'3차 임원 평가', N'최종 확정을 위한 임원의 3차 평가입니다.', 'n', 0, getdate(), 1);

-- ==========================================
-- 평가 기간 상태 / 흐름 (EVAL_PERIOD_STATUS)
-- ==========================================
if not exists (select 1
from common_codes_51
where group_code = 'EVAL_PERIOD_STATUS' and code_value = 'READY')
    insert into common_codes_51
    (group_code, code_value, code_name, description, is_deleted, version, created_at, created_by)
values
    ('EVAL_PERIOD_STATUS', 'READY', N'평가 준비중', N'평가 기간이 시작되기 전 상태입니다.', 'n', 0, getdate(), 1);

if not exists (select 1
from common_codes_51
where group_code = 'EVAL_PERIOD_STATUS' and code_value = 'IN_PROGRESS')
    insert into common_codes_51
    (group_code, code_value, code_name, description, is_deleted, version, created_at, created_by)
values
    ('EVAL_PERIOD_STATUS', 'IN_PROGRESS', N'진행중', N'현재 해당 평가 차수가 진행 중입니다.', 'n', 0, getdate(), 1);

if not exists (select 1
from common_codes_51
where group_code = 'EVAL_PERIOD_STATUS' and code_value = 'COMPLETED')
    insert into common_codes_51
    (group_code, code_value, code_name, description, is_deleted, version, created_at, created_by)
values
    ('EVAL_PERIOD_STATUS', 'COMPLETED', N'평가 완료', N'모든 평가가 종료되고 결과가 확정된 상태입니다.', 'n', 0, getdate(), 1);

-- ==========================================
-- 평가 요소 유형 (EVAL_ELEMENT_TYPE)
-- ==========================================
if not exists (select 1 from common_codes_51 where group_code = 'EVAL_ELEMENT_TYPE' and code_value = 'PERFORMANCE')
    insert into common_codes_51 (group_code, code_value, code_name, description, is_deleted, version, created_at, created_by)
    values ('EVAL_ELEMENT_TYPE', 'PERFORMANCE', N'성과 평가', N'업무 성과 및 목표 달성도를 평가하는 항목입니다.', 'n', 0, getdate(), 1);

if not exists (select 1 from common_codes_51 where group_code = 'EVAL_ELEMENT_TYPE' and code_value = 'COMPETENCY')
    insert into common_codes_51 (group_code, code_value, code_name, description, is_deleted, version, created_at, created_by)
    values ('EVAL_ELEMENT_TYPE', 'COMPETENCY', N'역량 평가', N'직무 수행에 필요한 전문성 및 태도를 평가하는 항목입니다.', 'n', 0, getdate(), 1);

if not exists (select 1 from common_codes_51 where group_code = 'EVAL_ELEMENT_TYPE' and code_value = 'MULTI_DIMENSIONAL')
    insert into common_codes_51 (group_code, code_value, code_name, description, is_deleted, version, created_at, created_by)
    values ('EVAL_ELEMENT_TYPE', 'MULTI_DIMENSIONAL', N'다면 평가', N'동료 및 부서원에 의한 다각도 평가 항목입니다.', 'n', 0, getdate(), 1);

if not exists (select 1 from common_codes_51 where group_code = 'EVAL_ELEMENT_TYPE' and code_value = 'INTERVIEW')
    insert into common_codes_51 (group_code, code_value, code_name, description, is_deleted, version, created_at, created_by)
    values ('EVAL_ELEMENT_TYPE', 'INTERVIEW', N'면담 평가', N'면담을 통해 정성적으로 평가하는 항목입니다.', 'n', 0, getdate(), 1);

-- ==========================================
-- 7. 부서(Department) 데이터 계층형 구성
-- 본부(Top) -> 팀(Child)
-- ==========================================
-- [DX본부]
if not exists (select 1
from departments_51
where dept_name = N'DX본부')
    insert into departments_51
    (dept_name, is_active, is_deleted, version, created_at, created_by)
values
    (N'DX본부', 'y', 'n', 0, getdate(), 1);

-- [DX전략팀]
if not exists (select 1
from departments_51
where dept_name = N'DX전략팀')
    insert into departments_51
    (parent_dept_id, dept_name, is_active, is_deleted, version, created_at, created_by)
select dept_id, N'DX전략팀', 'y', 'n', 0, getdate(), 1
from departments_51
where dept_name = N'DX본부';

-- [DX서비스팀]
if not exists (select 1
from departments_51
where dept_name = N'DX서비스팀')
    insert into departments_51
    (parent_dept_id, dept_name, is_active, is_deleted, version, created_at, created_by)
select dept_id, N'DX서비스팀', 'y', 'n', 0, getdate(), 1
from departments_51
where dept_name = N'DX본부';

-- [경영지원본부]
if not exists (select 1
from departments_51
where dept_name = N'경영지원본부')
    insert into departments_51
    (dept_name, is_active, is_deleted, version, created_at, created_by)
values
    (N'경영지원본부', 'y', 'n', 0, getdate(), 1);

-- [인사팀]
if not exists (select 1
from departments_51
where dept_name = N'인사팀')
    insert into departments_51
    (parent_dept_id, dept_name, is_active, is_deleted, version, created_at, created_by)
select dept_id, N'인사팀', 'y', 'n', 0, getdate(), 1
from departments_51
where dept_name = N'경영지원본부';

-- [재무팀]
if not exists (select 1
from departments_51
where dept_name = N'재무팀')
    insert into departments_51
    (parent_dept_id, dept_name, is_active, is_deleted, version, created_at, created_by)
select dept_id, N'재무팀', 'y', 'n', 0, getdate(), 1
from departments_51
where dept_name = N'경영지원본부';
-- ==========================================
-- 8. 부서별 더미 사원 데이터 (조직도 확인용) - 깔끔하게 재생성
-- ==========================================
-- 기존 더미 데이터 정리 (사번 1001~1050)
delete from employee_roles_51 where emp_id between 1001 and 1050;
delete from login_logs_51 where emp_id between 1001 and 1050;
delete from employees_51 where emp_id between 1001 and 1050;

-- [DX전략팀 사원]
insert into employees_51
    (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1001, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'김철수', 'chulsoo@ees.com', '010-1234-5671', '2023-01-10', 'EMPLOYED', 'n', 0
from departments_51 d, positions_51 p
where d.dept_name = N'DX전략팀' and p.position_name = N'과장';

insert into employees_51
    (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1002, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'이영희', 'younghee@ees.com', '010-1234-5672', '2023-05-15', 'EMPLOYED', 'n', 0
from departments_51 d, positions_51 p
where d.dept_name = N'DX전략팀' and p.position_name = N'대리';

-- [DX서비스팀 사원]
insert into employees_51
    (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1003, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'박준호', 'junho@ees.com', '010-1234-5673', '2022-11-20', 'EMPLOYED', 'n', 0
from departments_51 d, positions_51 p
where d.dept_name = N'DX서비스팀' and p.position_name = N'부장';

insert into employees_51
    (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1004, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'최미경', 'mikyoung@ees.com', '010-1234-5674', '2024-02-01', 'EMPLOYED', 'n', 0
from departments_51 d, positions_51 p
where d.dept_name = N'DX서비스팀' and p.position_name = N'사원';

-- [인사팀 사원]
insert into employees_51
    (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1005, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'정민우', 'minwoo@ees.com', '010-1234-5675', '2021-03-15', 'EMPLOYED', 'n', 0
from departments_51 d, positions_51 p
where d.dept_name = N'인사팀' and p.position_name = N'차장';

-- [재무팀 사원]
insert into employees_51
    (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1006, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'한소희', 'sohee@ees.com', '010-1234-5676', '2023-08-20', 'EMPLOYED', 'n', 0
from departments_51 d, positions_51 p
where d.dept_name = N'재무팀' and p.position_name = N'대리';

-- [DX전략팀 추가]
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1011, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'전략1', 'str1@ees.com', '010-1111-1011', '2024-01-01', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'DX전략팀' and p.position_name = N'사원';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1012, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'전략2', 'str2@ees.com', '010-1111-1012', '2024-01-02', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'DX전략팀' and p.position_name = N'대리';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1013, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'전략3', 'str3@ees.com', '010-1111-1013', '2024-01-03', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'DX전략팀' and p.position_name = N'사원';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1014, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'전략4', 'str4@ees.com', '010-1111-1014', '2024-01-04', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'DX전략팀' and p.position_name = N'대리';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1015, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'전략5', 'str5@ees.com', '010-1111-1015', '2024-01-05', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'DX전략팀' and p.position_name = N'사원';

-- [DX서비스팀 추가]
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1016, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'서비스1', 'svc1@ees.com', '010-2222-1016', '2024-02-01', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'DX서비스팀' and p.position_name = N'사원';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1017, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'서비스2', 'svc2@ees.com', '010-2222-1017', '2024-02-02', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'DX서비스팀' and p.position_name = N'대리';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1018, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'서비스3', 'svc3@ees.com', '010-2222-1018', '2024-02-03', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'DX서비스팀' and p.position_name = N'사원';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1019, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'서비스4', 'svc4@ees.com', '010-2222-1019', '2024-02-04', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'DX서비스팀' and p.position_name = N'대리';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1020, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'서비스5', 'svc5@ees.com', '010-2222-1020', '2024-02-05', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'DX서비스팀' and p.position_name = N'사원';

-- [인사팀 추가]
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1021, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'인사1', 'hr1@ees.com', '010-3333-1021', '2024-03-01', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'인사팀' and p.position_name = N'사원';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1022, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'인사2', 'hr2@ees.com', '010-3333-1022', '2024-03-02', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'인사팀' and p.position_name = N'대리';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1023, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'인사3', 'hr3@ees.com', '010-3333-1023', '2024-03-03', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'인사팀' and p.position_name = N'사원';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1024, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'인사4', 'hr4@ees.com', '010-3333-1024', '2024-03-04', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'인사팀' and p.position_name = N'대리';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1025, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'인사5', 'hr5@ees.com', '010-3333-1025', '2024-03-05', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'인사팀' and p.position_name = N'사원';

-- [재무팀 추가]
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1026, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'재무1', 'fin1@ees.com', '010-4444-1026', '2024-04-01', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'재무팀' and p.position_name = N'사원';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1027, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'재무2', 'fin2@ees.com', '010-4444-1027', '2024-04-02', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'재무팀' and p.position_name = N'대리';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1028, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'재무3', 'fin3@ees.com', '010-4444-1028', '2024-04-03', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'재무팀' and p.position_name = N'사원';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1029, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'재무4', 'fin4@ees.com', '010-4444-1029', '2024-04-04', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'재무팀' and p.position_name = N'대리';
insert into employees_51 (emp_id, dept_id, position_id, password, name, email, phone, hire_date, status_code, is_deleted, version)
select 1030, d.dept_id, p.position_id, '$2a$10$3q9D33VLDucrnlOTih.rSeLSxVtR1p3rWwiJofqjtLhUW/GXJ6wde', N'재무5', 'fin5@ees.com', '010-4444-1030', '2024-04-05', 'EMPLOYED', 'n', 0 from departments_51 d, positions_51 p where d.dept_name = N'재무팀' and p.position_name = N'사원';

-- [부서 리더 설정]
update departments_51 set leader_id = 1001 where dept_name = N'DX전략팀';
update departments_51 set leader_id = 1003 where dept_name = N'DX서비스팀';
update departments_51 set leader_id = 1005 where dept_name = N'인사팀';
update departments_51 set leader_id = 1006 where dept_name = N'재무팀';

-- [일반 사원 권한 부여 (ROLE_USER: 리더 제외)]
insert into employee_roles_51 (emp_id, role_id)
select e.emp_id, r.role_id
from employees_51 e, roles_51 r
where e.emp_id between 1001 and 1050
    and e.emp_id not in (1001, 1003, 1005, 1006)
    and r.role_name = 'ROLE_USER';

-- [부서장 권한 부여 (ROLE_MANAGER: 오직 리더만)]
insert into employee_roles_51 (emp_id, role_id)
select e.emp_id, r.role_id
from employees_51 e, roles_51 r
where e.emp_id in (1001, 1003, 1005, 1006)
    and r.role_name = 'ROLE_MANAGER';

-- ==========================================

-- ==========================================
-- 9. 초기 평가 차수 데이터 (테스트용)
-- ==========================================
if not exists (select 1 from evaluation_periods_51 where period_name = N'2026년 상반기 정기 평가')
    insert into evaluation_periods_51 (period_year, period_name, status_code, start_date, end_date, is_deleted, version, created_at, created_by)
    values (2026, N'2026년 상반기 정기 평가', 'IN_PROGRESS', '2026-01-01', '2026-06-30', 'n', 0, getdate(), 1000);
