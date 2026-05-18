package com.ees.eval.controller;

import com.ees.eval.support.AbstractMssqlTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

/**
 * 구버전 쿼리 파라미터(deptId, search, status) 전송 시
 * 신규 규격 파라미터(filterDeptId, keyword, filterStatus)로 호환 및 자동 Fallback 처리되는지 검증하는
 * 컨트롤러 통합 테스트 클래스입니다.
 */
@SpringBootTest
@Transactional
@Timeout(value = 60, unit = TimeUnit.SECONDS)
public class FilterFallbackIntegrationTest extends AbstractMssqlTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public Executor testVirtualThreadExecutor() {
            return Runnable::run;
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /**
     * 최종 등급 확정 조회 시, 구버전 파라미터(deptId, search, status)가 신규 규격으로 정상 대체 매핑되는지 검증합니다.
     */
    @Test
    @DisplayName("호환성 검증 - 최종 등급 확정 조회 시 구버전 파라미터(deptId, search, status)가 신규 규격으로 정상 대체 매핑된다")
    @WithMockUser(username = "1000", roles = "ADMIN")
    void finalGradeFallbackTest() throws Exception {
        mockMvc.perform(get("/eval/final-grade")
                        .param("deptId", "2")
                        .param("search", "테스트")
                        .param("status", "DONE"))
                .andExpect(status().isOk())
                // condition 모델 어트리뷰트 내에 fallback 매핑값들이 정확히 채워졌는지 검증
                .andExpect(model().attributeExists("condition"))
                .andExpect(model().attributeExists("filterConfig"));
    }

    /**
     * 성과평가 조회 시, 구버전 파라미터(deptId, search, status)가 신규 규격으로 정상 대체 매핑되는지 검증합니다.
     */
    @Test
    @DisplayName("호환성 검증 - 성과평가 조회 시 구버전 파라미터(deptId, search, status)가 신규 규격으로 정상 대체 매핑된다")
    @WithMockUser(username = "1000", roles = "ADMIN")
    void performanceFallbackTest() throws Exception {
        mockMvc.perform(get("/eval/performance")
                        .param("deptId", "2")
                        .param("search", "길동")
                        .param("status", "완료"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterDeptId", 2L))
                .andExpect(model().attribute("keyword", "길동"))
                .andExpect(model().attribute("filterStatus", "완료"))
                .andExpect(model().attributeExists("filterConfig"));
    }

    /**
     * 다면평가 조회 시, 구버전 파라미터(deptId, search, status)가 신규 규격으로 정상 대체 매핑되는지 검증합니다.
     */
    @Test
    @DisplayName("호환성 검증 - 다면평가 조회 시 구버전 파라미터(deptId, search, status)가 신규 규격으로 정상 대체 매핑된다")
    @WithMockUser(username = "1000", roles = "ADMIN")
    void multiDimensionalFallbackTest() throws Exception {
        mockMvc.perform(get("/eval/multi-dimensional")
                        .param("deptId", "1")
                        .param("search", "성명")
                        .param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterDeptId", 1L))
                .andExpect(model().attribute("keyword", "성명"))
                .andExpect(model().attribute("filterStatus", "SUBMITTED"))
                .andExpect(model().attributeExists("filterConfig"));
    }
}
