-- MSSQL EES 전체 테이블 생성 스크립트 (소문자 및 락 기본 정책 반영)

-- 이전 테이블 존재 시 삭제 로직 (순서: 종속성의 역순)
drop table if exists final_grades;
drop table if exists evidences;
drop table if exists interviews;
drop table if exists evaluation_histories;
drop table if exists evaluations;
drop table if exists evaluator_mappings;
drop table if exists evaluation_elements;
drop table if exists evaluation_periods;
drop table if exists employee_roles;
drop table if exists employees;
drop table if exists departments;
drop table if exists roles;
drop table if exists positions;
drop table if exists common_codes;

-- ==========================================
-- 1. 기초 시스템 데이터
-- ==========================================
create table common_codes
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

create table positions
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

create table roles
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
create table departments
(
    dept_id bigint identity(1,1) primary key,
    parent_dept_id bigint,
    dept_name nvarchar(100) not null,
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint,
    foreign key (parent_dept_id) references departments(dept_id)
);

create table employees
(
    emp_id bigint identity(1000,1) primary key,
    dept_id bigint not null,
    position_id bigint not null,
    username varchar(100) not null unique,
    password varchar(255) not null,
    name nvarchar(50) not null,
    email varchar(255),
    phone varchar(20),
    status_code varchar(20) not null,
    hire_date date,
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint,
    foreign key (dept_id) references departments(dept_id),
    foreign key (position_id) references positions(position_id)
);

create table employee_roles
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
    foreign key (emp_id) references employees(emp_id),
    foreign key (role_id) references roles(role_id)
);

-- ==========================================
-- 3. 평가 기준 및 매핑
-- ==========================================
create table evaluation_periods
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

create table evaluation_elements
(
    element_id bigint identity(1,1) primary key,
    period_id bigint not null,
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
    foreign key (period_id) references evaluation_periods(period_id)
);

create table evaluator_mappings
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
    foreign key (period_id) references evaluation_periods(period_id),
    foreign key (evaluatee_id) references employees(emp_id),
    foreign key (evaluator_id) references employees(emp_id)
);

-- ==========================================
-- 4. 평가 수행
-- ==========================================
create table evaluations
(
    eval_id bigint identity(1,1) primary key,
    mapping_id bigint not null,
    element_id bigint not null,
    score decimal(5,2),
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint,
    foreign key (mapping_id) references evaluator_mappings(mapping_id),
    foreign key (element_id) references evaluation_elements(element_id)
);

create table evaluation_histories
(
    history_id bigint identity(1,1) primary key,
    eval_id bigint not null,
    old_score decimal(5,2),
    new_score decimal(5,2),
    reason nvarchar(255),
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint,
    foreign key (eval_id) references evaluations(eval_id)
);

create table interviews
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
    foreign key (mapping_id) references evaluator_mappings(mapping_id)
);

create table evidences
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
    foreign key (eval_id) references evaluations(eval_id)
);

-- ==========================================
-- 5. 결과 확정
-- ==========================================
create table final_grades
(
    grade_id bigint identity(1,1) primary key,
    period_id bigint not null,
    emp_id bigint not null,
    total_score decimal(7,2),
    grade_code varchar(50),
    confirm_status_code varchar(50),
    is_deleted char(1) default 'n',
    version int default 0,
    created_at datetime default getdate(),
    created_by bigint,
    updated_at datetime,
    updated_by bigint,
    foreign key (period_id) references evaluation_periods(period_id),
    foreign key (emp_id) references employees(emp_id)
);
