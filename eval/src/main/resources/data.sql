-- ==========================================
-- EES 시드 데이터
-- 기획서 기준: 직급 체계, 권한 체계, 관리자 계정 초기화
-- ==========================================

-- ==========================================
-- 1. 직급 마스터 데이터
-- 사원(1) → 대리(2) → 과장(3) → 차장(4) → 부장(5) → 이사(6, 임원급)
-- ==========================================
if not exists (select 1 from positions_51 where position_name = N'사원')
    insert into positions_51 (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
    values (N'사원', 1, 1.00, 'n', 0, getdate(), 1);

if not exists (select 1 from positions_51 where position_name = N'대리')
    insert into positions_51 (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
    values (N'대리', 2, 1.20, 'n', 0, getdate(), 1);

if not exists (select 1 from positions_51 where position_name = N'과장')
    insert into positions_51 (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
    values (N'과장', 3, 1.50, 'n', 0, getdate(), 1);

if not exists (select 1 from positions_51 where position_name = N'차장')
    insert into positions_51 (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
    values (N'차장', 4, 1.70, 'n', 0, getdate(), 1);

if not exists (select 1 from positions_51 where position_name = N'부장')
    insert into positions_51 (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
    values (N'부장', 5, 2.00, 'n', 0, getdate(), 1);

if not exists (select 1 from positions_51 where position_name = N'이사')
    insert into positions_51 (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
    values (N'이사', 6, 2.50, 'n', 0, getdate(), 1);

-- 시스템 관리자 전용 직급 (시스템 계정용, 평가 대상 아님)
if not exists (select 1 from positions_51 where position_name = N'관리자')
    insert into positions_51 (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
    values (N'관리자', 99, 1.00, 'n', 0, getdate(), 1);

-- ==========================================
-- 2. 권한(Role) 마스터 데이터
-- 기획서 기준 역할: 일반사원 / 부서장(1차평가) / 임원(최종확정) / 관리자
-- ==========================================
if not exists (select 1 from roles_51 where role_name = 'ROLE_USER')
    insert into roles_51 (role_name, description, is_deleted, version, created_at, created_by)
    values ('ROLE_USER', N'일반 사원 - 본인 평가 진행', 'n', 0, getdate(), 1);

if not exists (select 1 from roles_51 where role_name = 'ROLE_MANAGER')
    insert into roles_51 (role_name, description, is_deleted, version, created_at, created_by)
    values ('ROLE_MANAGER', N'부서장 - 부서원 1차 평가 진행', 'n', 0, getdate(), 1);

if not exists (select 1 from roles_51 where role_name = 'ROLE_EXECUTIVE')
    insert into roles_51 (role_name, description, is_deleted, version, created_at, created_by)
    values ('ROLE_EXECUTIVE', N'임원 - 최종 등급 확정 권한', 'n', 0, getdate(), 1);

if not exists (select 1 from roles_51 where role_name = 'ROLE_ADMIN')
    insert into roles_51 (role_name, description, is_deleted, version, created_at, created_by)
    values ('ROLE_ADMIN', N'시스템 관리자 - 전체 기능 접근', 'n', 0, getdate(), 1);

-- ==========================================
-- 3. 관리자 계정 전용 부서 (시스템 계정용 FK 필수)
-- ==========================================
if not exists (select 1 from departments_51 where dept_name = N'관리부서')
    insert into departments_51 (dept_name, is_deleted, version, created_at, created_by)
    values (N'관리부서', 'n', 0, getdate(), 1);

-- ==========================================
-- 4. 시스템 관리자 계정 (비밀번호: admin123)
-- 사번은 identity(1000,1) 기준으로 1000부터 자동 부여
-- ==========================================
if not exists (select 1 from employees_51 where email = 'admin@ees.com')
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
    select 1 from employee_roles_51 er
    join employees_51 e on er.emp_id = e.emp_id
    where e.email = 'admin@ees.com'
)
    insert into employee_roles_51 (emp_id, role_id, is_deleted, version, created_at, created_by)
    select e.emp_id, r.role_id, 'n', 0, getdate(), 1
    from employees_51 e, roles_51 r
    where e.email = 'admin@ees.com'
      and r.role_name = 'ROLE_ADMIN';
