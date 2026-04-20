package com.ees.eval.service;

import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EmployeeService의 통합 테스트 클래스입니다.
 * BCrypt 비밀번호 암호화, JOIN 기반 직급/권한 조회, 낙관적 락,
 * 그리고 인증 검증 등 핵심 기능을 검증합니다.
 */
@SpringBootTest
@Transactional
class EmployeeServiceTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @org.junit.jupiter.api.BeforeEach
    void printHash() {
        System.out.println("DEBUG_HASH_ADMIN123: " + passwordEncoder.encode("admin123"));
    }

    /**
     * 사원 등록 후 비밀번호가 BCrypt로 암호화되어 저장되었는지,
     * 그리고 권한이 정상적으로 매핑되었는지 검증합니다.
     */
    @Test
    @DisplayName("사원 등록 - BCrypt 암호화 및 권한 매핑 검증")
    void registerEmployeeTest() {
        // given: 사원 등록 정보 준비 (평문 비밀번호)
        EmployeeDTO dto = EmployeeDTO.builder()
                .deptId(1L)        // data.sql에 삽입된 경영지원팀
                .positionId(1L)    // data.sql에 삽입된 사원 직급
                .password("myPassword123!")
                .name("홍길동")
                .email("hong@ees.com")
                .hireDate(LocalDate.of(2026, 3, 1))
                .build();

        // when: 사원 등록 수행 (ROLE_ADMIN 권한 부여, role_id=4)
        EmployeeDTO saved = employeeService.registerEmployee(dto, List.of(4L));

        // then: 저장 결과 검증
        assertThat(saved.empId()).isNotNull();
        assertThat(saved.name()).isEqualTo("홍길동");
        assertThat(saved.password()).isNull(); // DTO에는 비밀번호가 노출되지 않아야 함
        assertThat(saved.isDeleted()).isEqualTo("n");
        assertThat(saved.version()).isEqualTo(0);

        // then: 권한이 정상 매핑되었는지 검증 (Sequenced Collection 활용)
        assertThat(saved.roleNames()).isNotEmpty();
        assertThat(saved.roleNames().getFirst()).isEqualTo("ROLE_ADMIN");
    }

    /**
     * 올바른 비밀번호와 틀린 비밀번호로 인증(로그인) 시도하여
     * BCrypt 매칭 로직이 정상 동작하는지 검증합니다.
     */
    @Test
    @DisplayName("로그인 인증 - BCrypt 비밀번호 매칭 검증")
    void authenticateTest() {
        // given: 사원 등록
        EmployeeDTO dto = EmployeeDTO.builder()
                .deptId(1L).positionId(1L)
                .password("plain_login_pass")
                .name("김인증")
                .email("kim@ees.com")
                .hireDate(LocalDate.of(2026, 1, 15))
                .build();
        EmployeeDTO savedEmp = employeeService.registerEmployee(dto, List.of(1L)); // ROLE_USER (role_id=1)
        String empIdStr = String.valueOf(savedEmp.empId());

        // when & then: 올바른 비밀번호로 인증 성공
        boolean success = employeeService.authenticate(empIdStr, "plain_login_pass");
        assertThat(success).isTrue();

        // when & then: 틀린 비밀번호로 인증 실패
        boolean failure = employeeService.authenticate(empIdStr, "wrongPassword");
        assertThat(failure).isFalse();

        // when & then: 존재하지 않는 아이디로 인증 실패 (NumberFormatException catch 확인 포함)
        boolean notFound = employeeService.authenticate("999999", "plain_login_pass");
        assertThat(notFound).isFalse();
        
        boolean notFoundStr = employeeService.authenticate("nosuchuser", "plain_login_pass");
        assertThat(notFoundStr).isFalse();
    }

    /**
     * 사원 수정 시 낙관적 락(Optimistic Lock)이 정상 동작하는지 검증합니다.
     * 동일 데이터를 두 트랜잭션이 동시에 수정할 때 충돌을 감지합니다.
     */
    @Test
    @DisplayName("사원 수정 - 낙관적 락 충돌 검증")
    void optimisticLockTest() {
        // given: 사원 등록
        EmployeeDTO dto = EmployeeDTO.builder()
                .deptId(1L).positionId(1L)
                .password("raw_password_123")
                .name("박충돌")
                .email("park@ees.com")
                .hireDate(LocalDate.of(2025, 6, 1))
                .build();
        EmployeeDTO saved = employeeService.registerEmployee(dto, List.of(1L)); // ROLE_USER (role_id=1)

        // when: 두 개의 트랜잭션이 동일 데이터를 조회
        EmployeeDTO tx1 = employeeService.getEmployeeById(saved.empId());
        EmployeeDTO tx2 = employeeService.getEmployeeById(saved.empId());

        // then: 첫 번째 트랜잭션 수정 성공
        EmployeeDTO updatedTx1 = EmployeeDTO.builder()
                .empId(tx1.empId()).deptId(tx1.deptId()).positionId(tx1.positionId())
                .name("수정성공")
                .email(tx1.email()).hireDate(tx1.hireDate())
                .isDeleted(tx1.isDeleted()).version(tx1.version())
                .createdAt(tx1.createdAt()).createdBy(tx1.createdBy())
                .build();
        employeeService.updateEmployee(updatedTx1);

        // then: 두 번째 트랜잭션 수정 시도 → 버전 충돌 예외 발생
        EmployeeDTO updatedTx2 = EmployeeDTO.builder()
                .empId(tx2.empId()).deptId(tx2.deptId()).positionId(tx2.positionId())
                .name("충돌예정")
                .email(tx2.email()).hireDate(tx2.hireDate())
                .isDeleted(tx2.isDeleted()).version(tx2.version())
                .createdAt(tx2.createdAt()).createdBy(tx2.createdBy())
                .build();
        assertThatThrownBy(() -> employeeService.updateEmployee(updatedTx2))
                .isInstanceOf(EesOptimisticLockException.class);
    }


    /**
     * Java 21 Pattern Matching for switch를 활용한 권한별 접근 레벨 판별을 검증합니다.
     */
    @Test
    @DisplayName("권한별 접근 레벨 - Pattern Matching 검증")
    void accessPrivilegePatternMatchingTest() {
        // Pattern Matching for switch 로직 검증
        assertThat(employeeService.checkAccessPrivilege("ROLE_ADMIN"))
                .contains("전체 시스템 접근 허용");
        assertThat(employeeService.checkAccessPrivilege("ROLE_MANAGER"))
                .contains("부서 관리");
        assertThat(employeeService.checkAccessPrivilege("ROLE_USER"))
                .contains("본인 평가 조회");
        assertThat(employeeService.checkAccessPrivilege("ROLE_UNKNOWN"))
                .contains("알 수 없는 권한");
    }
}
