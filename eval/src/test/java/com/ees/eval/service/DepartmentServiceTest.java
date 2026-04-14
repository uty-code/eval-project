package com.ees.eval.service;

import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.exception.EesOptimisticLockException;
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
 * DepartmentService의 통합 테스트 클래스입니다.
 * 계층형 부서 관리, 부서별 사원 조회(EmployeeService 연동),
 * 낙관적 락, 소프트 삭제 및 삭제 안전장치를 검증합니다.
 */
@SpringBootTest
@Transactional
class DepartmentServiceTest {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private EmployeeService employeeService;

    /**
     * 부서 생성 후 상위 부서명과 인원수가 정상적으로 함께 조회되는지 검증합니다.
     */
    @Test
    @DisplayName("부서 생성 및 조회 - 상위 부서명, 인원수 포함 검증")
    void createAndGetDepartmentTest() {
        // given: 상위 부서 생성 (최상위)
        DepartmentDTO parentDto = DepartmentDTO.builder()
                .deptName("기술본부")
                .build();
        DepartmentDTO parentSaved = departmentService.createDepartment(parentDto);

        // given: 하위 부서 생성 (상위 부서 참조)
        DepartmentDTO childDto = DepartmentDTO.builder()
                .parentDeptId(parentSaved.deptId())
                .deptName("백엔드팀")
                .build();
        DepartmentDTO childSaved = departmentService.createDepartment(childDto);

        // then: 하위 부서 조회 시 상위 부서명이 포함되어야 함
        DepartmentDTO found = departmentService.getDepartmentById(childSaved.deptId());
        assertThat(found.deptName()).isEqualTo("백엔드팀");
        assertThat(found.parentDeptName()).isEqualTo("기술본부");
        assertThat(found.employeeCount()).isEqualTo(0);
    }

    /**
     * 계층형 트리 구조: 루트 부서 및 하위 부서 조회 기능을 검증합니다.
     */
    @Test
    @DisplayName("계층형 트리 - 루트 및 하위 부서 조회 검증")
    void hierarchyTest() {
        // given: 루트 부서 + 2개 하위 부서 구성
        DepartmentDTO rootDto = DepartmentDTO.builder().deptName("임원실").build();
        DepartmentDTO root = departmentService.createDepartment(rootDto);

        DepartmentDTO child1 = departmentService.createDepartment(
                DepartmentDTO.builder().parentDeptId(root.deptId()).deptName("전략기획팀").build());
        DepartmentDTO child2 = departmentService.createDepartment(
                DepartmentDTO.builder().parentDeptId(root.deptId()).deptName("재무팀").build());

        // when: 루트 부서 조회
        List<DepartmentDTO> roots = departmentService.getRootDepartments();
        // data.sql에서 삽입된 2개 + 테스트에서 만든 1개 이상
        assertThat(roots.size()).isGreaterThanOrEqualTo(1);

        // when: 하위 부서 조회 (Sequenced Collections 활용)
        List<DepartmentDTO> children = departmentService.getChildDepartments(root.deptId());
        assertThat(children).hasSize(2);
        assertThat(children.getFirst().deptName()).isEqualTo("전략기획팀");
        assertThat(children.getLast().deptName()).isEqualTo("재무팀");
        // 하위 부서의 parentDeptName이 루트 부서명으로 올바르게 채워졌는지 확인
        assertThat(children.getFirst().parentDeptName()).isEqualTo("임원실");
    }

    /**
     * EmployeeService 연동: 부서에 사원을 배치한 뒤
     * 부서별 사원 목록 조회 및 인원수 카운트가 정상 동작하는지 검증합니다.
     */
    @Test
    @DisplayName("부서-사원 연동 - 부서별 사원 조회 및 인원수 통계")
    void departmentEmployeeIntegrationTest() {
        // given: 부서 생성
        DepartmentDTO deptDto = DepartmentDTO.builder().deptName("QA팀").build();
        DepartmentDTO dept = departmentService.createDepartment(deptDto);

        // given: 해당 부서에 사원 2명 등록
        EmployeeDTO emp1 = EmployeeDTO.builder()
                .deptId(dept.deptId()).positionId(1L)
                .password("qaPass1!")
                .name("테스터1").email("qa1@ees.com")
                .hireDate(LocalDate.of(2026, 1, 1))
                .build();
        EmployeeDTO emp2 = EmployeeDTO.builder()
                .deptId(dept.deptId()).positionId(1L)
                .password("qaPass2!")
                .name("테스터2").email("qa2@ees.com")
                .hireDate(LocalDate.of(2026, 2, 1))
                .build();
        employeeService.registerEmployee(emp1, List.of(1L)); // ROLE_USER (role_id=1)
        employeeService.registerEmployee(emp2, List.of(1L)); // ROLE_USER (role_id=1)

        // when: 부서별 사원 목록 조회
        List<EmployeeDTO> employees = departmentService.getEmployeesByDeptId(dept.deptId());
        assertThat(employees).hasSize(2);
        assertThat(employees.getFirst().name()).isEqualTo("테스터1");
        assertThat(employees.getLast().name()).isEqualTo("테스터2");
        // 사원의 권한명도 조회되는지 확인
        assertThat(employees.getFirst().roleNames()).contains("ROLE_USER");

        // when: 부서 상세 조회 시 인원수가 반영되었는지 확인
        DepartmentDTO deptWithCount = departmentService.getDepartmentById(dept.deptId());
        assertThat(deptWithCount.employeeCount()).isEqualTo(2);
    }

    /**
     * 소속 사원이 존재하는 부서 삭제 시도 시 예외가 발생하는지 검증합니다.
     */
    @Test
    @DisplayName("삭제 안전장치 - 소속 사원 존재 시 삭제 거부")
    void deleteWithEmployeesBlockedTest() {
        // given: 부서 생성 후 사원 1명 배치
        DepartmentDTO deptDto = DepartmentDTO.builder().deptName("임시팀").build();
        DepartmentDTO dept = departmentService.createDepartment(deptDto);

        EmployeeDTO empDto = EmployeeDTO.builder()
                .deptId(dept.deptId()).positionId(1L)
                .password("tmpPass!")
                .name("임시사원").email("tmp@ees.com")
                .hireDate(LocalDate.of(2026, 3, 1))
                .build();
        employeeService.registerEmployee(empDto, List.of(1L)); // ROLE_USER (role_id=1)

        // when & then: 사원이 존재하므로 삭제 거부 (IllegalStateException)
        assertThatThrownBy(() -> departmentService.deleteDepartment(dept.deptId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("소속 사원이");
    }

    /**
     * 하위 부서가 존재하는 상위 부서 삭제 시도 시 예외가 발생하는지 검증합니다.
     */
    @Test
    @DisplayName("삭제 안전장치 - 하위 부서 존재 시 삭제 거부")
    void deleteWithChildDeptBlockedTest() {
        // given: 상위 + 하위 부서 구성
        DepartmentDTO parent = departmentService.createDepartment(
                DepartmentDTO.builder().deptName("상위부서").build());
        departmentService.createDepartment(
                DepartmentDTO.builder().parentDeptId(parent.deptId()).deptName("하위부서").build());

        // when & then: 하위 부서가 있으므로 삭제 거부
        assertThatThrownBy(() -> departmentService.deleteDepartment(parent.deptId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("하위 부서가");
    }

    /**
     * 사원도 하위 부서도 없는 빈 부서의 정상 삭제 및 낙관적 락을 검증합니다.
     */
    @Test
    @DisplayName("부서 삭제 및 낙관적 락 검증")
    void deleteAndOptimisticLockTest() {
        // given: 빈 부서 생성
        DepartmentDTO deptDto = DepartmentDTO.builder().deptName("삭제예정팀").build();
        DepartmentDTO saved = departmentService.createDepartment(deptDto);

        // when: 정상 삭제
        departmentService.deleteDepartment(saved.deptId());

        // then: 삭제 후 조회 불가
        assertThatThrownBy(() -> departmentService.getDepartmentById(saved.deptId()))
                .isInstanceOf(IllegalArgumentException.class);

        // --- 낙관적 락 검증 ---
        DepartmentDTO lockDto = DepartmentDTO.builder().deptName("락테스트팀").build();
        DepartmentDTO lockSaved = departmentService.createDepartment(lockDto);

        DepartmentDTO tx1 = departmentService.getDepartmentById(lockSaved.deptId());
        DepartmentDTO tx2 = departmentService.getDepartmentById(lockSaved.deptId());

        // 첫 번째 수정 성공
        departmentService.updateDepartment(
                DepartmentDTO.builder()
                        .deptId(tx1.deptId()).parentDeptId(tx1.parentDeptId())
                        .deptName("수정됨").isDeleted(tx1.isDeleted()).version(tx1.version())
                        .createdAt(tx1.createdAt()).createdBy(tx1.createdBy())
                        .build());

        // 두 번째 수정 → 버전 충돌
        assertThatThrownBy(() -> departmentService.updateDepartment(
                DepartmentDTO.builder()
                        .deptId(tx2.deptId()).parentDeptId(tx2.parentDeptId())
                        .deptName("충돌예정").isDeleted(tx2.isDeleted()).version(tx2.version())
                        .createdAt(tx2.createdAt()).createdBy(tx2.createdBy())
                        .build()))
                .isInstanceOf(EesOptimisticLockException.class);
    }
}
