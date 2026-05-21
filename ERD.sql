erDiagram
    employees_51 {
        bigint emp_id PK
        bigint dept_id FK
        bigint position_id FK
        varchar password
        nvarchar name
        varchar email
    }
    departments_51 {
        bigint dept_id PK
        bigint parent_dept_id FK
        bigint leader_id FK
        nvarchar dept_name
    }
    positions_51 {
        bigint position_id PK
        nvarchar position_name
        int hierarchy_level
        decimal weight_base
    }
    evaluation_periods_51 {
        bigint period_id PK
        int period_year
        nvarchar period_name
        varchar status_code
    }
    evaluator_mappings_51 {
        bigint mapping_id PK
        bigint period_id FK
        bigint evaluatee_id FK
        bigint evaluator_id FK
        varchar relation_type_code
    }
    evaluations_51 {
        bigint eval_id PK
        bigint mapping_id FK
        bigint element_id FK
        int score
        nvarchar reason
        varchar confirm_status_code
    }
    final_grades_51 {
        bigint grade_id PK
        bigint period_id FK
        bigint emp_id FK
        int total_score
        varchar final_grade_code
    }

    departments_51 ||--o{ employees_51 : "contains"
    positions_51 ||--o{ employees_51 : "assigns"
    evaluation_periods_51 ||--o{ evaluator_mappings_51 : "belongs"
    employees_51 ||--o{ evaluator_mappings_51 : "evaluatee"
    employees_51 ||--o{ evaluator_mappings_51 : "evaluator"
    evaluator_mappings_51 ||--o{ evaluations_51 : "records"
    evaluation_periods_51 ||--o{ final_grades_51 : "belongs"
    employees_51 ||--o{ final_grades_51 : "receives"
