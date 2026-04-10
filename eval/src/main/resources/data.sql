-- ==========================================
-- 기초 시드 데이터 삽입 (FK 참조용)
-- ==========================================

-- 부서 기초 데이터
insert into departments (dept_name, is_deleted, version, created_at, created_by)
values (N'경영지원팀', 'n', 0, getdate(), 1);

insert into departments (dept_name, is_deleted, version, created_at, created_by)
values (N'개발팀', 'n', 0, getdate(), 1);

-- 직급 기초 데이터
insert into positions (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
values (N'사원', 1, 1.00, 'n', 0, getdate(), 1);

insert into positions (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
values (N'대리', 2, 1.20, 'n', 0, getdate(), 1);

insert into positions (position_name, hierarchy_level, weight_base, is_deleted, version, created_at, created_by)
values (N'과장', 3, 1.50, 'n', 0, getdate(), 1);

-- 권한 기초 데이터
insert into roles (role_name, description, is_deleted, version, created_at, created_by)
values ('ROLE_ADMIN', N'시스템 관리자', 'n', 0, getdate(), 1);

insert into roles (role_name, description, is_deleted, version, created_at, created_by)
values ('ROLE_MANAGER', N'부서 관리자', 'n', 0, getdate(), 1);

insert into roles (role_name, description, is_deleted, version, created_at, created_by)
values ('ROLE_USER', N'일반 사용자', 'n', 0, getdate(), 1);

-- 기본 관리자 사원 데이터 (비밀번호: admin123)
insert into employees (dept_id, position_id, username, password, name, email, hire_date, is_deleted, version, created_at, created_by)
values (1, 3, 'admin', '$2a$10$6rmAcp4KBNF47Qjr3XgWHuUI1glNR0LavyzPSY78BqlOiASJDzsom', N'시스템관리자', 'admin@ees.com', '2020-01-01', 'n', 0, getdate(), 1);

-- 관리자 권한 부여
insert into employee_roles (emp_id, role_id, is_deleted, version, created_at, created_by)
values (1, 1, 'n', 0, getdate(), 1);

-- 퇴사자 테스트 데이터 (비밀번호: user123)
insert into employees (dept_id, position_id, username, password, name, email, hire_date, is_deleted, version, created_at, created_by)
values (1, 1, 'retired_user', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DM95wS5F90O.', N'퇴사자', 'retired@ees.com', '2019-01-01', 'y', 0, getdate(), 1);

insert into employee_roles (emp_id, role_id, is_deleted, version, created_at, created_by)
values (2, 3, 'n', 0, getdate(), 1);

-- 평가 차수 시드 데이터
insert into evaluation_periods (period_year, period_name, status_code, start_date, end_date, is_deleted, version, created_at, created_by)
values (2026, N'2026년 상반기 평가', 'IN_PROGRESS', '2026-04-01', '2026-06-30', 'n', 0, getdate(), 1);

insert into evaluation_periods (period_year, period_name, status_code, start_date, end_date, is_deleted, version, created_at, created_by)
values (2026, N'2026년 하반기 평가', 'PLANNED', '2026-07-01', '2026-12-31', 'n', 0, getdate(), 1);

insert into evaluation_periods (period_year, period_name, status_code, start_date, end_date, is_deleted, version, created_at, created_by)
values (2025, N'2025년 하반기 평가', 'COMPLETED', '2025-07-01', '2025-12-31', 'n', 0, getdate(), 1);

