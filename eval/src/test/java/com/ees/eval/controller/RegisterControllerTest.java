package com.ees.eval.controller;

import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.dto.EmployeePageDTO;
import com.ees.eval.service.DepartmentService;
import com.ees.eval.service.EmployeeService;
import com.ees.eval.service.PositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RegisterController의 Standalone 테스트 클래스입니다.
 * 사원 자가 등록 신청 및 관리자의 승인/거절 처리 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class RegisterControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private DepartmentService departmentService;

    @Mock
    private PositionService positionService;

    @Mock(name = "virtualThreadExecutor")
    private Executor virtualThreadExecutor;

    @InjectMocks
    private RegisterController registerController;

    @BeforeEach
    void setUp() {
        // @AuthenticationPrincipal 처리를 위한 Mock ArgumentResolver 설정
        HandlerMethodArgumentResolver mockUserResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                // 테스트용 관리자 (ID: 1) 반환
                return new User("1", "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(registerController)
                .setCustomArgumentResolvers(mockUserResolver)
                .build();
    }

    @Test
    @DisplayName("등록 신청 성공 - 유효한 데이터를 입력하면 PENDING 상태로 신청 완료된다")
    void submitRegistration_Success_ShouldRedirect() throws Exception {
        // when & then
        mockMvc.perform(post("/register")
                        .param("name", "신규지원")
                        .param("email", "new@example.com")
                        .param("phone", "010-1234-5678")
                        .param("deptId", "1")
                        .param("positionId", "1")
                        .param("hireDate", "2024-05-01")
                        .param("password", "pass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attribute("successMessage", "등록 신청이 완료되었습니다! 관리자 승인 후 로그인이 가능합니다."));

        verify(employeeService).registerEmployee(any(EmployeeDTO.class), anyList());
    }

    @Test
    @DisplayName("등록 신청 실패 - 올바르지 않은 전화번호 양식 입력 시 에러를 반환한다")
    void submitRegistration_Fail_InvalidPhone_ShouldShowError() throws Exception {
        // when & then
        mockMvc.perform(post("/register")
                        .param("name", "지원자")
                        .param("email", "test@example.com")
                        .param("phone", "02-123-4567") // 010 양식 아님
                        .param("deptId", "1")
                        .param("positionId", "1")
                        .param("hireDate", "2024-05-01")
                        .param("password", "pass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "등록 신청 중 오류가 발생했습니다: 전화번호 양식이 올바르지 않습니다. (예: 010-1111-2222)"));
    }

    @Test
    @DisplayName("등록 신청 실패 - 이미 존재하는 이메일 등 중복 데이터 입력 시 에러를 반환한다")
    void submitRegistration_Fail_DuplicateData_ShouldShowError() throws Exception {
        // given
        doAnswer(invocation -> { throw new IllegalStateException("이미 등록된 이메일입니다."); })
                .when(employeeService).registerEmployee(any(EmployeeDTO.class), anyList());

        // when & then
        mockMvc.perform(post("/register")
                        .param("name", "중복자")
                        .param("email", "dup@example.com")
                        .param("phone", "010-9999-9999")
                        .param("deptId", "1")
                        .param("positionId", "1")
                        .param("hireDate", "2024-05-01")
                        .param("password", "pass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "등록 신청 중 오류가 발생했습니다: 이미 등록된 이메일입니다."));
    }

    @Test
    @DisplayName("승인 대기 목록 조회 - 병렬 통계 처리를 포함하여 대기 사원 목록을 반환한다")
    void pendingList_ShouldHandleParallelCountingAndReturnView() throws Exception {
        // given: 가상 스레드 Executor가 작업을 즉시 실행하도록 설정
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(virtualThreadExecutor).execute(any());

        given(employeeService.getPendingEmployees()).willReturn(List.of());
        given(employeeService.countActiveEmployees()).willReturn(10L);
        given(employeeService.countThisYearHired()).willReturn(5L);
        given(employeeService.countLockedEmployees()).willReturn(0L);
        
        EmployeePageDTO mockPage = EmployeePageDTO.of(List.of(), 1, 1, 100L);
        given(employeeService.searchEmployeesPage(null, null, null, 1, 1)).willReturn(mockPage);

        // when & then
        mockMvc.perform(get("/employees/pending"))
                .andExpect(status().isOk())
                .andExpect(view().name("employees/pending"))
                .andExpect(model().attribute("totalEmployeeCount", 100L))
                .andExpect(model().attribute("activeCount", 10L));
    }

    @Test
    @DisplayName("승인 대기 목록 조회 실패 - 비동기 작업 중 예외 발생 시 적절히 처리한다")
    void pendingList_Fail_ParallelTaskException_ShouldHandleError() throws Exception {
        // given: 가상 스레드 작업 중 하나에서 예외 발생 시뮬레이션
        doAnswer(invocation -> {
            throw new RuntimeException("DB 부하로 인한 조회 실패");
        }).when(virtualThreadExecutor).execute(any());

        // when & then
        try {
            mockMvc.perform(get("/employees/pending"));
        } catch (Exception e) {
            // join() 과정에서 발생한 예외가 전파되는지 확인
            assert e.getMessage().contains("DB 부하로 인한 조회 실패");
        }
    }

    @Test
    @DisplayName("등록 신청 승인 - 승인 완료 후 대기 목록으로 리다이렉트된다")
    void approveEmployee_ShouldRedirectWithSuccess() throws Exception {
        // when & then
        mockMvc.perform(post("/employees/1001/approve"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees/pending"))
                .andExpect(flash().attribute("successMessage", "사원 등록 신청이 승인되었습니다. (사번: 1001)"));

        verify(employeeService).approveEmployee(eq(1001L), eq(1L));
    }

    @Test
    @DisplayName("등록 신청 거절 - 거절 완료 후 대기 목록으로 리다이렉트된다")
    void rejectEmployee_ShouldRedirectWithSuccess() throws Exception {
        // when & then
        mockMvc.perform(post("/employees/1001/reject"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees/pending"))
                .andExpect(flash().attribute("successMessage", "사원 등록 신청이 거절되었습니다. (사번: 1001)"));

        verify(employeeService).rejectEmployee(eq(1001L), eq(1L));
    }
}
