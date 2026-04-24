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
 * 시드 데이터와의 격리를 위해 테스트 전용 부서를 생성하여 사용합니다.
 */
@SpringBootTest
@Transactional
class EvaluatorMappingServiceTest extends com.ees.eval.support.AbstractMssqlTest {

    @Autowired
    private EvaluatorMappingService mappingService;

    @Autowired
    private EvaluationPeriodService periodService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private DepartmentService departmentService;

    /** 테스트용 차수 ID */
    private Long testPeriodId;

    /** 테스트 전용 부서 ID (시드 데이터와 격리) */
    private Long testDeptId;

    /** 테스트용 사원 ID (A: ROLE_MANAGER, B: ROLE_USER, C: ROLE_USER, D: ROLE_EXECUTIVE, E: ROLE_ADMIN) */
    private Long empIdA;
    private Long empIdB;
    private Long empIdC;
    private Long empIdD;
    private Long empIdE;
    private String nameA;
    private String nameB;
    private String nameC;
    private String nameD;
    private String nameE;

    /**
     * 각 테스트 전에 전용 부서 + 차수 1건 + 사원 3명을 미리 등록합니다.
     * 시드 데이터(eval-data.sql)와의 간섭을 방지하기 위해 별도의 부서를 생성합니다.
     */
    @BeforeEach
    void setUp() {
        // 테스트 전용 부서 생성 (시드 데이터와 격리)
        com.ees.eval.dto.DepartmentDTO testDept = departmentService.createDepartment(
                com.ees.eval.dto.DepartmentDTO.builder()
                        .deptName("매핑테스트부서")
                        .parentDeptId(null)  // 최상위 부서 (EXECUTIVE 매핑 검증에도 활용)
                        .build());
        testDeptId = testDept.deptId();

        // 평가 차수 생성
        EvaluationPeriodDTO period = periodService.createPeriod(
                EvaluationPeriodDTO.builder()
                        .periodYear(2026).periodName("매핑 테스트 차수")
                        .startDate(LocalDate.of(2026, 1, 1))
                        .endDate(LocalDate.of(2026, 6, 30))
                        .build());
        testPeriodId = period.periodId();

        // 사원 A (ROLE_MANAGER 권한)
        EmployeeDTO empA = employeeService.registerEmployee(
                EmployeeDTO.builder()
                        .deptId(testDeptId).positionId(3L) // 과장
                        .password("passA!")
                        .name("김팀장").email("managerA@ees.com")
                        .hireDate(LocalDate.of(2020, 3, 1)).build(),
                List.of(2L)); // ROLE_MANAGER
        empIdA = empA.empId();
        nameA = empA.name();

        // 사원 B (ROLE_USER 권한)
        EmployeeDTO empB = employeeService.registerEmployee(
                EmployeeDTO.builder()
                        .deptId(testDeptId).positionId(1L) // 사원
                        .password("passB!")
                        .name("이사원").email("memberB@ees.com")
                        .hireDate(LocalDate.of(2024, 5, 1)).build(),
                List.of(1L)); // ROLE_USER
        empIdB = empB.empId();
        nameB = empB.name();

        // 사원 C (ROLE_USER 권한)
        EmployeeDTO empC = employeeService.registerEmployee(
                EmployeeDTO.builder()
                        .deptId(testDeptId).positionId(1L)
                        .password("passC!")
                        .name("박사원").email("memberC@ees.com")
                        .hireDate(LocalDate.of(2025, 1, 1)).build(),
                List.of(1L)); // ROLE_USER
        empIdC = empC.empId();
        nameC = empC.name();

        // 사원 D (ROLE_EXECUTIVE 권한 - 최상위 부서이므로 임원 권한 부여 가능)
        EmployeeDTO empD = employeeService.registerEmployee(
                EmployeeDTO.builder()
                        .deptId(testDeptId).positionId(6L) // 이사
                        .password("passD!")
                        .name("윤임원").email("execD@ees.com")
                        .hireDate(LocalDate.of(2018, 1, 1)).build(),
                List.of(3L)); // ROLE_EXECUTIVE
        empIdD = empD.empId();
        nameD = empD.name();

        // 사원 E (ROLE_ADMIN 권한 - 시스템 관리자)
        EmployeeDTO empE = employeeService.registerEmployee(
                EmployeeDTO.builder()
                        .deptId(testDeptId).positionId(1L)
                        .password("passE!")
                        .name("관리자E").email("adminE@ees.com")
                        .hireDate(LocalDate.of(2019, 1, 1)).build(),
                List.of(4L)); // ROLE_ADMIN
        empIdE = empE.empId();
        nameE = empE.name();
    }

    /**
     * 단건 매핑 생성 및 조회를 검증합니다.
     */
    @Test
    @DisplayName("단건 매핑 생성 - 상급자 평가 매핑")
    void createSingleMappingTest() {
        // when: A(팀장)가 B(사원)의 상급자 평가자로 매핑
        EvaluatorMappingDTO created = mappingService.createMapping(
                EvaluatorMappingDTO.builder()
                        .periodId(testPeriodId)
                        .evaluateeId(empIdB)
                        .evaluatorId(empIdA)
                        .relationTypeCode("SUPERIOR")
                        .build());

        // then: 생성 직후 반환된 DTO가 아닌, ID로 재조회하여 JOIN된 정보(이름) 검증
        EvaluatorMappingDTO mapping = mappingService.getMappingById(created.mappingId());
        
        assertThat(mapping.mappingId()).isNotNull();
        assertThat(mapping.evaluateeId()).isEqualTo(empIdB);
        assertThat(mapping.evaluateeName()).isEqualTo(nameB);
        assertThat(mapping.evaluatorId()).isEqualTo(empIdA);
        assertThat(mapping.evaluatorName()).isEqualTo(nameA);
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
     * SELF가 아닌 유형(SUPERIOR 등)으로 자기 자신을 매핑하면 예외가 발생하고,
     * SELF 유형은 본인 매핑이 허용되는지 검증합니다.
     */
    @Test
    @DisplayName("자기평가 검증 - SELF 허용, 기타 유형 본인 매핑 차단")
    void selfMappingValidationTest() {
        // SELF 유형으로 본인 매핑 → 허용 (일반 사원인 empIdB)
        EvaluatorMappingDTO selfMapping = mappingService.createMapping(
                EvaluatorMappingDTO.builder()
                        .periodId(testPeriodId).evaluateeId(empIdB)
                        .evaluatorId(empIdB).relationTypeCode("SELF").build());
        assertThat(selfMapping.mappingId()).isNotNull();
        assertThat(selfMapping.relationTypeCode()).isEqualTo("SELF");

        // SUPERIOR 유형으로 본인 매핑 → 차단
        assertThatThrownBy(() -> mappingService.createMapping(
                EvaluatorMappingDTO.builder()
                        .periodId(testPeriodId).evaluateeId(empIdB)
                        .evaluatorId(empIdB).relationTypeCode("SUPERIOR").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자기 자신을 평가자로 지정할 수 없습니다");
    }

    /**
     * 자동 매핑 생성 시 임원(ROLE_EXECUTIVE)과 시스템 관리자(ROLE_ADMIN) 역할의 사원은
     * SELF(자기평가) 매핑이 생성되지 않는지 검증합니다.
     * 부서장(ROLE_MANAGER)과 일반 사원(ROLE_USER)은 SELF 매핑이 정상 생성되어야 합니다.
     */
    @Test
    @DisplayName("자동 매핑 - 임원/시스템관리자 SELF 제외, 부서장/일반사원 허용")
    void autoGenerateMappings_excludesExecutiveAndAdminFromSelfTest() {
        // when: 테스트 전용 부서 대상 자동 매핑 생성 (시드 데이터와 격리)
        mappingService.autoGenerateMappings(testPeriodId, testDeptId, null);

        // then: empIdA(ROLE_MANAGER, 부서장)의 매핑 목록에 SELF가 있어야 함
        List<EvaluatorMappingDTO> aEvaluators = mappingService.getMyEvaluators(testPeriodId, empIdA);
        boolean aHasSelf = aEvaluators.stream()
                .anyMatch(m -> "SELF".equals(m.relationTypeCode()));
        assertThat(aHasSelf)
                .as("ROLE_MANAGER 역할의 부서장(%d)에게도 SELF 매핑이 생성되어야 합니다", empIdA)
                .isTrue();

        // then: empIdB(ROLE_USER)의 매핑 목록에도 SELF가 있어야 함
        List<EvaluatorMappingDTO> bEvaluators = mappingService.getMyEvaluators(testPeriodId, empIdB);
        boolean bHasSelf = bEvaluators.stream()
                .anyMatch(m -> "SELF".equals(m.relationTypeCode()));
        assertThat(bHasSelf)
                .as("ROLE_USER 역할의 사원(%d)에게는 SELF 매핑이 생성되어야 합니다", empIdB)
                .isTrue();

        // then: empIdD(ROLE_EXECUTIVE, 임원)의 매핑 목록에 SELF가 없어야 함
        List<EvaluatorMappingDTO> dEvaluators = mappingService.getMyEvaluators(testPeriodId, empIdD);
        boolean dHasSelf = dEvaluators.stream()
                .anyMatch(m -> "SELF".equals(m.relationTypeCode()));
        assertThat(dHasSelf)
                .as("ROLE_EXECUTIVE 역할의 임원(%d)에게는 SELF 매핑이 생성되지 않아야 합니다", empIdD)
                .isFalse();

        // then: empIdE(ROLE_ADMIN, 시스템 관리자)의 매핑 목록에 SELF가 없어야 함
        List<EvaluatorMappingDTO> eEvaluators = mappingService.getMyEvaluators(testPeriodId, empIdE);
        boolean eHasSelf = eEvaluators.stream()
                .anyMatch(m -> "SELF".equals(m.relationTypeCode()));
        assertThat(eHasSelf)
                .as("ROLE_ADMIN 역할의 시스템 관리자(%d)에게는 SELF 매핑이 생성되지 않아야 합니다", empIdE)
                .isFalse();
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
        
        // 각각 재조회하여 이름 검증 (서비스 삽입 직후 반환 DTO 보강)
        EvaluatorMappingDTO first = mappingService.getMappingById(results.getFirst().mappingId());
        EvaluatorMappingDTO last = mappingService.getMappingById(results.getLast().mappingId());

        assertThat(first.evaluatorName()).isEqualTo(nameA);
        assertThat(last.evaluatorName()).isEqualTo(nameC);
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

        // when: A의 '내가 해야 할 평가 목록' → 2건 (B상급자, C상급자)
        List<EvaluatorMappingDTO> aTasks = mappingService.getMyEvaluationTasks(testPeriodId, empIdA);
        assertThat(aTasks).hasSize(2);

        // when: B의 '나를 평가하는 사람 목록' → 2건 (A상급자, C동료)
        List<EvaluatorMappingDTO> bEvaluators = mappingService.getMyEvaluators(testPeriodId, empIdB);
        assertThat(bEvaluators).hasSize(2);

        // then: B의 평가자에 상급자(SUPERIOR), 동료(PEER)가 포함
        List<String> bRelationTypes = bEvaluators.stream()
                .map(EvaluatorMappingDTO::relationTypeCode).toList();
        assertThat(bRelationTypes).containsExactlyInAnyOrder("SUPERIOR", "PEER");
    }
}
