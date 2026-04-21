package com.ees.eval.controller;

import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.dto.EmployeePageDTO;
import com.ees.eval.service.DepartmentService;
import com.ees.eval.service.EmployeeService;
import com.ees.eval.service.PositionService;
import com.ees.eval.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * EmployeeController의 Standalone 테스트 클래스입니다.
 * Spring Boot 자동 설정 없이 MockMvc를 직접 구성하여 컨트롤러 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private DepartmentService departmentService;

    @Mock
    private PositionService positionService;

    @Mock
    private RoleService roleService;

    @Mock(name = "virtualThreadExecutor")
    private Executor virtualThreadExecutor;

    @InjectMocks
    private EmployeeController employeeController;

    /**
     * 각 테스트 실행 전 MockMvc를 수동으로 빌드하여 셋업합니다.
     */
    @BeforeEach
    void setUp() {
        // 독립 실행형 설정으로 MockMvc 초기화 (Spring Boot 설정 의존성 제거)
        mockMvc = MockMvcBuilders.standaloneSetup(employeeController).build();
    }

    @Test
    @DisplayName("사원 목록 조회 - 정상 호출 시 목록 뷰를 반환한다")
    void listEmployees_ShouldReturnListView() throws Exception {
        // given: void 메서드인 Executor.execute 모킹
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(virtualThreadExecutor).execute(any());

        EmployeePageDTO mockPage = EmployeePageDTO.of(List.of(), 1, 10, 0L);
        given(employeeService.searchEmployeesPage(any(), any(), any(), anyInt(), anyInt())).willReturn(mockPage);
        given(departmentService.getAllDepartments()).willReturn(List.of());
        given(positionService.getAllPositions()).willReturn(List.of());
        given(employeeService.countActiveEmployees()).willReturn(10L);
        given(employeeService.countThisYearHired()).willReturn(2L);
        given(employeeService.countLockedEmployees()).willReturn(1L);

        // when & then
        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(view().name("employees/list"))
                .andExpect(model().attributeExists("page", "departments", "positions", "activeCount"));
    }

    @Test
    @DisplayName("신규 사원 등록 성공 - 유효한 데이터 입력 시 목록으로 리다이렉트된다")
    void createEmployee_Success_ShouldRedirect() throws Exception {
        // given
        EmployeeDTO savedDto = EmployeeDTO.builder().empId(1001L).build();
        given(employeeService.registerEmployee(any(EmployeeDTO.class), anyList())).willReturn(savedDto);

        // when & then
        mockMvc.perform(post("/employees")
                        .param("name", "홍길동")
                        .param("email", "hong@example.com")
                        .param("phone", "010-1234-5678")
                        .param("deptId", "1")
                        .param("positionId", "2")
                        .param("hireDate", "2024-01-01")
                        .param("password", "password123")
                        .param("roleIds", "1", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(employeeService).registerEmployee(any(EmployeeDTO.class), anyList());
    }

    @Test
    @DisplayName("사원 수정 폼 진입 - 정상 호출 시 수정 폼 뷰와 사원 데이터를 반환한다")
    void editEmployeeForm_ShouldReturnFormView() throws Exception {
        // given
        EmployeeDTO mockEmployee = EmployeeDTO.builder().empId(1001L).name("홍길동").build();
        given(employeeService.getEmployeeById(1001L)).willReturn(mockEmployee);
        given(departmentService.getAllDepartments()).willReturn(List.of());
        given(positionService.getAllPositions()).willReturn(List.of());
        given(roleService.getAllRoles()).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/employees/1001/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("employees/form"))
                .andExpect(model().attribute("employee", mockEmployee))
                .andExpect(model().attribute("isNew", false));
    }

    @Test
    @DisplayName("사원 정보 수정 성공 - 유효한 데이터 입력 시 목록으로 리다이렉트된다")
    void updateEmployee_Success_ShouldRedirect() throws Exception {
        // given
        // updateEmployee 메서드가 예외 없이 실행됨을 가정

        // when & then
        mockMvc.perform(post("/employees/1001")
                        .param("name", "홍길동수정")
                        .param("email", "update@example.com")
                        .param("phone", "010-9999-9999")
                        .param("deptId", "2")
                        .param("positionId", "3")
                        .param("statusCode", "ACTIVE")
                        .param("hireDate", "2024-01-01")
                        .param("version", "1")
                        .param("roleIds", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(employeeService).updateEmployee(any(EmployeeDTO.class), anyList());
    }

    @Test
    @DisplayName("사원 정보 수정 실패 - 낙관적 락 충돌 시 에러 메시지를 반환한다")
    void updateEmployee_Fail_OptimisticLocking_ShouldShowError() throws Exception {
        // given: 인자가 하나인 updateEmployee 메서드에서 예외 발생 시뮬레이션
        doAnswer(invocation -> { throw new RuntimeException("다른 사용자에 의해 변경되었습니다."); })
                .when(employeeService).updateEmployee(any(EmployeeDTO.class));

        // when & then
        mockMvc.perform(post("/employees/1001")
                        .param("name", "홍길동수정")
                        .param("email", "update@example.com")
                        .param("phone", "010-9999-9999")
                        .param("deptId", "2")
                        .param("positionId", "3")
                        .param("statusCode", "ACTIVE")
                        .param("hireDate", "2024-01-01")
                        .param("version", "1"))
                // roleIds를 보내지 않으므로 updateEmployee(dto)가 호출됨
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "사원 수정 중 오류가 발생했습니다: 다른 사용자에 의해 변경되었습니다."));
    }

    @Test
    @DisplayName("비밀번호 초기화 성공 - 호출 시 사번으로 비밀번호가 재설정된다")
    void resetPassword_ShouldRedirectWithSuccessMessage() throws Exception {
        // given
        EmployeeDTO mockEmployee = EmployeeDTO.builder()
                .empId(1001L)
                .name("홍길동")
                .email("hong@example.com")
                .hireDate(LocalDate.now())
                .version(1)
                .build();
        given(employeeService.getEmployeeById(1001L)).willReturn(mockEmployee);

        // when & then
        mockMvc.perform(post("/employees/1001/reset-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees"))
                .andExpect(flash().attribute("successMessage", "비밀번호가 초기화되었습니다. (초기 비밀번호: 사번과 동일)"));

        verify(employeeService).updateEmployee(any(EmployeeDTO.class));
    }

    @Test
    @DisplayName("사원 등록 실패 - 잘못된 이메일 양식 입력 시 에러 메시지와 함께 리다이렉트된다")
    void createEmployee_Fail_InvalidEmail_ShouldShowError() throws Exception {
        // when & then
        mockMvc.perform(post("/employees")
                        .param("name", "홍길동")
                        .param("email", "invalid-email")
                        .param("phone", "010-1234-5678")
                        .param("deptId", "1")
                        .param("positionId", "2")
                        .param("hireDate", "2024-01-01")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("계정 잠금 해제 - 정상 처리 시 목록으로 리다이렉트된다")
    void unlockAccount_ShouldRedirect() throws Exception {
        // when & then
        mockMvc.perform(post("/employees/1001/unlock"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(employeeService).unlockAccount(1001L);
    }
}
