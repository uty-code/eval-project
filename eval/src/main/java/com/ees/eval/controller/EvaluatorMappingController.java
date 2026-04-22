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
    private final com.ees.eval.mapper.DepartmentMapper departmentMapper;

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
    public String listMappings(@RequestParam(required = false) Long periodId, 
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String searchName,
            Authentication authentication,
            Principal principal, Model model) {
        List<EvaluationPeriodDTO> periods = periodService.getAllPeriods();
        Long selectedId = (periodId != null) ? periodId : (!periods.isEmpty() ? periods.get(0).periodId() : null);

        boolean isAdmin = isAdmin(authentication);
        Long effectiveDeptId = isAdmin ? deptId : getUserDeptId(principal);

        if (selectedId != null) {
            List<EvaluatorMappingDTO> mappings = mappingService.getMappingsByPeriodIdAndDeptId(selectedId, effectiveDeptId, searchName);
            
            // 피평가자 기준으로 매핑 정보 그룹핑 (UI 옵션 1)
            java.util.Map<Long, java.util.Map<String, Object>> groupedMap = new java.util.LinkedHashMap<>();
            for (EvaluatorMappingDTO m : mappings) {
                java.util.Map<String, Object> group = groupedMap.computeIfAbsent(m.evaluateeId(), k -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("evaluateeId", m.evaluateeId());
                    map.put("evaluateeName", m.evaluateeName());
                    map.put("manager", null);
                    map.put("executive", null);
                    map.put("subordinates", new java.util.ArrayList<EvaluatorMappingDTO>());
                    return map;
                });
                
                if ("MANAGER".equals(m.relationTypeCode())) {
                    group.put("manager", m);
                } else if ("EXECUTIVE".equals(m.relationTypeCode())) {
                    group.put("executive", m);
                } else if ("SUBORDINATE".equals(m.relationTypeCode())) {
                    @SuppressWarnings("unchecked")
                    List<EvaluatorMappingDTO> subs = (List<EvaluatorMappingDTO>) group.get("subordinates");
                    subs.add(m);
                }
            }
            
            model.addAttribute("groupedMappings", groupedMap.values());
            model.addAttribute("mappings", mappings);
            model.addAttribute("selectedPeriod", periodService.getPeriodById(selectedId));
            model.addAttribute("isFiltered", effectiveDeptId != null || (searchName != null && !searchName.isEmpty()));
        }

        if (isAdmin) {
            model.addAttribute("departments", departmentMapper.findAll());
        }

        model.addAttribute("periods", periods);
        model.addAttribute("activeMenu", "evaluators");
        model.addAttribute("selectedDeptId", effectiveDeptId);
        model.addAttribute("searchName", searchName);
        return "eval/evaluators/list";
    }

    /**
     * 조직 정보를 기반으로 평가자를 자동 생성합니다.
     */
    @PostMapping("/auto-generate")
    public String autoGenerate(@RequestParam Long periodId, @RequestParam(required = false) Long targetDeptId,
            Authentication authentication, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            boolean isAdmin = isAdmin(authentication);
            Long effectiveDeptId = isAdmin ? targetDeptId : getUserDeptId(principal);
            
            int count = mappingService.autoGenerateMappings(periodId, effectiveDeptId, null);
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

    /**
     * 평가자를 단건 수동으로 추가합니다.
     */
    @PostMapping("/create")
    public String createMapping(@ModelAttribute EvaluatorMappingDTO mappingDto, RedirectAttributes redirectAttributes) {
        try {
            mappingService.createMapping(mappingDto);
            redirectAttributes.addFlashAttribute("successMessage", "평가 관계가 수동으로 추가되었습니다.");
        } catch (Exception e) {
            log.error("평가 관계 추가 실패", e);
            redirectAttributes.addFlashAttribute("errorMessage", "추가 중 오류 발생: " + e.getMessage());
        }
        return "redirect:/eval/evaluators?periodId=" + mappingDto.periodId();
    }

    /**
     * 기존 매핑의 평가자를 다른 사원으로 수동 변경합니다.
     */
    @PostMapping("/{mappingId}/update")
    public String updateMapping(@PathVariable Long mappingId, @RequestParam Long periodId, @RequestParam Long newEvaluatorId, RedirectAttributes redirectAttributes) {
        try {
            mappingService.updateMapping(mappingId, newEvaluatorId);
            redirectAttributes.addFlashAttribute("successMessage", "평가자가 성공적으로 변경되었습니다.");
        } catch (Exception e) {
            log.error("평가자 변경 실패", e);
            redirectAttributes.addFlashAttribute("errorMessage", "변경 중 오류 발생: " + e.getMessage());
        }
        return "redirect:/eval/evaluators?periodId=" + periodId;
    }

    /**
     * 선택한 부서의 평가자 매핑을 일괄 초기화(삭제)합니다.
     */
    @PostMapping("/initialize")
    public String initializeMappings(@RequestParam Long periodId, @RequestParam(required = false) Long targetDeptId, 
            Authentication authentication, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            boolean isAdmin = isAdmin(authentication);
            Long effectiveDeptId = isAdmin ? targetDeptId : getUserDeptId(principal);
            mappingService.initializeMappingsByDept(periodId, effectiveDeptId);
            redirectAttributes.addFlashAttribute("successMessage", "대상 매핑 데이터가 일괄 초기화(삭제)되었습니다.");
        } catch (Exception e) {
            log.error("매핑 일괄 초기화 실패", e);
            redirectAttributes.addFlashAttribute("errorMessage", "초기화 중 오류 발생: " + e.getMessage());
        }
        return "redirect:/eval/evaluators?periodId=" + periodId;
    }
}
