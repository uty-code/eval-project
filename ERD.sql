-- ==========================================
-- 1. 기초 시스템 데이터
-- ==========================================
create table common_codes_51
(
    code_id bigint identity(1,1) primary key,
    group_code varchar(50),
    code_value varchar(50),
    code_name nvarchar(100),
    description nvarchar(255)
);

create table positions_51
(
    position_id bigint identity(1,1) primary key,
    position_name nvarchar(50),
    hierarchy_level int,
    weight_base decimal(5,2)
);

create table roles_51
(
    role_id bigint identity(1,1) primary key,
    role_name varchar(50),
    description nvarchar(255)
);

-- ==========================================
-- 2. 조직 및 사용자
-- ==========================================
create table departments_51
(
    dept_id bigint identity(1,1) primary key,
    parent_dept_id bigint,
    dept_name nvarchar(100),
    foreign key (parent_dept_id) references departments_51(dept_id)
);

create table employees_51
(
    emp_id bigint primary key,
    dept_id bigint,
    position_id bigint,
    name nvarchar(50),
    email varchar(255),
    status_code varchar(20), -- 재직/휴직/퇴사 상태 관리
    hire_date date,
    foreign key (dept_id) references departments_51(dept_id),
    foreign key (position_id) references positions_51(position_id)
);

create table employee_roles_51
(
    emp_id bigint,
    role_id bigint,
    primary key (emp_id, role_id),
    foreign key (emp_id) references employees_51(emp_id),
    foreign key (role_id) references roles_51(role_id)
);

-- ==========================================
-- 3. 평가 기준 및 매핑
-- ==========================================
create table evaluation_periods_51
(
    period_id bigint identity(1,1) primary key,
    period_year int,
    period_name nvarchar(100),
    status_code varchar(50),
    start_date date,
    end_date date
);

create table evaluation_elements_51
(
    element_id bigint identity(1,1) primary key,
    period_id bigint,
    element_type_code varchar(50),
    element_name nvarchar(255),
    max_score decimal(5,2),
    weight decimal(5,2),
    foreign key (period_id) references evaluation_periods_51(period_id)
);

create table evaluator_mappings_51
(
    mapping_id bigint identity(1,1) primary key,
    period_id bigint,
    evaluatee_id bigint,
    evaluator_id bigint,
    relation_type_code varchar(50),
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
    mapping_id bigint,
    element_id bigint,
    score decimal(5,2),
    comments nvarchar(max),
    foreign key (mapping_id) references evaluator_mappings_51(mapping_id),
    foreign key (element_id) references evaluation_elements_51(element_id)
);

create table evaluation_histories_51
(
    history_id bigint identity(1,1) primary key,
    eval_id bigint,
    old_score decimal(5,2),
    new_score decimal(5,2),
    reason nvarchar(255),
    foreign key (eval_id) references evaluations_51(eval_id)
);

create table interviews_51
(
    interview_id bigint identity(1,1) primary key,
    mapping_id bigint,
    content nvarchar(max),
    status_code varchar(50),
    foreign key (mapping_id) references evaluator_mappings_51(mapping_id)
);

create table evidences_51
(
    evidence_id bigint identity(1,1) primary key,
    eval_id bigint,
    file_name nvarchar(255),
    file_path nvarchar(500),
    foreign key (eval_id) references evaluations_51(eval_id)
);

-- ==========================================
-- 5. 결과 확정
-- ==========================================
create table final_grades_51
(
    grade_id bigint identity(1,1) primary key,
    period_id bigint,
    emp_id bigint,
    total_score decimal(7,2),
    grade_code varchar(50),
    confirm_status_code varchar(50),
    foreign key (period_id) references evaluation_periods_51(period_id),
    foreign key (emp_id) references employees_51(emp_id)
);