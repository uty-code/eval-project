package com.ees.eval.controller;

import com.ees.eval.service.MyEvaluationFacadeService;
import com.ees.eval.support.ui.EvalFilterConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MyEvaluationController의 Spring Security 인가(@PreAuthorize) 정책을 검증하는 슬라이스 테스트 클래스입니다.
 * DB 연결 없이 컨트롤러와 시큐리티 설정만을 로드하여 작동합니다.
 */
@WebMvcTest(controllers = MyEvaluationController.class)
@Import({
        MyEvaluationControllerSecurityTest.SecurityTestConfig.class,
        org.springframework.boot.autoconfigure.aop.AopAutoConfiguration.class
})
class MyEvaluationControllerSecurityTest {

    /**
     * 테스트용 Spring Security 설정을 정의하는 정적 설정 클래스입니다.
     * HTTP 요청은 모두 허용하며, 메소드 보안(@PreAuthorize) 검증만 활성화합니다.
     */
    @org.springframework.boot.test.context.TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class SecurityTestConfig {
        /**
         * HTTP 보안 필터 체인을 생성합니다.
         * CSRF를 비활성화하고 모든 HTTP 요청을 허용하도록 설정합니다.
         *
         * @param http HttpSecurity 설정 객체
         * @return 구성된 SecurityFilterChain
         * @throws Exception 설정 에러 발생 시
         */
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MyEvaluationFacadeService myEvaluationFacadeService;

    @MockitoBean
    private com.ees.eval.service.EmployeeService employeeService;

    @MockitoBean
    private EvalFilterConfigFactory filterConfigFactory;

    /**
     * 각 테스트 수행 전에 호출되어 목(Mock) 객체의 동작을 정의합니다.
     */
    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(filterConfigFactory.createMyEvalConfig(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(com.ees.eval.dto.EvalFilterConfig.builder()
                        .actionUrl("/eval/my-evaluation")
                        .build());

        org.mockito.Mockito.lenient()
                .when(myEvaluationFacadeService.getDashboardData(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(java.util.Collections.emptyMap());

        org.mockito.Mockito.lenient()
                .when(myEvaluationFacadeService.getAdminDashboardData(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(java.util.Collections.emptyMap());
    }

    /**
     * 임원 권한(ROLE_EXECUTIVE)을 가진 사용자가 자가평가 페이지 접근 시 403 에러가 나는지 검증합니다.
     *
     * @throws Exception MockMvc 수행 시 발생할 수 있는 예외
     */
    @Test
    @DisplayName("임원 계정(ROLE_EXECUTIVE)으로 자가평가 페이지(/eval/my-evaluation) 접근 시 403 Forbidden 에러가 발생해야 한다")
    @WithMockUser(username = "1002", roles = {"EXECUTIVE"})
    void should_denyAccess_when_userIsExecutive() throws Exception {
        mockMvc.perform(get("/eval/my-evaluation"))
                .andExpect(status().isForbidden());
    }

    /**
     * 사원 권한(ROLE_USER)을 가진 사용자가 자가평가 페이지 접근 시 200 OK를 반환하는지 검증합니다.
     *
     * @throws Exception MockMvc 수행 시 발생할 수 있는 예외
     */
    @Test
    @DisplayName("일반 사원 계정(ROLE_USER)으로 자가평가 페이지(/eval/my-evaluation) 접근 시 200 OK를 반환해야 한다")
    @WithMockUser(username = "1003", roles = {"USER"})
    void should_allowAccess_when_userIsStaff() throws Exception {
        mockMvc.perform(get("/eval/my-evaluation"))
                .andExpect(status().isOk());
    }

    /**
     * 관리자 권한(ROLE_ADMIN)을 가진 사용자가 자가평가 페이지 접근 시 200 OK를 반환하는지 검증합니다.
     *
     * @throws Exception MockMvc 수행 시 발생할 수 있는 예외
     */
    @Test
    @DisplayName("관리자 계정(ROLE_ADMIN)으로 자가평가 페이지(/eval/my-evaluation) 접근 시 200 OK를 반환해야 한다")
    @WithMockUser(username = "1004", roles = {"ADMIN"})
    void should_allowAccess_when_userIsAdmin() throws Exception {
        mockMvc.perform(get("/eval/my-evaluation"))
                .andExpect(status().isOk());
    }
}
