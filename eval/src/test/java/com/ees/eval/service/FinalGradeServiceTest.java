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

    @Test
    @DisplayName("should_produce_identical_scores_and_grades_for_both_views")
    void should_produce_identical_scores_and_grades_for_both_views() {
        // given
        EvaluatorMapping task = new EvaluatorMapping();
        task.setMappingId(mappingId);
        task.setPeriodId(periodId);
        task.setEvaluateeId(evaluateeId);
        task.setEvaluateeName("홍길동");
        task.setDeptName("개발팀");
        task.setRelationTypeCode("EXECUTIVE");

        given(mappingMapper.findByEvaluatorId(eq(periodId), eq(executiveId), anyString())).willReturn(List.of(task));
        given(mappingMapper.findAllByPeriodIdAndRelationType(eq(periodId), anyString())).willReturn(List.of(task));
        
        Employee evaluatee = new Employee();
        evaluatee.setEmpId(evaluateeId);
        evaluatee.setDeptId(10L);
        evaluatee.setDeptName("개발팀");
        given(employeeMapper.findByIds(anyList())).willReturn(List.of(evaluatee));

        given(mappingMapper.findByEvaluateeIds(eq(periodId), anyList())).willReturn(List.of(task));
        given(departmentMapper.findAll()).willReturn(Collections.emptyList());
        given(evaluationMapper.findByMappingIds(anyList())).willReturn(Collections.emptyList());
        given(elementService.getElementsByPeriodId(any(), any())).willReturn(Collections.emptyList());
        given(typeWeightService.isWeightSumValid(any(), any(), anyString())).willReturn(true);
        given(finalGradeMapper.findByPeriodId(anyLong())).willReturn(Collections.emptyList());

        // when
        List<FinalGradeTaskDTO> executiveResult = finalGradeService.getFinalGradeTasks(executiveId, new FinalGradeSearchCondition(periodId, null, null, null, null));
        List<FinalGradeTaskDTO> adminResult = finalGradeService.getAdminFinalGradeTasks(new FinalGradeSearchCondition(periodId, null, null, null, null));

        // then
        assertThat(executiveResult).hasSize(1);
        assertThat(adminResult).hasSize(1);

        FinalGradeTaskDTO execDto = executiveResult.get(0);
        FinalGradeTaskDTO adminDto = adminResult.get(0);

        assertThat(execDto.empId()).isEqualTo(adminDto.empId());
        assertThat(execDto.totalScore()).isEqualTo(adminDto.totalScore());
        assertThat(execDto.expectedGrade()).isEqualTo(adminDto.expectedGrade());
        assertThat(execDto.selfPerfScore()).isEqualTo(adminDto.selfPerfScore());
        assertThat(execDto.managerPerfScore()).isEqualTo(adminDto.managerPerfScore());
        assertThat(execDto.executivePerfScore()).isEqualTo(adminDto.executivePerfScore());
    }

    @Test
    @DisplayName("should_calculate_correct_scores_based_on_leader_flag")
    void should_calculate_correct_scores_based_on_leader_flag() {
        // given
        EvaluatorMapping leaderTask = new EvaluatorMapping();
        leaderTask.setMappingId(mappingId);
        leaderTask.setPeriodId(periodId);
        leaderTask.setEvaluateeId(evaluateeId);
        leaderTask.setEvaluateeName("리더길동");
        leaderTask.setRelationTypeCode("EXECUTIVE");

        Employee leaderEmp = new Employee();
        leaderEmp.setEmpId(evaluateeId);
        leaderEmp.setDeptId(10L);
        leaderEmp.setDeptName("개발팀");

        com.ees.eval.domain.Department dept = new com.ees.eval.domain.Department();
        dept.setDeptId(10L);
        dept.setLeaderId(evaluateeId); // leaderId 지정하여 리더로 만듦

        given(mappingMapper.findByEvaluatorId(eq(periodId), eq(executiveId), anyString())).willReturn(List.of(leaderTask));
        given(employeeMapper.findByIds(anyList())).willReturn(List.of(leaderEmp));
        given(mappingMapper.findByEvaluateeIds(eq(periodId), anyList())).willReturn(List.of(leaderTask));
        given(departmentMapper.findAll()).willReturn(List.of(dept));
        given(evaluationMapper.findByMappingIds(anyList())).willReturn(Collections.emptyList());
        given(elementService.getElementsByPeriodId(any(), any())).willReturn(Collections.emptyList());
        given(typeWeightService.isWeightSumValid(any(), any(), anyString())).willReturn(true);
        given(finalGradeMapper.findByPeriodId(anyLong())).willReturn(Collections.emptyList());

        // when
        List<FinalGradeTaskDTO> result = finalGradeService.getFinalGradeTasks(executiveId, new FinalGradeSearchCondition(periodId, null, null, null, null));

        // then
        assertThat(result).hasSize(1);
        FinalGradeTaskDTO dto = result.get(0);
        assertThat(dto.isLeader()).isTrue();
        assertThat(dto.selfPerfScore()).isNull(); // 리더는 성과/역량 점수가 null이어야 함
        assertThat(dto.selfCompScore()).isNull();
    }

    @Test
    @DisplayName("should_flag_weightValid_false_when_sum_is_invalid")
    void should_flag_weightValid_false_when_sum_is_invalid() {
        // given
        EvaluatorMapping task = new EvaluatorMapping();
        task.setMappingId(mappingId);
        task.setPeriodId(periodId);
        task.setEvaluateeId(evaluateeId);
        task.setRelationTypeCode("EXECUTIVE");

        Employee employee = new Employee();
        employee.setEmpId(evaluateeId);
        employee.setDeptId(10L);

        given(mappingMapper.findByEvaluatorId(eq(periodId), eq(executiveId), anyString())).willReturn(List.of(task));
        given(employeeMapper.findByIds(anyList())).willReturn(List.of(employee));
        given(mappingMapper.findByEvaluateeIds(eq(periodId), anyList())).willReturn(List.of(task));
        given(departmentMapper.findAll()).willReturn(Collections.emptyList());
        given(evaluationMapper.findByMappingIds(anyList())).willReturn(Collections.emptyList());
        given(elementService.getElementsByPeriodId(any(), any())).willReturn(Collections.emptyList());
        
        // 가중치 합이 유효하지 않은 상황 모킹
        given(typeWeightService.isWeightSumValid(any(), any(), anyString())).willReturn(false);
        given(finalGradeMapper.findByPeriodId(anyLong())).willReturn(Collections.emptyList());

        // when
        List<FinalGradeTaskDTO> result = finalGradeService.getFinalGradeTasks(executiveId, new FinalGradeSearchCondition(periodId, null, null, null, null));

        // then
        assertThat(result).hasSize(1);
        FinalGradeTaskDTO dto = result.get(0);
        assertThat(dto.weightValid()).isFalse(); // 가중치 유효성 플래그가 false여야 함
    }

    @Test
    @DisplayName("should_distribute_remaining_grades_correctly_when_TO_overflow")
    void should_distribute_remaining_grades_correctly_when_TO_overflow() {
        // given
        Long empId1 = 2001L;
        Long empId2 = 2002L;

        EvaluatorMapping task1 = new EvaluatorMapping();
        task1.setMappingId(501L);
        task1.setPeriodId(periodId);
        task1.setEvaluateeId(empId1);
        task1.setEvaluateeName("사원A");
        task1.setRelationTypeCode("EXECUTIVE");

        EvaluatorMapping task2 = new EvaluatorMapping();
        task2.setMappingId(502L);
        task2.setPeriodId(periodId);
        task2.setEvaluateeId(empId2);
        task2.setEvaluateeName("사원B");
        task2.setRelationTypeCode("EXECUTIVE");

        // 임원의 조회 결과에는 두 사원 모두 포함됨
        given(mappingMapper.findByEvaluatorId(eq(periodId), eq(executiveId), anyString())).willReturn(List.of(task1, task2));

        Employee emp1 = new Employee();
        emp1.setEmpId(empId1);
        emp1.setDeptId(10L);
        Employee emp2 = new Employee();
        emp2.setEmpId(empId2);
        emp2.setDeptId(10L);

        // findByIds 및 findByDeptId 모킹
        given(employeeMapper.findByIds(anyList())).willReturn(List.of(emp1, emp2));
        given(employeeMapper.findByDeptId(eq(10L))).willReturn(List.of(emp1, emp2));
        given(mappingMapper.findByEvaluateeIds(eq(periodId), anyList())).willReturn(List.of(task1, task2));
        given(departmentMapper.findAll()).willReturn(Collections.emptyList());
        given(evaluationMapper.findByMappingIds(anyList())).willReturn(Collections.emptyList());
        given(elementService.getElementsByPeriodId(any(), any())).willReturn(Collections.emptyList());
        given(typeWeightService.isWeightSumValid(any(), any(), anyString())).willReturn(true);

        // 두 명의 사원의 FinalGrade 모킹: 종합 점수 제공 (사원A = 90, 사원B = 80)
        com.ees.eval.domain.FinalGrade fg1 = new com.ees.eval.domain.FinalGrade();
        fg1.setPeriodId(periodId);
        fg1.setEmpId(empId1);
        fg1.setTotalScore(90);
        
        com.ees.eval.domain.FinalGrade fg2 = new com.ees.eval.domain.FinalGrade();
        fg2.setPeriodId(periodId);
        fg2.setEmpId(empId2);
        fg2.setTotalScore(80);
        
        given(finalGradeMapper.findByPeriodId(eq(periodId))).willReturn(List.of(fg1, fg2));

        // 등급 비율 모킹: S=50%, A=50%
        com.ees.eval.dto.EvaluationGradeRatioDTO ratio = new com.ees.eval.dto.EvaluationGradeRatioDTO(
                1L, periodId, 10L, 50, 50, 0, 0, 0
        );
        java.util.Map<Long, com.ees.eval.dto.EvaluationGradeRatioDTO> ratioMap = java.util.Map.of(10L, ratio);
        given(gradeRatioService.getAllRatiosByPeriodMap(eq(periodId))).willReturn(ratioMap);
        given(gradeRatioService.getGradeRatioFromMap(any(), eq(periodId), eq(10L))).willReturn(ratio);

        // when
        List<FinalGradeTaskDTO> result = finalGradeService.getFinalGradeTasks(executiveId, new FinalGradeSearchCondition(periodId, null, null, null, null));

        // then
        assertThat(result).hasSize(2);
        
        FinalGradeTaskDTO dtoA = result.stream().filter(d -> d.empId().equals(empId1)).findFirst().orElseThrow();
        FinalGradeTaskDTO dtoB = result.stream().filter(d -> d.empId().equals(empId2)).findFirst().orElseThrow();

        // 90점을 획득한 사원A가 S 등급, 80점을 획득한 사원B가 A 등급을 배분받았는지 검증
        assertThat(dtoA.expectedGrade()).isEqualTo("S");
        assertThat(dtoB.expectedGrade()).isEqualTo("A");
    }
}
