package com.ees.eval.service;

import com.ees.eval.domain.Employee;
import com.ees.eval.domain.Evaluation;
import com.ees.eval.domain.EvaluatorMapping;
import com.ees.eval.domain.FinalGrade;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.EvaluationResultDTO;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.mapper.FinalGradeMapper;
import com.ees.eval.service.impl.EvaluationResultServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

/**
 * 평가 결과 현황 서비스 단위 테스트입니다.
 * TDD 기반으로 매핑 기반 조회, 1차/2차/최종 점수 산출 및 상태 판단 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationResultServiceTest {

    @Mock private FinalGradeMapper finalGradeMapper;
    @Mock private EvaluatorMappingMapper mappingMapper;
    @Mock private EvaluationMapper evaluationMapper;
    @Mock private EmployeeMapper employeeMapper;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private EvaluationElementService elementService;
    @Mock private ScoreCalculationService scoreCalculationService;

    @InjectMocks
    private EvaluationResultServiceImpl resultService;

    private final Long periodId = 1L;
    private final Long empId = 2000L;
    private final Long deptId = 10L;

    /** 테스트용 사원 생성 헬퍼 */
    private Employee createEmployee(Long empId, Long deptId, String name, String posName) {
        Employee emp = Employee.builder()
                .empId(empId).deptId(deptId).name(name).build();
        emp.setPositionName(posName);
        emp.setDeptName("인사팀");
        return emp;
    }

    /** 테스트용 매핑 생성 헬퍼 */
    private EvaluatorMapping createMapping(Long mappingId, Long evaluateeId, String relationType) {
        EvaluatorMapping m = new EvaluatorMapping();
        m.setMappingId(mappingId);
        m.setEvaluateeId(evaluateeId);
        m.setRelationTypeCode(relationType);
        m.setIsDeleted("n");
        return m;
    }

    /** 테스트용 평가 데이터 생성 헬퍼 */
    private Evaluation createEvaluation(Long mappingId, Long elementId, int score, String status) {
        Evaluation e = new Evaluation();
        e.setMappingId(mappingId);
        e.setElementId(elementId);
        e.setScore(score);
        e.setConfirmStatusCode(status);
        return e;
    }

    /** 테스트용 평가 요소 생성 헬퍼 */
    private EvaluationElementDTO createElement(Long elementId, String typeCode,
                                                BigDecimal maxScore, BigDecimal weight) {
        return createElement(elementId, deptId, typeCode, maxScore, weight);
    }

    /** 테스트용 평가 요소 생성 헬퍼 */
    private EvaluationElementDTO createElement(Long elementId, Long elementDeptId, String typeCode,
                                                BigDecimal maxScore, BigDecimal weight) {
        return new EvaluationElementDTO(
                elementId, periodId, elementDeptId, typeCode,
                "요소_" + elementId, maxScore, weight,
                "n", 0, null, null, null, null);
    }

    // ========================================================================
    // 1. 매핑 없는 경우
    // ========================================================================
    @Test
    @DisplayName("should_return_empty_when_매핑이_없을때")
    void should_return_empty_when_no_mappings() {
        // given
        given(mappingMapper.findAllByPeriodId(periodId)).willReturn(Collections.emptyList());

        // when
        List<EvaluationResultDTO> results = resultService.getResults(periodId, null);

        // then
        assertThat(results).isEmpty();
    }

    // ========================================================================
    // 2. 일반사원 (MBO/COMP) 점수 산출
    // ========================================================================
    @Nested
    @DisplayName("일반사원 점수 산출")
    class StaffScoreTests {

        @Test
        @DisplayName("should_calculate_MBO_1차_2차_최종_when_MANAGER와_EXECUTIVE_매핑존재")
        void should_calculate_mbo_scores() {
            Employee staff = createEmployee(empId, deptId, "이관리", "대리");

            // given - 매핑 기반 진입 (FinalGrade 없어도 표시됨)
            EvaluatorMapping mgrMapping = createMapping(100L, empId, "MANAGER");
            EvaluatorMapping execMapping = createMapping(200L, empId, "EXECUTIVE");
            given(mappingMapper.findAllByPeriodId(periodId))
                    .willReturn(List.of(mgrMapping, execMapping));

            given(employeeMapper.findByIds(anyList())).willReturn(List.of(staff));
            given(departmentMapper.findAllLeaderIds()).willReturn(Collections.emptyList()); // 일반사원

            // FinalGrade가 있는 경우 (최종 확정됨)
            FinalGrade fg = new FinalGrade();
            fg.setEmpId(empId); fg.setPeriodId(periodId); fg.setTotalScore(92); fg.setFinalGradeCode("A");
            given(finalGradeMapper.findByPeriodId(periodId)).willReturn(List.of(fg));

            // 평가 요소: PERFORMANCE 2개 (만점 100, 가중치 50씩)
            EvaluationElementDTO elem1 = createElement(1L, "PERFORMANCE",
                    new BigDecimal("100"), new BigDecimal("50"));
            EvaluationElementDTO elem2 = createElement(2L, "PERFORMANCE",
                    new BigDecimal("100"), new BigDecimal("50"));
            given(elementService.getElementsByPeriodIdAndDeptIds(eq(periodId), anyList()))
                    .willReturn(List.of(elem1, elem2));

            // 1차(MANAGER) 평가 점수: 90, 94
            Evaluation mgrEval1 = createEvaluation(100L, 1L, 90, "SUBMITTED");
            Evaluation mgrEval2 = createEvaluation(100L, 2L, 94, "SUBMITTED");
            // 2차(EXECUTIVE) 평가 점수: 90, 94
            Evaluation execEval1 = createEvaluation(200L, 1L, 90, "SUBMITTED");
            Evaluation execEval2 = createEvaluation(200L, 2L, 94, "SUBMITTED");

            given(evaluationMapper.findByMappingIds(anyList()))
                    .willReturn(List.of(mgrEval1, mgrEval2, execEval1, execEval2));

            // when
            List<EvaluationResultDTO> results = resultService.getResults(periodId, null);

            // then
            assertThat(results).hasSize(1);
            EvaluationResultDTO r = results.get(0);
            assertThat(r.isLeader()).isFalse();
            assertThat(r.jobTitle()).isEqualTo("팀원");
            assertThat(r.mbo1stScore()).isNotNull();
            assertThat(r.mbo2ndScore()).isNotNull();
            assertThat(r.mboFinalScore()).isEqualTo(r.mbo2ndScore()); // 최종 = 2차
        }

        @Test
        @DisplayName("should_show_1차만완료_when_MANAGER만_제출됨")
        void should_show_1st_only() {
            Employee staff = createEmployee(empId, deptId, "이관리", "대리");

            // MANAGER 매핑만 존재 (1차만 진행)
            EvaluatorMapping mgrMapping = createMapping(100L, empId, "MANAGER");
            given(mappingMapper.findAllByPeriodId(periodId)).willReturn(List.of(mgrMapping));

            given(employeeMapper.findByIds(anyList())).willReturn(List.of(staff));
            given(departmentMapper.findAllLeaderIds()).willReturn(Collections.emptyList());

            // FinalGrade 없음 (아직 최종 확정 안됨)
            given(finalGradeMapper.findByPeriodId(periodId)).willReturn(Collections.emptyList());

            EvaluationElementDTO elem = createElement(1L, "PERFORMANCE",
                    new BigDecimal("100"), new BigDecimal("100"));
            given(elementService.getElementsByPeriodIdAndDeptIds(eq(periodId), anyList()))
                    .willReturn(List.of(elem));

            Evaluation mgrEval = createEvaluation(100L, 1L, 90, "SUBMITTED");
            given(evaluationMapper.findByMappingIds(anyList()))
                    .willReturn(List.of(mgrEval));

            // when
            List<EvaluationResultDTO> results = resultService.getResults(periodId, null);

            // then — 1차만 완료된 사원도 목록에 표시됨
            assertThat(results).hasSize(1);
            EvaluationResultDTO r = results.get(0);
            assertThat(r.mbo1stScore()).isNotNull();    // 1차 점수 있음
            assertThat(r.mbo2ndScore()).isNull();       // 2차 아직 없음
            assertThat(r.mboFinalScore()).isNull();     // 최종(=2차) 없음
            assertThat(r.mboStatus()).isEqualTo("1차평가완료");
            assertThat(r.totalScore()).isNull();        // FinalGrade 없음
            assertThat(r.gradeCode()).isNull();
        }

        @Test
        @DisplayName("should_use_common_elements_when_부서전용_항목이_없을때")
        void should_use_common_elements_when_dept_specific_missing() {
            Employee staff = createEmployee(empId, deptId, "이관리", "대리");

            EvaluatorMapping mgrMapping = createMapping(100L, empId, "MANAGER");
            given(mappingMapper.findAllByPeriodId(periodId)).willReturn(List.of(mgrMapping));

            given(employeeMapper.findByIds(anyList())).willReturn(List.of(staff));
            given(departmentMapper.findAllLeaderIds()).willReturn(Collections.emptyList());
            given(finalGradeMapper.findByPeriodId(periodId)).willReturn(Collections.emptyList());

            EvaluationElementDTO commonElem = createElement(1L, null, "PERFORMANCE",
                    new BigDecimal("100"), new BigDecimal("100"));
            given(elementService.getElementsByPeriodIdAndDeptIds(eq(periodId), anyList()))
                    .willReturn(List.of(commonElem));

            Evaluation mgrEval = createEvaluation(100L, 1L, 90, "SUBMITTED");
            given(evaluationMapper.findByMappingIds(anyList()))
                    .willReturn(List.of(mgrEval));

            // when
            List<EvaluationResultDTO> results = resultService.getResults(periodId, null);

            // then
            assertThat(results).hasSize(1);
            EvaluationResultDTO r = results.get(0);
            assertThat(r.mbo1stScore()).isEqualByComparingTo(new BigDecimal("90"));
            assertThat(r.mboStatus()).isEqualTo("1차평가완료");
        }

        @Test
        @DisplayName("should_set_status_2차평가완료_when_EXECUTIVE_평가_제출됨")
        void should_set_status_correctly() {
            Employee staff = createEmployee(empId, deptId, "이관리", "대리");

            EvaluatorMapping execMapping = createMapping(200L, empId, "EXECUTIVE");
            given(mappingMapper.findAllByPeriodId(periodId)).willReturn(List.of(execMapping));

            given(employeeMapper.findByIds(anyList())).willReturn(List.of(staff));
            given(departmentMapper.findAllLeaderIds()).willReturn(Collections.emptyList());
            given(finalGradeMapper.findByPeriodId(periodId)).willReturn(Collections.emptyList());

            EvaluationElementDTO elem = createElement(1L, "PERFORMANCE",
                    new BigDecimal("100"), new BigDecimal("100"));
            given(elementService.getElementsByPeriodIdAndDeptIds(eq(periodId), anyList()))
                    .willReturn(List.of(elem));

            Evaluation execEval = createEvaluation(200L, 1L, 90, "SUBMITTED");
            given(evaluationMapper.findByMappingIds(anyList()))
                    .willReturn(List.of(execEval));

            // when
            List<EvaluationResultDTO> results = resultService.getResults(periodId, null);

            // then
            assertThat(results.get(0).mboStatus()).isEqualTo("2차평가완료");
        }
    }

    // ========================================================================
    // 3. 부서장 (다면평가 MULTI) 점수 산출
    // ========================================================================
    @Nested
    @DisplayName("부서장 다면평가 점수 산출")
    class LeaderMultiScoreTests {

        @Test
        @DisplayName("should_calculate_MULTI_1차_평균_when_SUBORDINATE_매핑_다수")
        void should_calculate_multi_1st_average() {
            Employee leader = createEmployee(empId, deptId, "김부장", "팀장");

            // 매핑 기반 진입
            EvaluatorMapping sub1 = createMapping(301L, empId, "SUBORDINATE");
            EvaluatorMapping sub2 = createMapping(302L, empId, "SUBORDINATE");
            EvaluatorMapping execMapping = createMapping(200L, empId, "EXECUTIVE");
            given(mappingMapper.findAllByPeriodId(periodId))
                    .willReturn(List.of(sub1, sub2, execMapping));

            given(employeeMapper.findByIds(anyList())).willReturn(List.of(leader));
            given(departmentMapper.findAllLeaderIds()).willReturn(List.of(empId)); // 부서장

            FinalGrade fg = new FinalGrade();
            fg.setEmpId(empId); fg.setPeriodId(periodId); fg.setTotalScore(85); fg.setFinalGradeCode("A");
            given(finalGradeMapper.findByPeriodId(periodId)).willReturn(List.of(fg));

            // 평가 요소: MULTI_DIMENSIONAL 1개 (만점 100, 가중치 100)
            EvaluationElementDTO multiElem = createElement(10L, "MULTI_DIMENSIONAL",
                    new BigDecimal("100"), new BigDecimal("100"));
            given(elementService.getElementsByPeriodIdAndDeptIds(eq(periodId), anyList()))
                    .willReturn(List.of(multiElem));

            // SUBORDINATE 1: 점수 80, SUBORDINATE 2: 점수 90 → 평균 85
            Evaluation subEval1 = createEvaluation(301L, 10L, 80, "SUBMITTED");
            Evaluation subEval2 = createEvaluation(302L, 10L, 90, "SUBMITTED");
            // EXECUTIVE: 점수 87
            Evaluation execEval = createEvaluation(200L, 10L, 87, "SUBMITTED");

            given(evaluationMapper.findByMappingIds(anyList()))
                    .willReturn(List.of(subEval1, subEval2, execEval));

            // when
            List<EvaluationResultDTO> results = resultService.getResults(periodId, null);

            // then
            assertThat(results).hasSize(1);
            EvaluationResultDTO r = results.get(0);
            assertThat(r.isLeader()).isTrue();
            assertThat(r.jobTitle()).isEqualTo("부서장");
            assertThat(r.multi1stScore()).isEqualByComparingTo(new BigDecimal("85"));
            assertThat(r.multi2ndScore()).isEqualByComparingTo(new BigDecimal("87"));
            assertThat(r.multiFinalScore()).isEqualTo(r.multi2ndScore());
        }

        @Test
        @DisplayName("should_set_isLeader_true_when_부서장")
        void should_set_isLeader_true() {
            Employee leader = createEmployee(empId, deptId, "김부장", "팀장");

            // 매핑 1개만 존재
            EvaluatorMapping mapping = createMapping(100L, empId, "MANAGER");
            given(mappingMapper.findAllByPeriodId(periodId)).willReturn(List.of(mapping));

            given(employeeMapper.findByIds(anyList())).willReturn(List.of(leader));
            given(departmentMapper.findAllLeaderIds()).willReturn(List.of(empId));
            given(finalGradeMapper.findByPeriodId(periodId)).willReturn(Collections.emptyList());
            EvaluationElementDTO elem = createElement(1L, "PERFORMANCE",
                    new BigDecimal("100"), new BigDecimal("100"));
            given(elementService.getElementsByPeriodIdAndDeptIds(eq(periodId), anyList()))
                    .willReturn(List.of(elem));
            Evaluation eval = createEvaluation(100L, 1L, 90, "SUBMITTED");
            given(evaluationMapper.findByMappingIds(anyList()))
                    .willReturn(List.of(eval));

            // when
            List<EvaluationResultDTO> results = resultService.getResults(periodId, null);

            // then
            assertThat(results.get(0).isLeader()).isTrue();
            assertThat(results.get(0).jobTitle()).isEqualTo("부서장");
        }
    }

    // ========================================================================
    // 4. 부서 필터
    // ========================================================================
    @Test
    @DisplayName("should_filter_by_deptId_when_부서필터_적용")
    void should_filter_by_dept() {
        Employee emp1 = createEmployee(2000L, 10L, "이사원", "사원");
        Employee emp2 = createEmployee(2001L, 20L, "박사원", "사원");

        // 두 사원 모두 매핑 있음
        EvaluatorMapping m1 = createMapping(100L, 2000L, "MANAGER");
        EvaluatorMapping m2 = createMapping(101L, 2001L, "MANAGER");
        given(mappingMapper.findAllByPeriodId(periodId)).willReturn(List.of(m1, m2));

        given(employeeMapper.findByIds(anyList())).willReturn(List.of(emp1, emp2));
        given(departmentMapper.findAllLeaderIds()).willReturn(Collections.emptyList());
        given(finalGradeMapper.findByPeriodId(periodId)).willReturn(Collections.emptyList());
        EvaluationElementDTO elem = createElement(1L, 10L, "PERFORMANCE",
                new BigDecimal("100"), new BigDecimal("100"));
        given(elementService.getElementsByPeriodIdAndDeptIds(eq(periodId), anyList()))
                .willReturn(List.of(elem));
        Evaluation eval = createEvaluation(100L, 1L, 90, "SUBMITTED");
        given(evaluationMapper.findByMappingIds(anyList()))
                .willReturn(List.of(eval));

        // when — deptId=10 필터
        List<EvaluationResultDTO> results = resultService.getResults(periodId, 10L);

        // then — emp1만 반환
        assertThat(results).hasSize(1);
        assertThat(results.get(0).empId()).isEqualTo(2000L);
    }
}
