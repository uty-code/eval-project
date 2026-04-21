package com.ees.eval.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;

/**
 * Testcontainers를 사용하여 실제 MSSQL 환경에서 모든 데이터베이스 테스트를 수행하는 베이스 클래스입니다.
 * 전체 테스트 실행 동안 단 하나의 컨테이너만 기동하여 공유하는 '싱글톤 컨테이너 패턴'을 적용합니다.
 */
public abstract class AbstractMssqlTest {

    // 정적 필드로 컨테이너 정의
    protected static final MSSQLServerContainer<?> MSSQL_CONTAINER;

    static {
        // 컨테이너 설정 및 수동 기동
        MSSQL_CONTAINER = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                .acceptLicense();
        MSSQL_CONTAINER.start();
    }

    @DynamicPropertySource
    static void setDatasourceProperties(DynamicPropertyRegistry registry) {
        // MSSQL 접속 정보 바인딩
        registry.add("spring.datasource.url", MSSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MSSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MSSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");

        // HikariCP 최적화
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "30000");

        // 모든 테스트에서 운영 스키마/데이터를 자동으로 초기화하도록 설정
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:eval-schema.sql");
        registry.add("spring.sql.init.data-locations", () -> "classpath:eval-data.sql");
        registry.add("spring.sql.init.encoding", () -> "UTF-8");
        
        // H2 자동 구성을 방지하기 위해 드라이버를 명시적으로 고정
        registry.add("spring.datasource.type", () -> "com.zaxxer.hikari.HikariDataSource");
    }
}
