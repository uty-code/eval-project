package com.ees.eval.controller;

import com.ees.eval.domain.Employee;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.dto.MultiDimensionalEvalPageDTO;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MultiDimensionalEvaluationControllerTest {

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
    private EvaluationService evaluationService;

    @Mock
    private EvaluationMapper evaluationMapper;

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private DepartmentService departmentService;

    @InjectMocks
    private MultiDimensionalEvaluationController multiDimensionalEvaluationController;

    @Mock
    private com.ees.eval.support.ui.EvalFilterConfigFactory filterConfigFactory;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(filterConfigFactory.createMultiDimensionalConfig(any(), any(), any(), any()))
                .thenReturn(com.ees.eval.dto.EvalFilterConfig.builder().build());

        mockMvc = MockMvcBuilders.standaloneSetup(multiDimensionalEvaluationController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        UserDetails userDetails = new User("1001", "password", Collections.emptyList());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("should_방어적부서목록바인딩_when_다면평가계획차수조회시")
    void should_bindEmptyDepartmentsDefensively_when_periodIsPlanned() throws Exception {
        // given
        Long periodId = 10L;
        Long empId = 1001L;

        EvaluationPeriodDTO period = EvaluationPeriodDTO.builder()
                .periodId(periodId)
                .statusCode("PLANNED")
                .periodName("2026년 다면평가 차수")
                .periodYear(2026)
                .build();

        given(periodService.getAllPeriods()).willReturn(Collections.singletonList(period));
        given(periodService.isPeriodActive(periodId)).willReturn(false);

        MultiDimensionalEvalPageDTO mockPageData = new MultiDimensionalEvalPageDTO(
                Collections.emptyList(), 1, 1, 0, 10
        );
        given(mappingService.getMultiDimensionalTasks(eq(periodId), eq(empId), any(), any(), any(), anyInt(), anyInt(), anyBoolean()))
                .willReturn(mockPageData);

        // myTasks는 비어있도록 설정하여 evaluateeIds를 빈 목록으로 만듦
        given(departmentService.getAllDepartments()).willReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(get("/eval/multi-dimensional")
                        .param("periodId", periodId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("eval/multi-dimensional/list"))
                .andExpect(model().attributeExists("departments"))
                .andExpect(model().attribute("departments", Collections.emptyList()));
    }

    @Test
    @DisplayName("should_다면평가대상아님오류리다이렉트_when_관계가_SUBORDINATE가아닌경우")
    void should_redirectWithError_when_relationIsNotSubordinate() throws Exception {
        // given
        Long mappingId = 5L;
        EvaluatorMappingDTO mockMapping = EvaluatorMappingDTO.builder()
                .mappingId(mappingId)
                .periodId(10L)
                .evaluateeId(2002L)
                .relationTypeCode("MANAGER") // 다면평가는 SUBORDINATE여야 함
                .build();

        given(mappingService.getMappingById(mappingId)).willReturn(mockMapping);

        // when & then
        mockMvc.perform(get("/eval/multi-dimensional/form")
                        .param("mappingId", mappingId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/eval/multi-dimensional"))
                .andExpect(flash().attribute("errorMessage", "다면평가 대상이 아닙니다."));
    }

    @Test
    @DisplayName("should_제출불가오류리다이렉트_when_평가차수기간이비활성상태일때")
    void should_redirectWithErrorOnSubmit_when_periodIsInactive() throws Exception {
        // given
        Long mappingId = 5L;
        EvaluatorMappingDTO mockMapping = EvaluatorMappingDTO.builder()
                .mappingId(mappingId)
                .periodId(10L)
                .evaluateeId(2002L)
                .build();

        given(mappingService.getMappingById(mappingId)).willReturn(mockMapping);
        given(periodService.isPeriodActive(10L)).willReturn(false); // 비활성화 상태

        // when & then
        mockMvc.perform(post("/eval/multi-dimensional/submit")
                        .param("mappingId", mappingId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/eval/multi-dimensional?periodId=10"))
                .andExpect(flash().attribute("errorMessage", "평가 기간이 종료되어 제출할 수 없습니다."));
    }

    @Test
    @DisplayName("should_IN_PROGRESS차수가맨위에오고나머지는연도최신순정렬_when_전체차수조회시")
    void should_sortPeriodsWithInProgressFirstAndNewestYearDescending() throws Exception {
        // given
        Long empId = 1001L;

        EvaluationPeriodDTO p1 = EvaluationPeriodDTO.builder().periodId(1L).statusCode("COMPLETED").periodYear(2025).periodName("2025 COMP").build();
        EvaluationPeriodDTO p2 = EvaluationPeriodDTO.builder().periodId(2L).statusCode("IN_PROGRESS").periodYear(2026).periodName("2026 INPR").build();
        EvaluationPeriodDTO p3 = EvaluationPeriodDTO.builder().periodId(3L).statusCode("PLANNED").periodYear(2027).periodName("2027 PLAN").build();

        List<EvaluationPeriodDTO> rawPeriods = List.of(p1, p2, p3);

        given(periodService.getAllPeriods()).willReturn(rawPeriods);
        given(periodService.isPeriodActive(anyLong())).willReturn(false);

        MultiDimensionalEvalPageDTO mockPageData = new MultiDimensionalEvalPageDTO(
                Collections.emptyList(), 1, 1, 0, 10
        );
        given(mappingService.getMultiDimensionalTasks(any(), eq(empId), any(), any(), any(), anyInt(), anyInt(), anyBoolean()))
                .willReturn(mockPageData);
        given(departmentService.getAllDepartments()).willReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(get("/eval/multi-dimensional")
                        .param("periodId", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("periods"))
                .andExpect(result -> {
                    List<EvaluationPeriodDTO> sorted = (List<EvaluationPeriodDTO>) result.getModelAndView().getModel().get("periods");
                    // 정렬 기대 순서:
                    // 1. IN_PROGRESS 상태인 p2 (2026) -> 맨 앞
                    // 2. 그 외 연도 내림차순: p3 (2027) -> p1 (2025)
                    // 최종 순서: p2, p3, p1
                    org.junit.jupiter.api.Assertions.assertEquals(2L, sorted.get(0).periodId());
                    org.junit.jupiter.api.Assertions.assertEquals(3L, sorted.get(1).periodId());
                    org.junit.jupiter.api.Assertions.assertEquals(1L, sorted.get(2).periodId());
                });
    }
}
