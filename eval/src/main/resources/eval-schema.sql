-- MSSQL EES 전체 테이블 생성 스크립트 (소문자 및 락 기본 정책 반영)

alter database scoped configuration set IDENTITY_CACHE = OFF;
-- 이전 _51 테이블의 외래 키 제약 조건만 삭제 (다른 팀 테이블 보호)
EXEC sp_executesql N'
DECLARE @drop_constraints_sql NVARCHAR(MAX) = N'''';
SELECT @drop_constraints_sql += ''ALTER TABLE '' + QUOTENAME(schema_name(schema_id)) + ''.'' + QUOTENAME(object_name(parent_object_id)) +
    '' DROP CONSTRAINT '' + QUOTENAME(name) + '';''
FROM sys.foreign_keys
WHERE object_name(parent_object_id) LIKE ''%_51'';
EXEC sp_executesql @drop_constraints_sql;
';

-- 이전 _51 테이블의 외래 키 제약 조건만 삭제 (다른 팀 테이블 보호)
drop table if exists evidences_51;
drop table if exists login_logs_51;
drop table if exists final_grades_51;
drop table if exists evaluations_51;
drop table if exists evaluator_mappings_51;
drop table if exists evaluation_elements_51;
drop table if exists evaluation_periods_51;
drop table if exists employee_roles_51;
drop table if exists interviews_51;
drop table if exists employees_51;
drop table if exists departments_51;
drop table if exists roles_51;
drop table if exists positions_51;
drop table if exists common_codes_51;

-- ==========================================
-- 1. 기초 시스템 데이터
-- ==========================================
create table common_codes_51
(
    code_id bigint identity(1,1) primary key,
    group_code varchar(50) not null,
    code_value varchar(50) not null,
    code_name nvarchar(100) not null,
    description nvarchar(255),
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint
);

create table positions_51
(
    position_id bigint identity(1,1) primary key,
    position_name nvarchar(50) not null,
    hierarchy_level int not null,
    weight_base decimal(5,2) not null,
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint
);

create table roles_51
(
    role_id bigint identity(1,1) primary key,
    role_name varchar(50) not null,
    description nvarchar(255),
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint
);

-- ==========================================
-- 2. 조직 및 사용자
-- ==========================================
create table departments_51
(
    dept_id bigint identity(1,1) primary key,
    parent_dept_id bigint,
    leader_id bigint,
    dept_name nvarchar(100) not null,
    is_active char(1) default 'y' not null,
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint,
    foreign key (parent_dept_id) references departments_51(dept_id)
);

create table employees_51
(
    emp_id bigint primary key,
    dept_id bigint not null,
    position_id bigint not null,
    password varchar(255) not null,
    name nvarchar(50) not null,
    email varchar(255),
    phone varchar(50),
    status_code varchar(20) default 'employed',
    -- 재직/휴직/퇴사 상태 관리
    hire_date date,
    retire_date date,
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint,
    foreign key (dept_id) references departments_51(dept_id),
    foreign key (position_id) references positions_51(position_id)
);

-- departments_51.leader_id FK 추가 (employees_51 생성 이후)
alter table departments_51
    add constraint fk_dept_leader
    foreign key (leader_id) references employees_51(emp_id);


create table employee_roles_51
(
    emp_id bigint not null,
    role_id bigint not null,
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint,
    primary key (emp_id, role_id),
    foreign key (emp_id) references employees_51(emp_id),
    foreign key (role_id) references roles_51(role_id)
);

create table login_logs_51
(
    log_id bigint identity(1,1) primary key,
    emp_id bigint,
    login_input varchar(255) not null,
    result_code varchar(20) not null,
    is_failure char(1) default 'n', -- 로그인 실패 여부 (y/n)
    ip_address varchar(50),
    user_agent nvarchar(max),
    created_at datetime default getdate(),
    foreign key (emp_id) references employees_51(emp_id)
);

-- ==========================================
-- 3. 평가 기준 및 매핑
-- ==========================================
create table evaluation_periods_51
(
    period_id bigint identity(1,1) primary key,
    period_year int not null,
    period_name nvarchar(100) not null,
    status_code varchar(50) not null,
    start_date date,
    end_date date,
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint
);

create table evaluation_elements_51
(
    element_id bigint identity(1,1) primary key,
    period_id bigint not null,
    dept_id bigint,
    element_type_code varchar(50) not null,
    element_name nvarchar(255) not null,
    max_score decimal(5,2) not null,
    weight decimal(5,2) not null,
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint,
    foreign key (period_id) references evaluation_periods_51(period_id),
    foreign key (dept_id) references departments_51(dept_id)
);

create table evaluator_mappings_51
(
    mapping_id bigint identity(1,1) primary key,
    period_id bigint not null,
    evaluatee_id bigint not null,
    evaluator_id bigint not null,
    relation_type_code varchar(50) not null,
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint,
    foreign key (period_id) references evaluation_periods_51(period_id),
    foreign key (evaluatee_id) references employees_51(emp_id),
    foreign key (evaluator_id) references employees_51(emp_id)
);

-- ==========================================
-- 4. 평가 수행
-- ==========================================
create table evaluations_51
(
    eval_id bigint identity(1,1) primary key,
    mapping_id bigint not null,
    element_id bigint not null,
    score decimal(5,2),
    old_score decimal(5,2),
    reason nvarchar(255),
    comments nvarchar(max),
    -- final_grades 통합을 위한 확정 점수 및 등급 정보
    total_score decimal(7,2),
    grade_code varchar(50),
    confirm_status_code varchar(50),
    -- 텍스트 피드백/수행과정 기록용
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint,
    foreign key (mapping_id) references evaluator_mappings_51(mapping_id),
    foreign key (element_id) references evaluation_elements_51(element_id)
);

create table interviews_51
(
    interview_id bigint identity(1,1) primary key,
    mapping_id bigint not null,
    content nvarchar(max),
    status_code varchar(50),
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint,
    foreign key (mapping_id) references evaluator_mappings_51(mapping_id)
);

create table evidences_51
(
    evidence_id bigint identity(1,1) primary key,
    eval_id bigint not null,
    file_name nvarchar(255) not null,
    file_path nvarchar(500) not null,
    file_size bigint,
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint,
    foreign key (eval_id) references evaluations_51(eval_id)
);