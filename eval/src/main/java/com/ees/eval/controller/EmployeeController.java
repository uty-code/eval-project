package com.ees.eval.controller;

import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.dto.PositionDTO;
import com.ees.eval.dto.RoleDTO;
import com.ees.eval.service.DepartmentService;
import com.ees.eval.service.EmployeeService;
import com.ees.eval.service.PositionService;
import com.ees.eval.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * 사원(Employee) 관리 화면 요청을 처리하는 컨트롤러입니다.
 * 사원 목록 조회, 신규 등록, 정보 수정, 비밀번호 초기화, 논리적 삭제(퇴사 처리) 기능을 담당합니다.
 * 관리자(ADMIN) 권한 이상만 접근 가능합니다.
 */
@Slf4j
@Controller
@RequestMapping("/employees")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final DepartmentService departmentService;
    private final PositionService positionService;
    private final RoleService roleService;

    /**
     * 사원 목록 화면을 반환합니다.
     * 검색 조건(이름, 부서, 재직 상태)에 따라 필터링된 목록을 제공합니다.
     *
     * @param searchName   검색할 사원 성명 (부분 일치, 선택)
     * @param searchDeptId 검색할 부서 ID (선택)
     * @param searchStatus 검색할 재직 상태 코드 (선택, 예: ACTIVE/LEAVE/RETIRED)
     * @param model        뷰에 데이터를 전달하는 Model 객체
     * @return 사원 목록 뷰 이름
     */
    @GetMapping
    public String listEmployees(
            @RequestParam(value = "searchName", required = false) String searchName,
            @RequestParam(value = "searchDeptId", required = false) Long searchDeptId,
            @RequestParam(value = "searchStatus", required = false) String searchStatus,
            Model model) {

        // 전체 사원 목록 조회 (소프트 델리트 제외)
        List<EmployeeDTO> employees = employeeService.getAllEmployees();

        // 클라이언트 사이드 필터링을 위해 전체 부서 및 직급 목록 조회
        List<DepartmentDTO> departments = departmentService.getAllDepartments();
        List<PositionDTO> positions = positionService.getAllPositions();

        // 통계 수치 계산 (Thymeleaf에서 Stream 직접 사용 불가하므로 미리 계산)
        long activeCount = employees.stream()
                .filter(e -> !"y".equalsIgnoreCase(e.isDeleted()))
                .count();
        long thisYearHired = employees.stream()
                .filter(e -> e.hireDate() != null && e.hireDate().getYear() == LocalDate.now().getYear())
                .count();

        model.addAttribute("employees", employees);
        model.addAttribute("departments", departments);
        model.addAttribute("positions", positions);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("thisYearHired", thisYearHired);
        // 검색 조건을 뷰로 다시 전달하여 폼 상태 유지
        model.addAttribute("searchName", searchName);
        model.addAttribute("searchDeptId", searchDeptId);
        model.addAttribute("searchStatus", searchStatus);

        return "employees/list";
    }

    /**
     * 신규 사원 등록 폼 화면을 반환합니다.
     *
     * @param model 뷰에 데이터를 전달하는 Model 객체
     * @return 사원 등록 폼 뷰 이름
     */
    @GetMapping("/new")
    public String newEmployeeForm(Model model) {
        // 빈 DTO 및 셀렉트 박스용 목록 제공
        model.addAttribute("employee", EmployeeDTO.builder().build());
        model.addAttribute("departments", departmentService.getAllDepartments());
        model.addAttribute("positions", positionService.getAllPositions());
        model.addAttribute("roles", roleService.getAllRoles());
        model.addAttribute("isNew", true);
        return "employees/form";
    }

    /**
     * 신규 사원을 등록합니다.
     * 비밀번호는 서비스 계층에서 BCrypt로 암호화됩니다.
     *
     * @param username           로그인 아이디
     * @param name               사원 성명
     * @param email              이메일
     * @param deptId             소속 부서 ID
     * @param positionId         직급 ID
     * @param hireDate           입사일
     * @param password           초기 비밀번호 (평문)
     * @param roleIds            부여할 권한 ID 목록
     * @param redirectAttributes 리다이렉트 시 전달할 Flash 메시지
     * @return 사원 목록 화면으로 리다이렉트
     */
    @PostMapping
    public String createEmployee(
            @RequestParam("username") String username,
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam("deptId") Long deptId,
            @RequestParam("positionId") Long positionId,
            @RequestParam("hireDate") LocalDate hireDate,
            @RequestParam("password") String password,
            @RequestParam(value = "roleIds", required = false) List<Long> roleIds,
            RedirectAttributes redirectAttributes) {
        try {
            // DTO 빌드 후 서비스 계층 호출 (비밀번호 암호화는 ServiceImpl에서 처리)
            EmployeeDTO dto = EmployeeDTO.builder()
                    .username(username)
                    .name(name)
                    .email(email)
                    .deptId(deptId)
                    .positionId(positionId)
                    .hireDate(hireDate)
                    .password(password)
                    .build();

            employeeService.registerEmployee(dto, roleIds != null ? roleIds : List.of());
            redirectAttributes.addFlashAttribute("successMessage",
                    "'" + name + "' 사원이 성공적으로 등록되었습니다.");
        } catch (Exception e) {
            log.error("사원 등록 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "사원 등록 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/employees";
    }

    /**
     * 사원 수정 폼 화면을 반환합니다.
     *
     * @param empId 수정할 사원 식별자
     * @param model 뷰에 데이터를 전달하는 Model 객체
     * @return 사원 수정 폼 뷰 이름
     */
    @GetMapping("/{empId}/edit")
    public String editEmployeeForm(@PathVariable Long empId, Model model) {
        // 해당 사원 정보 및 셀렉트 박스용 목록 조회
        EmployeeDTO employee = employeeService.getEmployeeById(empId);
        model.addAttribute("employee", employee);
        model.addAttribute("departments", departmentService.getAllDepartments());
        model.addAttribute("positions", positionService.getAllPositions());
        model.addAttribute("roles", roleService.getAllRoles());
        model.addAttribute("isNew", false);
        return "employees/form";
    }

    /**
     * 사원 정보를 수정합니다.
     * 낙관적 락(version)을 통한 동시성 제어가 수행됩니다.
     *
     * @param empId              수정할 사원 식별자
     * @param name               변경할 사원 성명
     * @param email              변경할 이메일
     * @param deptId             변경할 부서 ID
     * @param positionId         변경할 직급 ID
     * @param hireDate           변경할 입사일
     * @param version            낙관적 락을 위한 버전 번호
     * @param redirectAttributes 리다이렉트 시 전달할 Flash 메시지
     * @return 사원 목록 화면으로 리다이렉트
     */
    @PostMapping("/{empId}")
    public String updateEmployee(
            @PathVariable Long empId,
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam("deptId") Long deptId,
            @RequestParam("positionId") Long positionId,
            @RequestParam("hireDate") LocalDate hireDate,
            @RequestParam("version") Integer version,
            RedirectAttributes redirectAttributes) {
        try {
            // DTO 빌드 후 서비스 계층 호출 (낙관적 락 version 포함)
            EmployeeDTO dto = EmployeeDTO.builder()
                    .empId(empId)
                    .name(name)
                    .email(email)
                    .deptId(deptId)
                    .positionId(positionId)
                    .hireDate(hireDate)
                    .version(version)
                    .build();

            employeeService.updateEmployee(dto);
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
     * 사원을 논리적으로 삭제(Soft Delete) 처리합니다.
     * 실제 데이터를 제거하지 않고 is_deleted 플래그를 'y'로 변경합니다.
     *
     * @param empId              삭제할 사원 식별자
     * @param redirectAttributes 리다이렉트 시 전달할 Flash 메시지
     * @return 사원 목록 화면으로 리다이렉트
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
     * 사원 비밀번호를 초기값으로 재설정합니다.
     * 초기 비밀번호는 사번(username)과 동일하게 설정됩니다.
     *
     * @param empId              초기화 대상 사원 식별자
     * @param redirectAttributes 리다이렉트 시 전달할 Flash 메시지
     * @return 사원 목록 화면으로 리다이렉트
     */
    @PostMapping("/{empId}/reset-password")
    public String resetPassword(
            @PathVariable Long empId,
            RedirectAttributes redirectAttributes) {
        try {
            // 사원 조회 후 비밀번호를 사번과 동일하게 재설정
            EmployeeDTO employee = employeeService.getEmployeeById(empId);
            EmployeeDTO resetDto = EmployeeDTO.builder()
                    .empId(empId)
                    .password(employee.username()) // 초기 비밀번호: 사번과 동일
                    .version(employee.version())
                    .build();
            employeeService.updateEmployee(resetDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "비밀번호가 초기화되었습니다. (초기 비밀번호: 사번과 동일)");
        } catch (Exception e) {
            log.error("비밀번호 초기화 실패 (empId={}): {}", empId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "비밀번호 초기화 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/employees";
    }
}
