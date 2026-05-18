package com.ees.eval.controller;

import com.ees.eval.dto.DashboardStatsDTO;
import com.ees.eval.dto.EmployeeDashboardDTO;
import com.ees.eval.service.DashboardService;
import com.ees.eval.service.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.util.Collections;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MainController의 대시보드 화면 연동 및 접근 제어 테스트입니다.
 */
@ExtendWith(MockitoExtension.class)
class MainControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private MainController mainController;

    private UserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/WEB-INF/views/");
        viewResolver.setSuffix(".html");

        HandlerMethodArgumentResolver mockPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(UserDetails.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
                return mockUserDetails;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(mainController)
                .setViewResolvers(viewResolver)
                .setCustomArgumentResolvers(mockPrincipalResolver)
                .build();
    }

    @Test
    @DisplayName("대시보드 - 관리자(ROLE_ADMIN) 권한 사용자는 어드민 대시보드를 본다")
    void dashboard_ShouldShowAdminDashboardForManagementUsers() throws Exception {
        // given
        mockUserDetails = User.withUsername("1")
                .password("password")
                .authorities("ROLE_ADMIN")
                .build();

        DashboardStatsDTO mockStats = DashboardStatsDTO.builder()
                .employeeCount(100L)
                .departmentCount(12L)
                .activePeriodName("2026년 상반기")
                .totalEvaluatees(100)
                .finalizedCount(50)
                .completionRate(50.0)
                .gradeDistribution(Collections.emptyMap())
                .deptAverageScores(Collections.emptyMap())
                .recentActivities(Collections.emptyList())
                .build();

        given(dashboardService.getDashboardStats()).willReturn(mockStats);
        given(employeeService.getTop5RecentEmployees()).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("employeeCount", 100L))
                .andExpect(model().attribute("departmentCount", 12L))
                .andExpect(model().attribute("activePeriodName", "2026년 상반기"))
                .andExpect(model().attribute("completionRate", 50L))
                .andExpect(model().attribute("welcomeMessage", "사원 평가 시스템(EES) 관리자 페이지에 오신 것을 환영합니다."));
    }

    @Test
    @DisplayName("대시보드 - 일반 사원은 본인 대시보드(dashboard_emp)를 본다")
    void dashboard_ShouldShowEmployeeDashboardForRegularUsers() throws Exception {
        // given
        mockUserDetails = User.withUsername("123")
                .password("password")
                .authorities("ROLE_STAFF")
                .build();

        EmployeeDashboardDTO mockEmpStats = EmployeeDashboardDTO.builder()
                .activePeriodName("2026년 상반기")
                .dDay(10L)
                .selfEvalStatus("IN_PROGRESS")
                .pendingPeerEvals(2)
                .totalPeerEvals(5)
                .myRecentGrades(Collections.emptyList())
                .build();

        given(dashboardService.getEmployeeDashboardStats(123L)).willReturn(mockEmpStats);

        // when & then
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard_emp"))
                .andExpect(model().attribute("welcomeMessage", "123님, 오늘도 좋은 하루 되세요!"))
                .andExpect(model().attributeExists("empStats"));
    }

    @Test
    @DisplayName("대시보드 - 인증 정보가 없으면 로그인 페이지로 리다이렉트된다")
    void dashboard_ShouldRedirectToLoginWhenNotAuthenticated() throws Exception {
        // given
        mockUserDetails = null;

        // when & then
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
