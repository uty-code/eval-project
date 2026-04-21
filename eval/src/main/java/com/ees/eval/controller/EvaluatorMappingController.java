package com.ees.eval.controller;

import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.service.EvaluatorMappingService;
import com.ees.eval.service.EvaluationPeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.domain.Employee;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 평가자 매핑(EvaluatorMapping) 관리 컨트롤러입니다.
 * 차수별 평가자 자동 생성 및 수동 관리를 담당합니다.
 */
@Slf4j
@Controller
@RequestMapping("/eval/evaluators")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EXECUTIVE')")
public class EvaluatorMappingController {

    private final EvaluatorMappingService mappingService;
    private final EvaluationPeriodService periodService;
    private final EmployeeMapper employeeMapper;

    /**
     * 현재 사용자가 관리자(ADMIN)인지 확인합니다.
     */
    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));
    }

    /**
     * 현재 사용자의 부서 ID를 조회합니다. (부서장/임원용)
     */
    private Long getUserDeptId(Principal principal) {
        Long empId = Long.parseLong(principal.getName());
        return employeeMapper.findById(empId)
                .map(Employee::getDeptId)
                .orElse(null);
    }

    @GetMapping
    public String listMappings(@RequestParam(required = false) Long periodId, Authentication authentication,
            Principal principal, Model model) {
        List<EvaluationPeriodDTO> periods = periodService.getAllPeriods();
        Long selectedId = (periodId != null) ? periodId : (!periods.isEmpty() ? periods.get(0).periodId() : null);

        if (selectedId != null) {
            boolean isAdmin = isAdmin(authentication);
            Long deptId = isAdmin ? null : getUserDeptId(principal);
            Long excludeEmpId = isAdmin ? null : Long.parseLong(principal.getName());
            
            // 정책 위반 데이터(자기 평가 등) 실시간 정리
            mappingService.cleanUpInvalidMappings(selectedId);
            
            List<EvaluatorMappingDTO> mappings = mappingService.getMappingsByPeriodIdAndDeptId(selectedId, deptId, excludeEmpId);
            model.addAttribute("mappings", mappings);
            model.addAttribute("selectedPeriod", periodService.getPeriodById(selectedId));
            model.addAttribute("isFiltered", deptId != null);
        }

        model.addAttribute("periods", periods);
        model.addAttribute("activeMenu", "evaluators");
        return "eval/evaluators/list";
    }

    /**
     * 조직 정보를 기반으로 평가자를 자동 생성합니다.
     */
    @PostMapping("/auto-generate")
    public String autoGenerate(@RequestParam Long periodId, Authentication authentication, Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            boolean isAdmin = isAdmin(authentication);
            Long deptId = isAdmin ? null : getUserDeptId(principal);
            Long excludeEmpId = isAdmin ? null : Long.parseLong(principal.getName());
            
            int count = mappingService.autoGenerateMappings(periodId, deptId, excludeEmpId);
            redirectAttributes.addFlashAttribute("successMessage", count + "건의 평가 관계가 자동 생성되었습니다.");
        } catch (Exception e) {
            log.error("평가자 자동 생성 실패", e);
            redirectAttributes.addFlashAttribute("errorMessage", "자동 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/eval/evaluators?periodId=" + periodId;
    }

    /**
     * 매핑을 삭제합니다.
     */
    @PostMapping("/{mappingId}/delete")
    public String deleteMapping(@PathVariable Long mappingId, @RequestParam Long periodId,
            RedirectAttributes redirectAttributes) {
        try {
            mappingService.deleteMapping(mappingId);
            redirectAttributes.addFlashAttribute("successMessage", "평가 관계가 삭제되었습니다.");
        } catch (Exception e) {
            log.error("평가 관계 삭제 실패", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/eval/evaluators?periodId=" + periodId;
    }
}
