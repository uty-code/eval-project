package com.ees.eval.support.ui;

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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 공통 평가 필터 바(eval-filter-bar)가 Thymeleaf와 HTMX를 통해
 * 각 화면 및 사용자 권한(어드민/일반사원)에 따라 올바른 구성 요소로 정상 렌더링되는지 검증하는 실물 마크업 회귀 테스트입니다.
 */
@SpringBootTest
@Transactional
@Timeout(value = 60, unit = TimeUnit.SECONDS)
public class EvalFilterBarRenderingTest extends AbstractMssqlTest {

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
     * 일반 사원이 '나의 평가'를 조회할 때 부서 필터는 가려지고, 자가평가 맞춤형 키워드 힌트가 출력되는지 검증합니다.
     */
    @Test
    @DisplayName("마크업 검증 - 일반 사원이 나의 평가 페이지를 열면 부서 필터는 숨겨지고 키워드 검색 힌트가 '차수 이름 입력'으로 출력된다")
    @WithMockUser(username = "1001", roles = "EMPLOYEE")
    void should_hideDeptFilterAndShowCustomPlaceholder_when_employeeAccessMyEval() throws Exception {
        mockMvc.perform(get("/eval/my-evaluation"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(view().name("eval/my-evaluation/list"))
                // 부서 필터 select 엘리먼트나 ID가 렌더링되지 않아야 함 (visible=false)
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("id=\"filterDeptId\""))))
                // 일반 사용자용 플레이스홀더 출력 검증
                .andExpect(content().string(containsString("placeholder=\"차수 이름 입력\"")));
    }

    /**
     * 관리자가 '나의 평가'를 조회할 때 부서 필터가 보이고, 성명/사번 검색 힌트가 출력되는지 검증합니다.
     */
    @Test
    @DisplayName("마크업 검증 - 관리자가 나의 평가 페이지를 열면 부서 필터가 활성화되고 키워드 검색 힌트가 '이름 또는 사번 입력'으로 출력된다")
    @WithMockUser(username = "1000", roles = "ADMIN")
    void should_showDeptFilterAndShowAdminPlaceholder_when_adminAccessMyEval() throws Exception {
        mockMvc.perform(get("/eval/my-evaluation"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(view().name("eval/my-evaluation/list"))
                // 어드민이므로 부서 필터가 노출되어야 함 (visible=true)
                .andExpect(content().string(containsString("name=\"filterDeptId\"")))
                // 어드민용 플레이스홀더 출력 검증
                .andExpect(content().string(containsString("placeholder=\"이름 또는 사번 입력\"")));
    }

    /**
     * '최종 등급 확정' 화면에서 탭(tab) 상태 정보가 hidden input으로 누락 없이 정확히 전달되는지 검증합니다.
     */
    @Test
    @DisplayName("마크업 검증 - 최종 등급 확정 화면 렌더링 시, activeTab 상태를 전송하기 위한 hidden 파라미터가 정상 포함된다")
    @WithMockUser(username = "1000", roles = "ADMIN")
    void should_renderTabHiddenParameter_when_accessFinalGrade() throws Exception {
        mockMvc.perform(get("/eval/final-grade")
                        .param("tab", "executive"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(view().name("eval/final-grade/list"))
                // hidden 파라미터로 tab=executive 가 렌더링되어 전송되는지 검증
                .andExpect(content().string(containsString("name=\"tab\"")))
                .andExpect(content().string(containsString("value=\"executive\"")));
    }
}
