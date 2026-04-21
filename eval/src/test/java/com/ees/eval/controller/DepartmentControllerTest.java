package com.ees.eval.controller;

import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.service.DepartmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DepartmentController의 Standalone 테스트 클래스입니다.
 * 부서 목록 조회, 등록, 수정, 삭제 및 리더 지정 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class DepartmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DepartmentService departmentService;

    @InjectMocks
    private DepartmentController departmentController;

    @BeforeEach
    void setUp() {
        // 독립 실행형 설정으로 MockMvc 초기화
        mockMvc = MockMvcBuilders.standaloneSetup(departmentController).build();
    }

    @Test
    @DisplayName("부서 목록 조회 - 검색 조건 없이 호출 시 전체 목록과 통계 데이터를 반환한다")
    void listDepartments_NoParams_ShouldReturnListView() throws Exception {
        // given
        List<DepartmentDTO> mockDepts = List.of(
                DepartmentDTO.builder().deptId(1L).deptName("개발부").employeeCount(10).build(),
                DepartmentDTO.builder().deptId(2L).deptName("인사부").employeeCount(5).build()
        );
        given(departmentService.searchDepartments(null, null)).willReturn(mockDepts);

        // when & then
        mockMvc.perform(get("/departments"))
                .andExpect(status().isOk())
                .andExpect(view().name("departments/list"))
                .andExpect(model().attribute("departments", mockDepts))
                .andExpect(model().attribute("totalEmployeeCount", 15))
                .andExpect(model().attribute("rootDeptCount", 2L));
    }

    @Test
    @DisplayName("신규 부서 등록 성공 - 유효한 이름 입력 시 목록으로 리다이렉트된다")
    void createDepartment_Success_ShouldRedirect() throws Exception {
        // when & then
        mockMvc.perform(post("/departments")
                        .param("deptName", "신규부서")
                        .param("parentDeptId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/departments"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(departmentService).createDepartment(any(DepartmentDTO.class));
    }

    @Test
    @DisplayName("부서 수정 성공 - 수정된 정보 반영 후 목록으로 리다이렉트된다")
    void updateDepartment_Success_ShouldRedirect() throws Exception {
        // when & then
        mockMvc.perform(post("/departments/1")
                        .param("deptName", "수정부서")
                        .param("version", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/departments"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(departmentService).updateDepartment(any(DepartmentDTO.class));
    }

    @Test
    @DisplayName("부서 삭제 실패 - 소속 사원이 있을 경우 에러 메시지를 반환한다")
    void deleteDepartment_Fail_HasEmployees_ShouldShowError() throws Exception {
        // given
        doAnswer(invocation -> { throw new IllegalStateException("소속 사원이 존재하여 삭제할 수 없습니다."); })
                .when(departmentService).deleteDepartment(1L);

        // when & then
        mockMvc.perform(post("/departments/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "소속 사원이 존재하여 삭제할 수 없습니다."));
    }

    @Test
    @DisplayName("부서 리더 지정 - 정상 호출 시 해당 부서 정보 페이지로 리다이렉트된다")
    void assignLeader_ShouldRedirectToEditForm() throws Exception {
        // when & then
        mockMvc.perform(post("/departments/1/assign-leader")
                        .param("leaderId", "1001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/departments/1/edit"))
                .andExpect(flash().attribute("successMessage", "부서 리더가 성공적으로 지정되었습니다."));

        verify(departmentService).assignLeader(1L, 1001L);
    }

    @Test
    @DisplayName("부서 사용 상태 토글 - 호출 시 성공 메시지와 함께 리다이렉트된다")
    void toggleDepartmentStatus_ShouldRedirect() throws Exception {
        // when & then
        mockMvc.perform(post("/departments/1/toggle-status"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/departments"))
                .andExpect(flash().attribute("successMessage", "부서 사용 상태가 변경되었습니다."));

        verify(departmentService).toggleDepartmentStatus(1L);
    }
}
