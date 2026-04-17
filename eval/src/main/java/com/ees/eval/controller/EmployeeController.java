package com.ees.eval.controller;

import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.dto.PositionDTO;
import com.ees.eval.dto.RoleDTO;
import com.ees.eval.service.DepartmentService;
import com.ees.eval.service.EmployeeService;
import com.ees.eval.service.PositionService;
import com.ees.eval.service.RoleService;
import com.ees.eval.util.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 사원(Employee) 관리 화면 요청을 처리하는 컨트롤러입니다.
 * 사원 목록 조회, 신규 등록, 정보 수정, 비밀번호 초기화, 논리적 삭제(퇴사 처리) 기능을 담당합니다.
 * 관리자(ADMIN) 권한 이상만 접근 가능합니다.
 *
 * <p>통계 데이터(pendingCount, lockedCount, activeCount, totalEmployeeCount, thisYearHired)는
 * GlobalModelAdvice에서 모든 요청에 전역 주입되므로 이 컨트롤러에서 별도로 조회하지 않습니다.</p>
 */
@Slf4j
@Controller
@RequestMapping("/employees")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final DepartmentService departmentService;
    @Qualifier("virtualThreadExecutor")
    private final Executor virtualThreadExecutor;
    private final PositionService positionService;
    private final RoleService roleService;

    /**
     * 사원 목록 화면을 반환합니다.
     * 검색 조건(이름, 부서, 재직 상태)에 따라 필터링된 목록을 제공합니다.
     * 통계 데이터는 GlobalModelAdvice에서 자동 주입됩니다.
     */
    @GetMapping
    public String listEmployees(
            @RequestParam(value = "searchName", required = false) String searchName,
            @RequestParam(value = "searchDeptId", required = false) Long searchDeptId,
            @RequestParam(value = "searchStatus", required = false) String searchStatus,
            @RequestParam(value = "page", defaultValue = "1") int pageNum,
            Model model) {

        final String finalName   = (searchName != null && !searchName.isBlank()) ? searchName.trim() : null;
        final String finalStatus = (searchStatus != null && !searchStatus.isBlank()) ? searchStatus : null;
        final int pageSize = 10;

        // -- 병렬 조회: 페이지 데이터 · 부서 목록 · 직급 목록만 조회 (통계는 GlobalModelAdvice가 담당)
        CompletableFuture<com.ees.eval.dto.EmployeePageDTO> pageFuture = CompletableFuture.supplyAsync(
                () -> employeeService.searchEmployeesPage(finalName, searchDeptId, finalStatus, pageNum, pageSize),
                virtualThreadExecutor);

        CompletableFuture<List<DepartmentDTO>> departmentsFuture = CompletableFuture.supplyAsync(
                departmentService::getAllDepartments,
                virtualThreadExecutor);

        CompletableFuture<List<PositionDTO>> positionsFuture = CompletableFuture.supplyAsync(
                positionService::getAllPositions,
                virtualThreadExecutor);

        CompletableFuture.allOf(pageFuture, departmentsFuture, positionsFuture).join();

        com.ees.eval.dto.EmployeePageDTO page = pageFuture.join();

        model.addAttribute("page", page);
        model.addAttribute("employees", page.employees());
        model.addAttribute("departments", departmentsFuture.join());
        model.addAttribute("positions", positionsFuture.join());
        model.addAttribute("searchName", searchName);
        model.addAttribute("searchDeptId", searchDeptId);
        model.addAttribute("searchStatus", searchStatus);

        return "employees/list";
    }

    /**
     * 계정 잠금 사원 목록 화면을 반환합니다.
     * login_fail_cnt >= 5 인 사원만 표시되며 잠금 해제 버튼을 제공합니다.
     */
    @GetMapping("/locked")
    public String lockedEmployees(Model model) {
        model.addAttribute("lockedEmployees", employeeService.getLockedEmployees());
        return "employees/locked";
    }

    /**
     * 신규 사원 등록 폼 화면을 반환합니다.
     */
    @GetMapping("/new")
    public String newEmployeeForm(Model model) {
        model.addAttribute("employee", EmployeeDTO.builder().build());
        model.addAttribute("departments", departmentService.getAllDepartments());
        model.addAttribute("positions", positionService.getAllPositions());
        model.addAttribute("roles", roleService.getAllRoles());
        model.addAttribute("nextEmpId", employeeService.getNextEmpId());
        model.addAttribute("isNew", true);
        return "employees/form";
    }

    /**
     * 신규 사원을 등록합니다.
     * 비밀번호는 서비스 계층에서 BCrypt로 암호화됩니다.
     */
    @PostMapping
    public String createEmployee(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam("deptId") Long deptId,
            @RequestParam("positionId") Long positionId,
            @RequestParam("hireDate") LocalDate hireDate,
            @RequestParam("password") String password,
            @RequestParam(value = "roleIds", required = false) List<Long> roleIds,
            RedirectAttributes redirectAttributes) {
        try {
            EmployeeDTO dto = EmployeeDTO.builder()
                    .name(name)
                    .email(email)
                    .phone(PhoneUtils.validate(phone))
                    .deptId(deptId)
                    .positionId(positionId)
                    .hireDate(hireDate)
                    .password(password)
                    .build();

            EmployeeDTO savedDto = employeeService.registerEmployee(dto, roleIds != null ? roleIds : List.of());
            redirectAttributes.addFlashAttribute("successMessage",
                    "'" + name + "' 사원이 성공적으로 등록되었습니다. 발급된 사번은 [" + savedDto.empId() + "] 입니다.");
        } catch (Exception e) {
            log.error("사원 등록 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "사원 등록 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/employees";
    }

    /**
     * 사원 수정 폼 화면을 반환합니다.
     */
    @GetMapping("/{empId}/edit")
    public String editEmployeeForm(@PathVariable Long empId, Model model) {
        model.addAttribute("employee", employeeService.getEmployeeById(empId));
        model.addAttribute("departments", departmentService.getAllDepartments());
        model.addAttribute("positions", positionService.getAllPositions());
        model.addAttribute("roles", roleService.getAllRoles());
        model.addAttribute("isNew", false);
        return "employees/form";
    }

    /**
     * 사원 정보를 수정합니다.
     * 낙관적 락(version)을 통한 동시성 제어가 수행됩니다.
     */
    @PostMapping("/{empId}")
    public String updateEmployee(
            @PathVariable Long empId,
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam("deptId") Long deptId,
            @RequestParam("positionId") Long positionId,
            @RequestParam("statusCode") String statusCode,
            @RequestParam("hireDate") LocalDate hireDate,
            @RequestParam("version") Integer version,
            @RequestParam(value = "roleIds", required = false) List<Long> roleIds,
            RedirectAttributes redirectAttributes) {
        try {
            EmployeeDTO dto = EmployeeDTO.builder()
                    .empId(empId)
                    .name(name)
                    .email(email)
                    .phone(PhoneUtils.validate(phone))
                    .deptId(deptId)
                    .positionId(positionId)
                    .statusCode(statusCode)
                    .hireDate(hireDate)
                    .version(version)
                    .build();

            if (roleIds != null && !roleIds.isEmpty()) {
                employeeService.updateEmployee(dto, roleIds);
            } else {
                employeeService.updateEmployee(dto);
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "'" + name + "' 사원 정보가 수정되었습니다.");
        } catch (Exception e) {
            log.error("사원 수정 실패 (empId={}): {}", empId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "사원 수정 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/employees";
    }

    /**
     * 사원을 논리적으로 삭제(퇴사 처리)합니다.
     * 실제 데이터를 제거하지 않고 is_deleted 플래그를 'y'로 변경합니다.
     */
    @PostMapping("/{empId}/delete")
    public String deleteEmployee(
            @PathVariable Long empId,
            RedirectAttributes redirectAttributes) {
        try {
            employeeService.deleteEmployee(empId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "사원이 성공적으로 탈퇴(소프트 삭제) 처리되었습니다.");
        } catch (Exception e) {
            log.error("사원 삭제 실패 (empId={}): {}", empId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "사원 삭제 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/employees";
    }

    /**
     * 사원 비밀번호를 초기값(사번)으로 재설정합니다.
     * 서비스의 #unlockAccount와 동일한 방식을 사용하되, 실패 횟수는 초기화하지 않습니다.
     */
    @PostMapping("/{empId}/reset-password")
    public String resetPassword(
            @PathVariable Long empId,
            RedirectAttributes redirectAttributes) {
        try {
            employeeService.resetPasswordToEmpId(empId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "비밀번호가 초기화되었습니다. (초기 비밀번호: 사번과 동일)");
        } catch (Exception e) {
            log.error("비밀번호 초기화 실패 (empId={}): {}", empId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "비밀번호 초기화 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/employees";
    }

    /**
     * 계정 잠금을 해제합니다.
     * login_fail_cnt를 0으로 초기화하고 비밀번호를 사번과 동일하게 설정합니다.
     */
    @PostMapping("/{empId}/unlock")
    public String unlockAccount(
            @PathVariable Long empId,
            RedirectAttributes redirectAttributes) {
        try {
            employeeService.unlockAccount(empId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "계정 잠금이 해제되었으며, 비밀번호가 사번과 동일하게 초기화되었습니다.");
        } catch (Exception e) {
            log.error("계정 잠금 해제 실패 (empId={}): {}", empId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "계정 잠금 해제 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/employees/locked";
    }
}
