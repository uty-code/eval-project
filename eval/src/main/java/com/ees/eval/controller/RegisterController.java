package com.ees.eval.controller;

import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.dto.PositionDTO;
import com.ees.eval.service.DepartmentService;
import com.ees.eval.service.EmployeeService;
import com.ees.eval.service.PositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 사원 등록 신청(자가 등록) 및 관리자 승인/거절 기능을 담당하는 컨트롤러입니다.
 * /register는 비인증 공개 엔드포인트이며, 승인 관련 엔드포인트는 ADMIN 권한이 필요합니다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class RegisterController {

    private final EmployeeService employeeService;
    private final DepartmentService departmentService;
    private final PositionService positionService;
    @Qualifier("virtualThreadExecutor")
    private final Executor virtualThreadExecutor;

    /**
     * 사원 등록 신청 폼 화면을 반환합니다. (비인증 공개)
     */
    @GetMapping("/register")
    public String registerForm(Model model) {
        List<DepartmentDTO> departments = departmentService.getAllDepartments();
        List<PositionDTO> positions = positionService.getAllPositions();
        model.addAttribute("departments", departments);
        model.addAttribute("positions", positions);
        return "register";
    }

    /**
     * 사원 등록 신청을 처리합니다. PENDING 상태로 저장됩니다. (비인증 공개)
     */
    @PostMapping("/register")
    public String submitRegistration(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam("phone") String phone,
            @RequestParam("deptId") Long deptId,
            @RequestParam("positionId") Long positionId,
            @RequestParam("hireDate") LocalDate hireDate,
            @RequestParam("password") String password,
            RedirectAttributes redirectAttributes) {
        try {
            if (phone == null || !phone.matches("^010-\\d{4}-\\d{4}$")) {
                throw new IllegalArgumentException("전화번호 양식이 올바르지 않습니다. (예: 010-1111-2222)");
            }
            if (email == null || !email.matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")) {
                throw new IllegalArgumentException("이메일 양식이 올바르지 않습니다. (예: example@domain.com)");
            }
            EmployeeDTO dto = EmployeeDTO.builder()
                    .name(name)
                    .email(email)
                    .phone(phone)
                    .deptId(deptId)
                    .positionId(positionId)
                    .hireDate(hireDate)
                    .password(password)
                    .statusCode("PENDING") // 관리자 승인 대기 상태
                    .build();

            employeeService.registerEmployee(dto, List.of());
            redirectAttributes.addFlashAttribute("successMessage",
                    "등록 신청이 완료되었습니다! 관리자 승인 후 로그인이 가능합니다.");
        } catch (Exception e) {
            log.error("사원 등록 신청 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "등록 신청 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/register";
    }

    /**
     * 관리자용 - 승인 대기 사원 목록 조회 (사원 관리 페이지에서 탭으로 보여주므로
     * /employees/pending으로 접근하여 사원관리 레이아웃 재사용)
     */
    @GetMapping("/employees/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public String pendingList(Model model) {
        List<EmployeeDTO> pendingEmployees = employeeService.getPendingEmployees();
        model.addAttribute("pendingEmployees", pendingEmployees);

        CompletableFuture<Long> activeCountFuture = CompletableFuture.supplyAsync(
                employeeService::countActiveEmployees,
                virtualThreadExecutor);

        CompletableFuture<Long> thisYearHiredFuture = CompletableFuture.supplyAsync(
                employeeService::countThisYearHired,
                virtualThreadExecutor);

        CompletableFuture<Long> totalEmployeeCountFuture = CompletableFuture.supplyAsync(
                () -> employeeService.searchEmployeesPage(null, null, null, 1, 1).totalCount(),
                virtualThreadExecutor);

        CompletableFuture<Long> lockedCountFuture = CompletableFuture.supplyAsync(
                employeeService::countLockedEmployees,
                virtualThreadExecutor);

        CompletableFuture.allOf(activeCountFuture, thisYearHiredFuture, totalEmployeeCountFuture, lockedCountFuture).join();

        model.addAttribute("activeCount", activeCountFuture.join());
        model.addAttribute("thisYearHired", thisYearHiredFuture.join());
        model.addAttribute("totalEmployeeCount", totalEmployeeCountFuture.join());
        model.addAttribute("lockedCount", lockedCountFuture.join());

        return "employees/pending";
    }

    /**
     * 관리자용 - 사원 등록 신청 승인 처리
     */
    @PostMapping("/employees/{empId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public String approveEmployee(
            @PathVariable Long empId,
            @AuthenticationPrincipal User user,
            @RequestHeader(value = "HX-Request", required = false) boolean htmxRequest,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Long adminId = Long.parseLong(user.getUsername());
            employeeService.approveEmployee(empId, adminId);
            
            String msg = "사원 등록 신청이 승인되었습니다. (사번: " + empId + ")";
            if (htmxRequest) {
                model.addAttribute("successMessage", msg);
                // CUD 작업 후 업데이트된 통계 수치를 모델에 반영하여 HTMX OOB 업데이트 정합성 확보
                refreshStats(model);
                return pendingList(model);
            }
            redirectAttributes.addFlashAttribute("successMessage", msg);
        } catch (Exception e) {
            log.error("사원 승인 실패 (empId={}): {}", empId, e.getMessage());
            if (htmxRequest) {
                model.addAttribute("errorMessage", "승인 처리 중 오류가 발생했습니다: " + e.getMessage());
                return pendingList(model);
            }
            redirectAttributes.addFlashAttribute("errorMessage",
                    "승인 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/employees/pending";
    }

    /**
     * 관리자용 - 사원 등록 신청 거절 처리
     */
    @PostMapping("/employees/{empId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public String rejectEmployee(
            @PathVariable Long empId,
            @AuthenticationPrincipal User user,
            @RequestHeader(value = "HX-Request", required = false) boolean htmxRequest,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Long adminId = Long.parseLong(user.getUsername());
            employeeService.rejectEmployee(empId, adminId);
            
            String msg = "사원 등록 신청이 거절되었습니다. (사번: " + empId + ")";
            if (htmxRequest) {
                model.addAttribute("successMessage", msg);
                refreshStats(model);
                return pendingList(model);
            }
            redirectAttributes.addFlashAttribute("successMessage", msg);
        } catch (Exception e) {
            log.error("사원 거절 실패 (empId={}): {}", empId, e.getMessage());
            if (htmxRequest) {
                model.addAttribute("errorMessage", "거절 처리 중 오류가 발생했습니다: " + e.getMessage());
                return pendingList(model);
            }
            redirectAttributes.addFlashAttribute("errorMessage",
                    "거절 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/employees/pending";
    }

    /**
     * HTMX 요청 시 최신 통계 데이터를 모델에 주입하여
     * 화면 상단 통계 카드 및 사이드바 배지의 실시간 업데이트를 보장합니다.
     */
    private void refreshStats(Model model) {
        CompletableFuture<Long> pendingCountFuture = CompletableFuture.supplyAsync(
                employeeService::countPendingEmployees, virtualThreadExecutor);
        CompletableFuture<Long> activeCountFuture = CompletableFuture.supplyAsync(
                employeeService::countActiveEmployees, virtualThreadExecutor);
        CompletableFuture<Long> thisYearHiredFuture = CompletableFuture.supplyAsync(
                employeeService::countThisYearHired, virtualThreadExecutor);
        CompletableFuture<Long> totalCountFuture = CompletableFuture.supplyAsync(
                () -> employeeService.searchEmployeesPage(null, null, null, 1, 1).totalCount(),
                virtualThreadExecutor);
        CompletableFuture<Long> lockedCountFuture = CompletableFuture.supplyAsync(
                employeeService::countLockedEmployees, virtualThreadExecutor);

        CompletableFuture.allOf(pendingCountFuture, activeCountFuture, thisYearHiredFuture, totalCountFuture, lockedCountFuture).join();

        model.addAttribute("pendingCount", pendingCountFuture.join());
        model.addAttribute("activeCount", activeCountFuture.join());
        model.addAttribute("thisYearHired", thisYearHiredFuture.join());
        model.addAttribute("totalEmployeeCount", totalCountFuture.join());
        model.addAttribute("lockedCount", lockedCountFuture.join());
    }
}
