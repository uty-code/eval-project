package com.ees.eval.service;

import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.EvaluatorMappingDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EvaluatorMappingService의 통합 테스트 클래스입니다.
 * 중복 체크, 자기평가 검증, 일괄 매핑, 내 평가 목록/나를 평가하는 사람 조회를 검증합니다.
 */
@SpringBootTest
@Transactional
class EvaluatorMappingServiceTest {

    @Autowired
    private EvaluatorMappingService mappingService;

    @Autowired
    private EvaluationPeriodService periodService;

    @Autowired
    private EmployeeService employeeService;

    /** 테스트용 차수 ID */
    private Long testPeriodId;

    /** 테스트용 사원 ID (A: 팀장, B: 사원, C: 사원) */
    private Long empIdA;
    private Long empIdB;
    private Long empIdC;

    /**
     * 각 테스트 전에 차수 1건 + 사원 3명을 미리 등록합니다.
     */
    @BeforeEach
    void setUp() {
        // 평가 차수 생성
        EvaluationPeriodDTO period = periodService.createPeriod(
                EvaluationPeriodDTO.builder()
                        .periodYear(2026).periodName("매핑 테스트 차수")
                        .startDate(LocalDate.of(2026, 1, 1))
                        .endDate(LocalDate.of(2026, 6, 30))
                        .build());
        testPeriodId = period.periodId();

        // 사원 A (팀장)
        empIdA = employeeService.registerEmployee(
                EmployeeDTO.builder()
                        .deptId(1L).positionId(3L) // 과장
                        .password("passA!")
                        .name("김팀장").email("managerA@ees.com")
                        .hireDate(LocalDate.of(2020, 3, 1)).build(),
                List.of(2L)).empId(); // ROLE_MANAGER

        // 사원 B
        empIdB = employeeService.registerEmployee(
                EmployeeDTO.builder()
                        .deptId(1L).positionId(1L) // 사원
                        .password("passB!")
                        .name("이사원").email("memberB@ees.com")
                        .hireDate(LocalDate.of(2024, 5, 1)).build(),
                List.of(3L)).empId(); // ROLE_USER

        // 사원 C
        empIdC = employeeService.registerEmployee(
                EmployeeDTO.builder()
                        .deptId(1L).positionId(1L)
                        .password("passC!")
                        .name("박사원").email("memberC@ees.com")
                        .hireDate(LocalDate.of(2025, 1, 1)).build(),
                List.of(3L)).empId();
    }

    /**
     * 단건 매핑 생성 및 조회를 검증합니다.
     */
    @Test
    @DisplayName("단건 매핑 생성 - 상급자 평가 매핑")
    void createSingleMappingTest() {
        // when: A(팀장)가 B(사원)의 상급자 평가자로 매핑
        EvaluatorMappingDTO mapping = mappingService.createMapping(
                EvaluatorMappingDTO.builder()
                        .periodId(testPeriodId)
                        .evaluateeId(empIdB)
                        .evaluatorId(empIdA)
                        .relationTypeCode("SUPERIOR")
                        .build());

        // then
        assertThat(mapping.mappingId()).isNotNull();
        assertThat(mapping.evaluateeName()).isEqualTo("이사원");
        assertThat(mapping.evaluatorName()).isEqualTo("김팀장");
        assertThat(mapping.relationTypeCode()).isEqualTo("SUPERIOR");
    }

    /**
     * 동일 차수에서 동일 관계가 중복되면 예외가 발생하는지 검증합니다.
     */
    @Test
    @DisplayName("중복 체크 - 동일 관계 중복 생성 차단")
    void duplicateMappingBlockedTest() {
        // given: 최초 매핑 성공
        mappingService.createMapping(EvaluatorMappingDTO.builder()
                .periodId(testPeriodId).evaluateeId(empIdB)
                .evaluatorId(empIdA).relationTypeCode("SUPERIOR").build());

        // then: 동일 매핑 재시도 → 예외
        assertThatThrownBy(() -> mappingService.createMapping(EvaluatorMappingDTO.builder()
                .periodId(testPeriodId).evaluateeId(empIdB)
                .evaluatorId(empIdA).relationTypeCode("SUPERIOR").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 존재");
    }

    /**
     * 자기 자신을 SUPERIOR/PEER로 매핑하면 예외가 발생하고,
     * SELF는 허용되는지 검증합니다.
     */
    @Test
    @DisplayName("자기평가 검증 - SELF 허용, SUPERIOR/PEER 차단")
    void selfMappingValidationTest() {
        // SELF → 허용
        EvaluatorMappingDTO selfMapping = mappingService.createMapping(
                EvaluatorMappingDTO.builder()
                        .periodId(testPeriodId).evaluateeId(empIdB)
                        .evaluatorId(empIdB).relationTypeCode("SELF").build());
        assertThat(selfMapping.relationTypeCode()).isEqualTo("SELF");

        // SUPERIOR → 차단
        assertThatThrownBy(() -> mappingService.createMapping(
                EvaluatorMappingDTO.builder()
                        .periodId(testPeriodId).evaluateeId(empIdB)
                        .evaluatorId(empIdB).relationTypeCode("SUPERIOR").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상급자");

        // PEER → 차단
        assertThatThrownBy(() -> mappingService.createMapping(
                EvaluatorMappingDTO.builder()
                        .periodId(testPeriodId).evaluateeId(empIdB)
                        .evaluatorId(empIdB).relationTypeCode("PEER").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("동료");
    }

    /**
     * 일괄 매핑: 한 명의 피평가자에게 여러 평가자를 동시 매핑합니다.
     */
    @Test
    @DisplayName("일괄 매핑 - 여러 평가자 한 번에 매핑")
    void bulkMappingTest() {
        // when: B에게 A, C를 PEER로 일괄 매핑
        List<EvaluatorMappingDTO> results = mappingService.createBulkMappings(
                testPeriodId, empIdB, List.of(empIdA, empIdC), "PEER");

        // then
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().evaluatorName()).isEqualTo("김팀장");
        assertThat(results.getLast().evaluatorName()).isEqualTo("박사원");
    }

    /**
     * '내가 수행해야 할 평가 목록'과 '나를 평가하는 사람 목록'을 정확히 조회합니다.
     */
    @Test
    @DisplayName("평가 목록 조회 - 내 평가 과제 / 나를 평가하는 사람")
    void evaluationTaskAndEvaluatorListTest() {
        // given: 매핑 구성
        // A → B 상급자 평가
        mappingService.createMapping(EvaluatorMappingDTO.builder()
                .periodId(testPeriodId).evaluateeId(empIdB)
                .evaluatorId(empIdA).relationTypeCode("SUPERIOR").build());

        // A → C 상급자 평가
        mappingService.createMapping(EvaluatorMappingDTO.builder()
                .periodId(testPeriodId).evaluateeId(empIdC)
                .evaluatorId(empIdA).relationTypeCode("SUPERIOR").build());

        // C → B 동료 평가
        mappingService.createMapping(EvaluatorMappingDTO.builder()
                .periodId(testPeriodId).evaluateeId(empIdB)
                .evaluatorId(empIdC).relationTypeCode("PEER").build());

        // B 자기 평가
        mappingService.createMapping(EvaluatorMappingDTO.builder()
                .periodId(testPeriodId).evaluateeId(empIdB)
                .evaluatorId(empIdB).relationTypeCode("SELF").build());

        // when: A의 '내가 해야 할 평가 목록' → 2건 (B상급자, C상급자)
        List<EvaluatorMappingDTO> aTasks = mappingService.getMyEvaluationTasks(testPeriodId, empIdA);
        assertThat(aTasks).hasSize(2);

        // when: B의 '나를 평가하는 사람 목록' → 3건 (A상급자, C동료, B자기)
        List<EvaluatorMappingDTO> bEvaluators = mappingService.getMyEvaluators(testPeriodId, empIdB);
        assertThat(bEvaluators).hasSize(3);

        // then: B의 평가자에 자기평가(SELF), 상급자(SUPERIOR), 동료(PEER)가 모두 포함
        List<String> bRelationTypes = bEvaluators.stream()
                .map(EvaluatorMappingDTO::relationTypeCode).toList();
        assertThat(bRelationTypes).containsExactlyInAnyOrder("SELF", "SUPERIOR", "PEER");
    }
}
