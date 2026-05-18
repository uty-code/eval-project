package com.ees.eval.controller;

import com.ees.eval.domain.Employee;
import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.FinalGradeTaskDTO;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.mapper.FinalGradeMapper;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 최종 등급 확정 컨트롤러 TDD 단위 테스트 클래스
 */
@ExtendWith(MockitoExtension.class)
class FinalGradeControllerTest {

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
    private EvaluatorMappingMapper evaluatorMappingMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private DepartmentMapper departmentMapper;
    @Mock
    private FinalGradeService finalGradeService;
    @Mock
    private FinalGradeMapper finalGradeMapper;
    @Mock
    private ScoreCalculationService scoreCalculationService;

    @InjectMocks
    private FinalGradeController finalGradeController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(finalGradeController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        UserDetails userDetails = new User("1001", "password", Collections.emptyList());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("should_sortPeriodsWithInProgressFirstAndNewestYearDescending_when_retrievingPeriods")
    void should_sortPeriodsWithInProgressFirstAndNewestYearDescending_when_retrievingPeriods() throws Exception {
        // given
        EvaluationPeriodDTO p1 = EvaluationPeriodDTO.builder().periodId(1L).periodName("2024년 1차").periodYear(2024).statusCode("COMPLETED").build();
        EvaluationPeriodDTO p2 = EvaluationPeriodDTO.builder().periodId(2L).periodName("2025년 1차").periodYear(2025).statusCode("IN_PROGRESS").build();
        EvaluationPeriodDTO p3 = EvaluationPeriodDTO.builder().periodId(3L).periodName("2026년 1차").periodYear(2026).statusCode("PLANNED").build();
        
        List<EvaluationPeriodDTO> periods = new ArrayList<>(List.of(p1, p2, p3));
        given(periodService.getAllPeriods()).willReturn(periods);
        given(periodService.resolveSelectedPeriod(any(), any())).willReturn(p2);
        given(departmentMapper.findAll()).willReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(get("/eval/final-grade"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("periods"))
                .andExpect(model().attribute("periods", org.hamcrest.Matchers.contains(p2, p3, p1)));
    }
}
