package com.ees.eval.controller;

import com.ees.eval.domain.Employee;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationPeriodService;
import com.ees.eval.service.EvaluationTypeWeightService;
import com.ees.eval.service.EvaluatorMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.ees.eval.mapper.FinalGradeMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PerformanceEvaluationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EvaluationPeriodService periodService;

    @Mock
    private EvaluatorMappingService mappingService;

    @Mock
    private EvaluationElementService elementService;

    @Mock
    private EvaluationTypeWeightService typeWeightService;

    @Mock
    private EvaluationMapper evaluationMapper;

    @Mock
    private EvaluatorMappingMapper evaluatorMappingMapper;

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private com.ees.eval.service.DepartmentService departmentService;

    @Mock
    private com.ees.eval.support.ui.EvalFilterConfigFactory filterConfigFactory;

    @Mock
    private FinalGradeMapper finalGradeMapper;

    @InjectMocks
    private PerformanceEvaluationController performanceEvaluationController;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(filterConfigFactory.createPerformanceConfig(any(), any(), any(), any()))
                .thenReturn(com.ees.eval.dto.EvalFilterConfig.builder().build());

        org.mockito.Mockito.lenient()
                .when(departmentService.getAllDepartments())
                .thenReturn(Collections.emptyList());

        org.mockito.Mockito.lenient()
                .when(finalGradeMapper.findByPeriodIds(any()))
                .thenReturn(Collections.emptyList());

        mockMvc = MockMvcBuilders.standaloneSetup(performanceEvaluationController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        UserDetails userDetails = new User("1001", "password", Collections.emptyList());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("평가 차수가 PLANNED 상태일 때 성과/역량 평가 폼 진입 차단 테스트")
    void blockAccessWhenPeriodIsPlanned() throws Exception {
        // Given
        Long mappingId = 1L;
        Long periodId = 10L;

        EvaluatorMappingDTO mapping = EvaluatorMappingDTO.builder()
                .mappingId(mappingId)
                .periodId(periodId)
                .evaluateeId(2001L)
                .relationTypeCode("MANAGER")
                .build();

        EvaluationPeriodDTO period = EvaluationPeriodDTO.builder()
                .periodId(periodId)
                .statusCode("PLANNED")
                .build();

        given(mappingService.getMappingById(mappingId)).willReturn(mapping);
        given(periodService.getPeriodById(periodId)).willReturn(period);

        // When & Then
        mockMvc.perform(get("/eval/performance/form")
                        .param("mappingId", mappingId.toString())
                        .param("evalType", "PERFORMANCE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/eval/performance?periodId=" + periodId))
                .andExpect(flash().attribute("errorMessage", "평가 시작 전입니다. 평가 기간에 다시 접속해 주세요."));
    }

    @Test
    @DisplayName("평가 차수가 PLANNED 상태일 때 목록 페이지에 안내 메시지 표시 테스트")
    void showInfoMessageWhenPeriodIsPlanned() throws Exception {
        // Given
        Long periodId = 10L;
        Long empId = 1001L;
        EvaluationPeriodDTO period = EvaluationPeriodDTO.builder()
                .periodId(periodId)
                .statusCode("PLANNED")
                .periodName("2026년 상반기 평가")
                .periodYear(2026)
                .build();

        Employee emp = new Employee();
        emp.setEmpId(empId);
        emp.setDeptId(1L);

        given(periodService.getAllPeriods()).willReturn(Collections.singletonList(period));
        given(employeeMapper.findById(empId)).willReturn(Optional.of(emp));

        given(mappingService.getMyEvaluationTasks(periodId, empId)).willReturn(Collections.singletonList(
                EvaluatorMappingDTO.builder().periodId(periodId).evaluateeId(2001L).relationTypeCode("MANAGER").build()
        ));

        // 실행 및 검증
        mockMvc.perform(get("/eval/performance")
                        .param("periodId", periodId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("eval/performance/list"))
                .andExpect(model().attribute("infoMessage", "현재 평가 시작 전입니다. 정해진 평가 기간에만 작성이 가능합니다."));
    }

    @Test
    @DisplayName("should_필터창이출력되고준비중배너와공존한다_when_평가차수가_PLANNED_상태일때")
    void should_renderFilterAndBannerTogether_when_periodIsPlanned() throws Exception {
        // given - 데이터 정의
        Long periodId = 10L;
        Long empId = 1001L;
        EvaluationPeriodDTO period = EvaluationPeriodDTO.builder()
                .periodId(periodId)
                .statusCode("PLANNED")
                .periodName("2026년 상반기 평가")
                .periodYear(2026)
                .build();

        // mock - 모의 객체 동작 설정
        given(periodService.getAllPeriods()).willReturn(Collections.singletonList(period));
        // PLANNED 차수이므로 배정된 태스크는 없음
        given(mappingService.getMyEvaluationTasks(periodId, empId)).willReturn(Collections.emptyList());

        // when & then - 실행 및 검증
        mockMvc.perform(get("/eval/performance")
                        .param("periodId", periodId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("eval/performance/list"))
                // 1) departments 모델 속성의 존재 확인 (방어적 바인딩 검증)
                .andExpect(model().attributeExists("departments"))
                // 2) 선택된 차수가 바인딩되었는지 확인
                .andExpect(model().attribute("selectedPeriod", period));
    }
}
