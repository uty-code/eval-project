package com.ees.eval.controller;

import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 부서(Department) 관리 화면 요청을 처리하는 컨트롤러입니다.
 * 부서 목록 조회, 등록, 수정, 삭제(소프트 델리트) 기능을 담당합니다.
 * 관리자(ADMIN) 권한 이상만 접근 가능합니다.
 */
@Slf4j
@Controller
@RequestMapping("/departments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DepartmentController {

    private final DepartmentService departmentService;

    /**
     * 부서 목록 화면을 반환합니다.
     * 검색 파라미터를 받아 필터링된 결과를 제공할 수 있습니다.
     *
     * @param searchKeyword 검색어 (부서명 또는 부서코드)
     * @param searchStatus 사용 여부 ('y', 'n', 또는 기본값 빈 문자열)
     * @param model 뷰에 데이터를 전달하는 Model 객체
     * @return 부서 목록 뷰 이름
     */
    @GetMapping
    public String listDepartments(
            @RequestParam(value = "searchKeyword", required = false) String searchKeyword,
            @RequestParam(value = "searchStatus", required = false) String searchStatus,
            Model model) {

        // 검색 조건이 있으면 필터링된 목록, 없으면 전체(트리형) 목록 반환
        List<DepartmentDTO> departments = departmentService.searchDepartments(searchKeyword, searchStatus);

        // Thymeleaf에서 Stream API를 직접 사용할 수 없으므로 통계를 미리 계산하여 모델에 전달
        // 전체 부서수는 검색 결과 목록 size를 참조하므로 따로 넘길 필요가 없음
        int totalEmployeeCount = departments.stream()
                .mapToInt(d -> d.employeeCount() != null ? d.employeeCount() : 0)
                .sum();
        long rootDeptCount = departments.stream()
                .filter(d -> d.parentDeptId() == null)
                .count();

        model.addAttribute("departments", departments);
        model.addAttribute("totalEmployeeCount", totalEmployeeCount);
        model.addAttribute("rootDeptCount", rootDeptCount);
        
        // 검색 폼 유지를 위한 파라미터 전달
        model.addAttribute("searchKeyword", searchKeyword);
        model.addAttribute("searchStatus", searchStatus);
        
        return "departments/list";
    }

    /**
     * 신규 부서 등록 폼 화면을 반환합니다.
     *
     * @param model 뷰에 데이터를 전달하는 Model 객체
     * @return 부서 등록 폼 뷰 이름
     */
    @GetMapping("/new")
    public String newDepartmentForm(Model model) {
        // 빈 DTO 및 상위 부서 선택을 위한 전체 목록 제공
        model.addAttribute("department", DepartmentDTO.builder().build());
        model.addAttribute("allDepartments", departmentService.getAllDepartments());
        model.addAttribute("isNew", true);
        return "departments/form";
    }

    /**
     * 신규 부서를 등록합니다.
     *
     * @param deptName          등록할 부서 명칭
     * @param parentDeptId      상위 부서 ID (최상위 부서인 경우 null)
     * @param redirectAttributes 리다이렉트 시 전달할 Flash 메시지
     * @return 부서 목록 화면으로 리다이렉트
     */
    @PostMapping
    public String createDepartment(
            @RequestParam("deptName") String deptName,
            @RequestParam(value = "parentDeptId", required = false) Long parentDeptId,
            RedirectAttributes redirectAttributes) {
        try {
            // DTO 빌드 후 서비스 계층 호출
            DepartmentDTO dto = DepartmentDTO.builder()
                    .deptName(deptName)
                    .parentDeptId(parentDeptId)
                    .build();
            departmentService.createDepartment(dto);
            redirectAttributes.addFlashAttribute("successMessage", "'" + deptName + "' 부서가 성공적으로 등록되었습니다.");
        } catch (Exception e) {
            log.error("부서 등록 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "부서 등록 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/departments";
    }

    /**
     * 부서 수정 폼 화면을 반환합니다.
     * 리더 지정을 위해 해당 부서의 재직 중인 사원 목록도 함께 전달합니다.
     *
     * @param deptId 수정할 부서 식별자
     * @param model  뷰에 데이터를 전달하는 Model 객체
     * @return 부서 수정 폼 뷰 이름
     */
    @GetMapping("/{deptId}/edit")
    public String editDepartmentForm(@PathVariable Long deptId, Model model) {
        // 해당 부서 정보 및 상위 부서 선택용 목록 조회
        DepartmentDTO department = departmentService.getDepartmentById(deptId);
        model.addAttribute("department", department);
        model.addAttribute("allDepartments", departmentService.getAllDepartments());
        // 해당 부서 소속 사원 목록 (리더 후보 Select용)
        model.addAttribute("deptEmployees", departmentService.getEmployeesByDeptId(deptId));
        model.addAttribute("isNew", false);
        return "departments/form";
    }

    /**
     * 부서 정보를 수정합니다.
     *
     * @param deptId            수정할 부서 식별자
     * @param deptName          변경할 부서 명칭
     * @param parentDeptId      변경할 상위 부서 ID (null 허용)
     * @param version           낙관적 락을 위한 버전 번호
     * @param redirectAttributes 리다이렉트 시 전달할 Flash 메시지
     * @return 부서 목록 화면으로 리다이렉트
     */
    @PostMapping("/{deptId}")
    public String updateDepartment(
            @PathVariable Long deptId,
            @RequestParam("deptName") String deptName,
            @RequestParam(value = "parentDeptId", required = false) Long parentDeptId,
            @RequestParam("version") Integer version,
            RedirectAttributes redirectAttributes) {
        try {
            // DTO 빌드 후 서비스 계층 호출 (낙관적 락 version 포함)
            DepartmentDTO dto = DepartmentDTO.builder()
                    .deptId(deptId)
                    .deptName(deptName)
                    .parentDeptId(parentDeptId)
                    .version(version)
                    .build();
            departmentService.updateDepartment(dto);
            redirectAttributes.addFlashAttribute("successMessage", "'" + deptName + "' 부서 정보가 수정되었습니다.");
        } catch (Exception e) {
            log.error("부서 수정 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "부서 수정 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/departments";
    }

    /**
     * 부서를 논리적으로 삭제(소프트 델리트)합니다.
     * 소속 사원이나 하위 부서가 있으면 삭제가 거부됩니다.
     *
     * @param deptId             삭제할 부서 식별자
     * @param redirectAttributes 리다이렉트 시 전달할 Flash 메시지
     * @return 부서 목록 화면으로 리다이렉트
     */
    @PostMapping("/{deptId}/delete")
    public String deleteDepartment(
            @PathVariable Long deptId,
            RedirectAttributes redirectAttributes) {
        try {
            departmentService.deleteDepartment(deptId);
            redirectAttributes.addFlashAttribute("successMessage", "부서가 성공적으로 삭제되었습니다.");
        } catch (IllegalStateException e) {
            // 소속 사원 또는 하위 부서 존재 시 삭제 거부 메시지 처리
            log.warn("부서 삭제 거부: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("부서 삭제 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "부서 삭제 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/departments";
    }

    /**
     * 부서의 사용 여부(is_active)를 전환합니다.
     * 사용중(y)이면 미사용중(n)으로, 미사용중(n)이면 사용중(y)으로 변경합니다.
     *
     * @param deptId             대상 부서 식별자
     * @param redirectAttributes 리다이렉트 시 전달할 Flash 메시지
     * @return 부서 목록 화면으로 리다이렉트
     */
    @PostMapping("/{deptId}/toggle-status")
    public String toggleDepartmentStatus(
            @PathVariable Long deptId,
            RedirectAttributes redirectAttributes) {
        try {
            departmentService.toggleDepartmentStatus(deptId);
            redirectAttributes.addFlashAttribute("successMessage", "부서 사용 상태가 변경되었습니다.");
        } catch (Exception e) {
            log.error("부서 상태 변경 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "상태 변경 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/departments";
    }

    /**
     * 부서에 리더(부서장)를 지정하거나 해제합니다.
     * 리더 지정 시 ROLE_MANAGER 권한이 자동 부여되고,
     * 기존 리더는 해제되며 다른 부서 리더가 아니면 권한이 회수됩니다.
     *
     * @param deptId             대상 부서 식별자
     * @param leaderId           리더로 지정할 사원 ID (빈 값이면 리더 해제)
     * @param redirectAttributes 리다이렉트 시 전달할 Flash 메시지
     * @return 부서 수정 폼으로 리다이렉트
     */
    @PostMapping("/{deptId}/assign-leader")
    public String assignLeader(
            @PathVariable Long deptId,
            @RequestParam(value = "leaderId", required = false) Long leaderId,
            RedirectAttributes redirectAttributes) {
        try {
            departmentService.assignLeader(deptId, leaderId);
            if (leaderId != null) {
                redirectAttributes.addFlashAttribute("successMessage", "부서 리더가 성공적으로 지정되었습니다.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "부서 리더가 해제되었습니다.");
            }
        } catch (IllegalArgumentException e) {
            log.warn("리더 지정 거부: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("리더 지정 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "리더 지정 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/departments/" + deptId + "/edit";
    }
}
