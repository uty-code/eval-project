package com.ees.eval.controller;

import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
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

    /**
     * 특정 평가 차수의 평가 요소 목록 및 설정 페이지를 반환합니다.
     *
     * @param periodId 대상 차수 ID (Optional, 미전달 시 최근 차수 기준)
     * @param model    Thymeleaf 모델
     * @return eval/elements/list.html
     */
    @GetMapping
    public String listElements(@RequestParam(required = false) Long periodId, Model model) {
        List<EvaluationPeriodDTO> periods = periodService.getAllPeriods();
        
        // periodId가 없으면 가장 최근(첫 번째) 차수 선택
        Long selectedId = (periodId != null) ? periodId : 
                         (!periods.isEmpty() ? periods.get(0).periodId() : null);

        if (selectedId != null) {
            List<EvaluationElementDTO> elements = elementService.getElementsByPeriodId(selectedId);
            EvaluationPeriodDTO selectedPeriod = periodService.getPeriodById(selectedId);
            
            // 가중치 합계 계산
            BigDecimal totalWeight = elements.stream()
                    .map(EvaluationElementDTO::weight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            model.addAttribute("elements", elements);
            model.addAttribute("selectedPeriod", selectedPeriod);
            model.addAttribute("totalWeight", totalWeight);
            model.addAttribute("isValid", totalWeight.compareTo(new BigDecimal("100.00")) == 0);
        }

        model.addAttribute("periods", periods);
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
        return "redirect:/eval/elements?periodId=" + dto.periodId();
    }

    /**
     * 평가 요소를 삭제합니다.
     */
    @PostMapping("/{elementId}/delete")
    public String deleteElement(@PathVariable Long elementId, @RequestParam Long periodId, RedirectAttributes redirectAttributes) {
        try {
            elementService.deleteElement(elementId);
            redirectAttributes.addFlashAttribute("successMessage", "평가 항목이 삭제되었습니다.");
        } catch (Exception e) {
            log.error("평가 항목 삭제 실패", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/eval/elements?periodId=" + periodId;
    }
}
