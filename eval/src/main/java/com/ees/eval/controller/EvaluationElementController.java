package com.ees.eval.controller;

import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.service.DepartmentService;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationPeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

/**
 * 평가 요소(EvaluationElement) 관리 컨트롤러입니다.
 * 특정 평가 차수에 귀속된 평가 항목 및 가중치 설정을 담당합니다.
 */
@Slf4j
@Controller
@RequestMapping("/eval/elements")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EXECUTIVE')")
public class EvaluationElementController {

    private final EvaluationElementService elementService;
    private final EvaluationPeriodService periodService;
    private final DepartmentService departmentService;

    /**
     * 특정 평가 차수의 평가 요소 목록 및 설정 페이지를 반환합니다.
     *
     * @param periodId 대상 차수 ID (Optional, 미전달 시 최근 차수 기준)
     * @param model    Thymeleaf 모델
     * @return eval/elements/list.html
     */
    @GetMapping
    public String listElements(@RequestParam(required = false) Long periodId, 
                               @RequestParam(required = false) Long deptId, 
                               Model model) {
        List<EvaluationPeriodDTO> periods = periodService.getAllPeriods();
        List<DepartmentDTO> departments = departmentService.getAllDepartments();
        
        // periodId가 없으면 가장 최근(첫 번째) 차수 선택
        Long selectedId = (periodId != null) ? periodId : 
                         (!periods.isEmpty() ? periods.get(0).periodId() : null);

        if (selectedId != null) {
            List<EvaluationElementDTO> elements = elementService.getElementsByPeriodId(selectedId, deptId);
            EvaluationPeriodDTO selectedPeriod = periodService.getPeriodById(selectedId);
            
            // 가중치 합계 계산
            BigDecimal totalWeight = elements.stream()
                    .map(EvaluationElementDTO::weight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            model.addAttribute("elements", elements);
            model.addAttribute("selectedPeriod", selectedPeriod);
            model.addAttribute("selectedDeptId", deptId);
            model.addAttribute("totalWeight", totalWeight);
            model.addAttribute("isValid", totalWeight.compareTo(new BigDecimal("100.00")) == 0);
        }

        model.addAttribute("periods", periods);
        model.addAttribute("departments", departments);
        model.addAttribute("activeMenu", "elements");
        return "eval/elements/list";
    }

    /**
     * 평가 요소를 신규 생성합니다.
     */
    @PostMapping
    public String createElement(@ModelAttribute EvaluationElementDTO dto, RedirectAttributes redirectAttributes) {
        try {
            elementService.createElement(dto);
            redirectAttributes.addFlashAttribute("successMessage", "평가 항목이 추가되었습니다.");
        } catch (Exception e) {
            log.error("평가 항목 생성 실패", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        String redirectUrl = "redirect:/eval/elements?periodId=" + dto.periodId();
        if (dto.deptId() != null) {
            redirectUrl += "&deptId=" + dto.deptId();
        }
        return redirectUrl;
    }

    /**
     * 평가 요소를 수정합니다.
     */
    @PostMapping("/update")
    public String updateElement(@ModelAttribute EvaluationElementDTO dto, RedirectAttributes redirectAttributes) {
        try {
            elementService.updateElement(dto);
            redirectAttributes.addFlashAttribute("successMessage", "평가 항목이 수정되었습니다.");
        } catch (Exception e) {
            log.error("평가 항목 수정 실패", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        String redirectUrl = "redirect:/eval/elements?periodId=" + dto.periodId();
        if (dto.deptId() != null) {
            redirectUrl += "&deptId=" + dto.deptId();
        }
        return redirectUrl;
    }

    /**
     * 전사 공통 항목을 부서 설정으로 복사합니다.
     */
    @PostMapping("/copy-common")
    public String copyCommonElements(@RequestParam Long periodId,
                                     @RequestParam Long deptId,
                                     RedirectAttributes redirectAttributes) {
        try {
            elementService.copyCommonElementsToDept(periodId, deptId);
            redirectAttributes.addFlashAttribute("successMessage", "전사 공통 항목을 성공적으로 불러왔습니다.");
        } catch (Exception e) {
            log.error("공통 항목 복사 실패", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/eval/elements?periodId=" + periodId + "&deptId=" + deptId;
    }

    /**
     * 특정 차수/부서의 모든 평가 요소를 초기화합니다.
     */
    @PostMapping("/reset")
    public String resetElements(@RequestParam Long periodId, 
                                @RequestParam(required = false) Long deptId,
                                RedirectAttributes redirectAttributes) {
        try {
            elementService.resetElements(periodId, deptId);
            redirectAttributes.addFlashAttribute("successMessage", "평가 항목이 모두 초기화되었습니다.");
        } catch (Exception e) {
            log.error("평가 항목 초기화 실패", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        String redirectUrl = "redirect:/eval/elements?periodId=" + periodId;
        if (deptId != null) {
            redirectUrl += "&deptId=" + deptId;
        }
        return redirectUrl;
    }

    /**
     * 평가 요소를 삭제합니다.
     */
    @PostMapping("/{elementId}/delete")
    public String deleteElement(@PathVariable Long elementId, 
                                @RequestParam Long periodId, 
                                @RequestParam(required = false) Long deptId,
                                RedirectAttributes redirectAttributes) {
        try {
            elementService.deleteElement(elementId);
            redirectAttributes.addFlashAttribute("successMessage", "평가 항목이 삭제되었습니다.");
        } catch (Exception e) {
            log.error("평가 항목 삭제 실패", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        String redirectUrl = "redirect:/eval/elements?periodId=" + periodId;
        if (deptId != null) {
            redirectUrl += "&deptId=" + deptId;
        }
        return redirectUrl;
    }
}
