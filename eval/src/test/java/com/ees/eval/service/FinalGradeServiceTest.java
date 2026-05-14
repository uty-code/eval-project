package com.ees.eval.service;

import com.ees.eval.domain.Employee;
import com.ees.eval.domain.Evaluation;
import com.ees.eval.domain.EvaluatorMapping;
import com.ees.eval.dto.FinalGradeSearchCondition;
import com.ees.eval.dto.FinalGradeTaskDTO;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.mapper.FinalGradeMapper;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationTypeWeightService;
import com.ees.eval.service.impl.FinalGradeServiceImpl;
import com.ees.eval.service.ScoreCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FinalGradeServiceTest {

    @Mock
    private EvaluatorMappingMapper mappingMapper;
    @Mock
    private EvaluationMapper evaluationMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private com.ees.eval.mapper.DepartmentMapper departmentMapper;
    @Mock
    private EvaluationElementService elementService;
    @Mock
    private EvaluationTypeWeightService typeWeightService;
    @Mock
    private FinalGradeMapper finalGradeMapper;
    @Mock
    private ScoreCalculationService scoreCalculationService;
    @Mock
    private com.ees.eval.service.EvaluationGradeRatioService gradeRatioService;

    @InjectMocks
    private FinalGradeServiceImpl finalGradeService;

    private final Long periodId = 1L;
    private final Long executiveId = 1000L;
    private final Long evaluateeId = 2000L;
    private final Long mappingId = 500L;

    @Test
    @DisplayName("should_return_empty_list_when_no_tasks")
    void should_return_empty_list_when_no_tasks() {
        // given
        given(mappingMapper.findByEvaluatorId(anyLong(), anyLong(), any())).willReturn(Collections.emptyList());

        // when
        List<FinalGradeTaskDTO> result = finalGradeService.getFinalGradeTasks(executiveId, new FinalGradeSearchCondition(periodId, null, null, null, null));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should_calculate_status_flags_correctly")
    void should_calculate_status_flags_correctly() {
        // given
        EvaluatorMapping task = new EvaluatorMapping();
        task.setMappingId(mappingId);
        task.setEvaluateeId(evaluateeId);
        task.setEvaluateeName("홍길동");
        task.setDeptName("개발팀");

        given(mappingMapper.findByEvaluatorId(eq(periodId), eq(executiveId), anyString())).willReturn(List.of(task));
        
        Employee evaluatee = new Employee();
        evaluatee.setEmpId(evaluateeId);
        evaluatee.setDeptId(10L);
        given(employeeMapper.findByIds(anyList())).willReturn(List.of(evaluatee));

        EvaluatorMapping selfMapping = new EvaluatorMapping();
        selfMapping.setMappingId(501L);
        selfMapping.setEvaluateeId(evaluateeId);
        selfMapping.setRelationTypeCode("SELF");
        given(mappingMapper.findByEvaluateeIds(eq(periodId), anyList())).willReturn(List.of(task, selfMapping));
        given(departmentMapper.findAll()).willReturn(Collections.emptyList());

        given(evaluationMapper.findByMappingIds(anyList())).willReturn(Collections.emptyList());
        given(elementService.getElementsByPeriodId(any(), any())).willReturn(Collections.emptyList());
        given(typeWeightService.isWeightSumValid(any(), any(), anyString())).willReturn(true);
        given(finalGradeMapper.findByPeriodId(anyLong())).willReturn(Collections.emptyList());

        // when
        List<FinalGradeTaskDTO> result = finalGradeService.getFinalGradeTasks(executiveId, new FinalGradeSearchCondition(periodId, null, null, null, null));

        // then
        assertThat(result).hasSize(1);
        FinalGradeTaskDTO dto = result.get(0);
        assertThat(dto.evaluateeName()).isEqualTo("홍길동");
        assertThat(dto.weightValid()).isTrue();
        assertThat(dto.selfSubmitted()).isFalse(); // 평가 데이터가 없으므로 false
        assertThat(dto.allSubmitted()).isFalse(); // 요소가 없으므로 false (로직상 elements.isEmpty() -> false)
    }
}
