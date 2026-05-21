-- ==========================================
-- EES (Employee Evaluation System) DDL 스크립트
-- ==========================================

-- 1. departments_51 (부서)
CREATE TABLE departments_51 (
    dept_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    parent_dept_id BIGINT NULL,
    leader_id BIGINT NULL,
    dept_name NVARCHAR(100) NOT NULL,
    
    -- 공통 속성
    is_deleted CHAR(1) DEFAULT 'n' NOT NULL,
    version INT DEFAULT 0 NOT NULL,
    created_at DATETIME DEFAULT GETDATE() NOT NULL,
    created_by VARCHAR(50) NULL,
    updated_at DATETIME DEFAULT GETDATE() NOT NULL,
    updated_by VARCHAR(50) NULL
);

-- 2. positions_51 (직책/직급)
CREATE TABLE positions_51 (
    position_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    position_name NVARCHAR(50) NOT NULL,
    hierarchy_level INT NOT NULL,
    weight_base DECIMAL(5,2) NOT NULL,
    
    -- 공통 속성
    is_deleted CHAR(1) DEFAULT 'n' NOT NULL,
    version INT DEFAULT 0 NOT NULL,
    created_at DATETIME DEFAULT GETDATE() NOT NULL,
    created_by VARCHAR(50) NULL,
    updated_at DATETIME DEFAULT GETDATE() NOT NULL,
    updated_by VARCHAR(50) NULL
);

-- 3. employees_51 (사원)
CREATE TABLE employees_51 (
    emp_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    dept_id BIGINT NOT NULL,
    position_id BIGINT NOT NULL,
    password VARCHAR(255) NOT NULL,
    name NVARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    
    -- 공통 속성
    is_deleted CHAR(1) DEFAULT 'n' NOT NULL,
    version INT DEFAULT 0 NOT NULL,
    created_at DATETIME DEFAULT GETDATE() NOT NULL,
    created_by VARCHAR(50) NULL,
    updated_at DATETIME DEFAULT GETDATE() NOT NULL,
    updated_by VARCHAR(50) NULL,
    
    FOREIGN KEY (dept_id) REFERENCES departments_51(dept_id),
    FOREIGN KEY (position_id) REFERENCES positions_51(position_id)
);

-- 4. evaluation_periods_51 (평가 차수)
CREATE TABLE evaluation_periods_51 (
    period_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    period_year INT NOT NULL,
    period_name NVARCHAR(100) NOT NULL,
    status_code VARCHAR(20) NOT NULL, -- 예: PLAN, IN_PROGRESS, CLOSED
    
    -- 공통 속성
    is_deleted CHAR(1) DEFAULT 'n' NOT NULL,
    version INT DEFAULT 0 NOT NULL,
    created_at DATETIME DEFAULT GETDATE() NOT NULL,
    created_by VARCHAR(50) NULL,
    updated_at DATETIME DEFAULT GETDATE() NOT NULL,
    updated_by VARCHAR(50) NULL
);

-- 5. evaluator_mappings_51 (평가 관계 매핑)
CREATE TABLE evaluator_mappings_51 (
    mapping_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    period_id BIGINT NOT NULL,
    evaluatee_id BIGINT NOT NULL,
    evaluator_id BIGINT NOT NULL,
    relation_type_code VARCHAR(20) NOT NULL, -- 예: SELF, LEADER_1, LEADER_2
    
    -- 공통 속성
    is_deleted CHAR(1) DEFAULT 'n' NOT NULL,
    version INT DEFAULT 0 NOT NULL,
    created_at DATETIME DEFAULT GETDATE() NOT NULL,
    created_by VARCHAR(50) NULL,
    updated_at DATETIME DEFAULT GETDATE() NOT NULL,
    updated_by VARCHAR(50) NULL,
    
    FOREIGN KEY (period_id) REFERENCES evaluation_periods_51(period_id),
    FOREIGN KEY (evaluatee_id) REFERENCES employees_51(emp_id),
    FOREIGN KEY (evaluator_id) REFERENCES employees_51(emp_id)
);

-- 6. evaluations_51 (평가 결과 제출)
CREATE TABLE evaluations_51 (
    eval_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    mapping_id BIGINT NOT NULL,
    element_id BIGINT NULL,
    score INT NOT NULL,
    reason NVARCHAR(500) NULL,
    confirm_status_code VARCHAR(20) NOT NULL, -- 예: TEMP, SUBMITTED
    
    -- 공통 속성
    is_deleted CHAR(1) DEFAULT 'n' NOT NULL,
    version INT DEFAULT 0 NOT NULL,
    created_at DATETIME DEFAULT GETDATE() NOT NULL,
    created_by VARCHAR(50) NULL,
    updated_at DATETIME DEFAULT GETDATE() NOT NULL,
    updated_by VARCHAR(50) NULL,
    
    FOREIGN KEY (mapping_id) REFERENCES evaluator_mappings_51(mapping_id)
);

-- 7. final_grades_51 (최종 등급)
CREATE TABLE final_grades_51 (
    grade_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    period_id BIGINT NOT NULL,
    emp_id BIGINT NOT NULL,
    total_score INT NOT NULL,
    final_grade_code VARCHAR(10) NOT NULL, -- 예: S, A, B, C, D
    
    -- 공통 속성
    is_deleted CHAR(1) DEFAULT 'n' NOT NULL,
    version INT DEFAULT 0 NOT NULL,
    created_at DATETIME DEFAULT GETDATE() NOT NULL,
    created_by VARCHAR(50) NULL,
    updated_at DATETIME DEFAULT GETDATE() NOT NULL,
    updated_by VARCHAR(50) NULL,
    
    FOREIGN KEY (period_id) REFERENCES evaluation_periods_51(period_id),
    FOREIGN KEY (emp_id) REFERENCES employees_51(emp_id)
);
