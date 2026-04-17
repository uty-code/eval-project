package com.ees.eval.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 시작 시 login_logs_51 테이블이 없으면 자동으로 생성합니다.
 * schema.sql의 init.mode가 'never'로 설정된 환경에서 추가 테이블을 안전하게 반영합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        createLoginLogTableIfNotExists();
    }

    private void createLoginLogTableIfNotExists() {
        try {
            String checkSql = """
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_NAME = 'login_logs_51'
                    """;
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class);

            if (count == null || count == 0) {
                String createSql = """
                        CREATE TABLE login_logs_51 (
                            log_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
                            emp_id      BIGINT,
                            login_input VARCHAR(100) NOT NULL,
                            result_code VARCHAR(20)  NOT NULL,
                            ip_address  VARCHAR(45),
                            user_agent  NVARCHAR(500),
                            created_at  DATETIME DEFAULT GETDATE()
                        )
                        """;
                jdbcTemplate.execute(createSql);
                log.info("[DatabaseInitializer] login_logs_51 테이블이 생성되었습니다.");
            } else {
                log.debug("[DatabaseInitializer] login_logs_51 테이블이 이미 존재합니다.");
            }
        } catch (Exception e) {
            log.error("[DatabaseInitializer] login_logs_51 테이블 생성 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
