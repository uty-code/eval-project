-- ============================================================
-- 로그인 이력 테이블 생성 (Audit Log)
-- 최초 실행 시 DB에 직접 실행하거나 schema.sql에 추가
-- ============================================================

-- 기존 테이블 재생성 시
DROP TABLE IF EXISTS login_logs_51;

CREATE TABLE login_logs_51
(
    log_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    emp_id       BIGINT,                          -- NULL 허용 (존재하지 않는 사번으로 시도 시)
    login_input  VARCHAR(100) NOT NULL,           -- 실제 입력한 사번 원문
    result_code  VARCHAR(20)  NOT NULL,           -- SUCCESS / FAIL_INVALID / FAIL_LOCKED / FAIL_RETIRED / FAIL_PENDING
    ip_address   VARCHAR(45),                     -- IPv4/IPv6 모두 허용
    user_agent   NVARCHAR(500),                   -- 브라우저/기기 정보
    created_at   DATETIME     DEFAULT GETDATE()
);
