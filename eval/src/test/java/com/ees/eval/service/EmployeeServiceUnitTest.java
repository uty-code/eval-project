package com.ees.eval.service;

import com.ees.eval.domain.Employee;
import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.mapper.*;
import com.ees.eval.service.impl.EmployeeServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * EmployeeService의 순수 단위 테스트 클래스입니다.
 * DB 연동 없이 Mockito를 사용하여 비즈니스 로직(암호화, 인증, 권한 검증 등)만 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeService 단위 테스트 (Mockito)")
class EmployeeServiceUnitTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private PositionMapper positionMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private LoginLogMapper loginLogMapper;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    /**
     * 사원 등록 시 비밀번호가 BCrypt로 암호화되고,
     * 권한 매핑 및 insert 호출이 올바르게 수행되는지 검증합니다.
     */
    @Test
    @DisplayName("사원 등록 시 비밀번호가 암호화되고 기본 상태값이 설정되어야 한다.")
    void registerEmployee_EncryptsPasswordAndSetsDefaults() {
        // given: 평문 비밀번호를 포함한 사원 등록 DTO 준비
        EmployeeDTO inputDto = EmployeeDTO.builder()
                .name("테스터")
                .password("plainPassword")
                .deptId(1L)
                .positionId(1L)
                .hireDate(java.time.LocalDate.of(2026, 1, 1))
                .build();

        given(passwordEncoder.encode("plainPassword")).willReturn("encryptedPassword");

        // given: insert 호출 시 DB가 ID를 부여하는 흐름을 시뮬레이션
        // registerEmployee 내부에서 insert 후 getEmployeeById로 재조회하므로,
        // insert 시점에 empId를 직접 주입해야 이후 findById 스텁이 정상 작동함
        Employee savedEmployee = Employee.builder()
                .empId(100L)
                .name("테스터")
                .password("encryptedPassword")
                .deptId(1L)
                .positionId(1L)
                .statusCode("EMPLOYED")
                .build();

        given(employeeMapper.insert(any(Employee.class))).willAnswer(invocation -> {
            Employee e = invocation.getArgument(0);
            e.setEmpId(100L); // DB의 IDENTITY 컬럼 자동 생성 시뮬레이션
            return 1;
        });

        given(employeeMapper.findById(100L)).willReturn(Optional.of(savedEmployee));
        given(employeeMapper.findRoleNamesByEmpId(100L)).willReturn(List.of("ROLE_USER"));
        given(departmentMapper.findById(1L)).willReturn(Optional.empty());
        given(positionMapper.findById(1L)).willReturn(Optional.empty());

        // when: 사원 등록 수행
        EmployeeDTO result = employeeService.registerEmployee(inputDto, List.of(1L));

        // then: 결과 검증
        assertThat(result.empId()).isEqualTo(100L);
        assertThat(result.roleNames()).contains("ROLE_USER");

        // then: 암호화 및 insert, 권한 매핑 호출 여부 검증
        verify(passwordEncoder).encode("plainPassword");
        verify(employeeMapper).insert(any(Employee.class));
        verify(employeeMapper, times(1)).insertEmployeeRole(eq(100L), eq(1L), anyLong(), any());
    }

    /**
     * 올바른 비밀번호 입력 시 인증에 성공(true)을 반환하는지 검증합니다.
     */
    @Test
    @DisplayName("로그인 인증 시 비밀번호가 일치하면 true를 반환해야 한다.")
    void authenticate_Success() {
        // given
        Long empId = 12345L;
        String rawPassword = "password123";
        String encodedPassword = "encodedPassword123";

        Employee employee = Employee.builder()
                .empId(empId)
                .password(encodedPassword)
                .statusCode("EMPLOYED")
                .build();

        given(employeeMapper.findByIdForAuth(empId)).willReturn(Optional.of(employee));
        given(loginLogMapper.countRecentFailures(empId)).willReturn(0);
        given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(true);

        // when
        boolean result = employeeService.authenticate(String.valueOf(empId), rawPassword);

        // then
        assertThat(result).isTrue();
    }

    /**
     * 퇴사 상태(RETIRED)인 사원의 인증 시도는 비밀번호 확인 없이 즉시 실패해야 합니다.
     */
    @Test
    @DisplayName("퇴사한 사원은 인증에 실패해야 한다.")
    void authenticate_Fail_Retired() {
        // given
        Long empId = 12345L;
        Employee employee = Employee.builder()
                .empId(empId)
                .statusCode("RETIRED")
                .build();

        given(employeeMapper.findByIdForAuth(empId)).willReturn(Optional.of(employee));

        // when
        boolean result = employeeService.authenticate(String.valueOf(empId), "any");

        // then
        assertThat(result).isFalse();
    }

    /**
     * 존재하지 않는 사원 ID 또는 숫자가 아닌 문자열로 인증 시도 시 false를 반환해야 합니다.
     *
     * <p>내부에서 NumberFormatException이 발생하는 경우(문자열 ID)와
     * DB에서 사원을 찾지 못한 경우를 모두 커버합니다.</p>
     */
    @Test
    @DisplayName("존재하지 않는 사원 또는 잘못된 형식의 ID는 인증에 실패해야 한다.")
    void authenticate_Fail_NotFound() {
        // given: DB에 존재하지 않는 사원 ID
        given(employeeMapper.findByIdForAuth(999999L)).willReturn(Optional.empty());

        // when & then: 존재하지 않는 숫자 ID → false
        boolean notFound = employeeService.authenticate("999999", "anyPassword");
        assertThat(notFound).isFalse();

        // when & then: 숫자가 아닌 문자열 ID → NumberFormatException 내부 처리 후 false
        boolean notFoundStr = employeeService.authenticate("nosuchuser", "anyPassword");
        assertThat(notFoundStr).isFalse();
    }

    /**
     * Java 21 Pattern Matching for switch를 활용한 권한별 접근 레벨 판별을 검증합니다.
     * 이 메서드는 순수 로직이므로 DB 연동이 전혀 필요하지 않습니다.
     */
    @Test
    @DisplayName("권한별 접근 레벨 - Pattern Matching 검증")
    void checkAccessPrivilege_ShouldReturnCorrectMessage() {
        // when & then: 각 권한명에 따른 접근 레벨 메시지 검증
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
