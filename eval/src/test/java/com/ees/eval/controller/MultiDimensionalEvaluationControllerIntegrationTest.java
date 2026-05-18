package com.ees.eval.controller;

import com.ees.eval.domain.EvaluationPeriod;
import com.ees.eval.mapper.EvaluationPeriodMapper;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MultiDimensionalEvaluationController의 실물 통합 테스트 클래스입니다.
 * 실제 MSSQL 컨테이너 환경에서 Thymeleaf 템플릿 엔진 렌더링 결과와 HTML 마크업의 회귀 안정성을 검증합니다.
 */
@SpringBootTest
@Transactional
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class MultiDimensionalEvaluationControllerIntegrationTest extends AbstractMssqlTest {

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

    @Autowired
    private EvaluationPeriodMapper periodMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("통합 테스트 - 다면평가 계획 차수 조회 시, 실물 HTML 내부에 검색 필터 Form과 다면평가 준비 중 카드 배너가 동시에 공존하여 노출되는지 검증한다")
    @WithMockUser(username = "1000", roles = "ADMIN")
    void should_renderFilterAndBannerTogether_when_periodIsPlanned_Integration() throws Exception {
        // 1. Given - 데이터베이스에 PLANNED 상태의 다면평가 차수 직접 영속화
        EvaluationPeriod period = EvaluationPeriod.builder()
                .periodYear(2027)
                .periodName("2027년 하반기 다면평가")
                .statusCode("PLANNED")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(10))
                .build();
        period.setIsDeleted("n");
        period.setVersion(0);
        period.setCreatedBy(1L);
        period.setCreatedAt(LocalDateTime.now());
        period.setUpdatedBy(1L);
        period.setUpdatedAt(LocalDateTime.now());

        periodMapper.insert(period);

        // 2. When & Then - 실제 다면평가 목록 요청 시 HTML 렌더링 마크업 검사
        mockMvc.perform(get("/eval/multi-dimensional")
                        .param("periodId", period.getPeriodId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(view().name("eval/multi-dimensional/list"))
                // 3. departments 부서 드롭다운 정보가 전달되었는지 확인 (NPE 방어)
                .andExpect(model().attributeExists("departments"))
                // 4. HTML 본문 내 id="filterForm" 과 "다면평가 준비 중" 텍스트의 동시 공존 강력 검증
                .andExpect(content().string(containsString("id=\"filterForm\"")))
                .andExpect(content().string(containsString("다면평가 준비 중")));
    }
}
