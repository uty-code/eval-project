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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * EmployeeController의 통합 테스트 클래스입니다.
 * 실제 MSSQL 컨테이너 환경에서 컨트롤러-서비스-매퍼-DB 간의 실데이터 연동을 검증합니다.
 *
 * 내부 TestConfig에서 virtualThreadExecutor를 동기 실행으로 교체하여,
 * 테스트 환경에서의 커넥션 경합과 DB 락 문제를 원천 방지합니다.
 */
@SpringBootTest
@Transactional
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class EmployeeControllerIntegrationTest extends AbstractMssqlTest {

    /**
     * 테스트 전용 설정: virtualThreadExecutor를 동기 실행으로 교체합니다.
     * static inner class + @TestConfiguration이므로 프로덕션 빈을 확실히 오버라이드합니다.
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public Executor virtualThreadExecutor() {
            // 호출 스레드에서 즉시 실행 → 별도 스레드/커넥션 불필요
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

    @Test
    @DisplayName("통합 테스트 - 사원 등록 후 목록에서 실제 저장된 데이터가 조회되는지 확인한다")
    @WithMockUser(roles = "ADMIN")
    void createAndListEmployee_Integration() throws Exception {
        // 1. 사원 등록 요청 (CSRF 토큰 추가)
        mockMvc.perform(post("/register")
                        .param("name", "통합테스트")
                        .param("email", "integration@test.com")
                        .param("phone", "010-9999-8888")
                        .param("deptId", "1")
                        .param("positionId", "1")
                        .param("hireDate", "2024-01-01")
                        .param("password", "pass123")
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"));

        // 2. 관리자 권한으로 사원 목록 조회
        mockMvc.perform(get("/employees")
                        .param("searchName", "통합테스트"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("통합테스트")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("integration@test.com")));
    }

    @Test
    @DisplayName("통합 테스트 - 존재하지 않는 사원 상세 조회 시 에러 페이지가 표시되어야 한다")
    @WithMockUser(roles = "ADMIN")
    void viewNonExistentEmployee_ShouldShowError() throws Exception {
        // 존재하지 않는 ID로 접근 시 GlobalExceptionHandler가 에러 뷰를 반환하는지 확인
        mockMvc.perform(get("/employees/99999/edit"))
                .andDo(print())
                .andExpect(status().isOk()) // GlobalExceptionHandler가 200 + error 뷰 반환
                .andExpect(view().name("error/custom-error"));
    }

    @Test
    @DisplayName("통합 테스트 - 페이지네이션 파라미터가 서비스 및 뷰까지 정상 전달되는지 확인한다")
    @WithMockUser(roles = "ADMIN")
    void listEmployees_Pagination_Integration() throws Exception {
        mockMvc.perform(get("/employees")
                        .param("page", "1")
                        .param("searchDeptId", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("page"))
                .andExpect(model().attribute("searchDeptId", 1L))
                .andExpect(view().name("employees/list"));
    }

    @Test
    @DisplayName("통합 테스트 - 승인 대기 목록에서 HTMX 요청 시 리다이렉트 없이 프래그먼트를 반환한다")
    @WithMockUser(username = "1000", roles = "ADMIN")
    void approveEmployee_Htmx_ShouldNotRedirect() throws Exception {
        // 실제 데이터가 없더라도 컨트롤러의 HTMX 분기 로직이 작동하는지 확인
        // (존재하지 않는 empId인 경우 예외가 발생하더라도 HTMX 요청이면 에러 메시지와 함께 목록 뷰를 반환하도록 설계됨)
        mockMvc.perform(post("/employees/99999/approve")
                        .header("HX-Request", "true")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(view().name("employees/pending"));
    }
}
