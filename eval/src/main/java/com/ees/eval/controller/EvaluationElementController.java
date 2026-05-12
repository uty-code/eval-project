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
    private final com.ees.eval.service.EvaluationTypeWeightService typeWeightService;

    /**
     * 특정 평가 차수의 평가 요소 목록 및 설정 페이지를 반환합니다.
     */
    @GetMapping
    public String listElements(@RequestParam(required = false) Long periodId,
            @RequestParam(required = false) Long deptId,
            @RequestHeader(value = "HX-Request", required = false) boolean isHtmx,
            Model model) {
        prepareListModel(periodId, deptId, model);

        if (isHtmx) {
            return "eval/elements/list :: content";
        }
        return "eval/elements/list";
    }

    private void prepareListModel(Long periodId, Long deptId, Model model) {
        List<EvaluationPeriodDTO> periods = periodService.getAllPeriods();
        List<DepartmentDTO> departments = departmentService.getSimpleAllDepartments();

        // 진행 중(IN_PROGRESS)인 차수를 기본으로 선택하고, 없으면 첫 번째 차수로 폴백
        Long selectedId = (periodId != null) ? periodId
                : periods.stream()
                        .filter(p -> "IN_PROGRESS".equals(p.statusCode()))
                        .map(EvaluationPeriodDTO::periodId)
                        .findFirst()
                        .orElse(!periods.isEmpty() ? periods.get(0).periodId() : null);

        if (selectedId != null) {
            List<EvaluationElementDTO> elements = elementService.getElementsByPeriodId(selectedId, deptId);
            EvaluationPeriodDTO selectedPeriod = periodService.getPeriodById(selectedId);

            List<com.ees.eval.dto.EvaluationTypeWeightDTO> memberWeights = typeWeightService.getTypeWeights(selectedId,
                    deptId, "STAFF");
            List<com.ees.eval.dto.EvaluationTypeWeightDTO> leaderWeights = typeWeightService.getTypeWeights(selectedId,
                    deptId, "LEADER");

            model.addAttribute("elements", elements);
            model.addAttribute("selectedPeriod", selectedPeriod);
            model.addAttribute("selectedDeptId", deptId);
            model.addAttribute("memberWeights", memberWeights);
            model.addAttribute("leaderWeights", leaderWeights);

            // 편집 가능 여부: PLANNED 상태일 때만 수정 허용
            boolean editable = "PLANNED".equals(selectedPeriod.statusCode());
            model.addAttribute("editable", editable);

            BigDecimal memberTotal = elements.stream()
                    .filter(e -> List.of("PERFORMANCE", "COMPETENCY").contains(e.elementTypeCode()))
                    .map(EvaluationElementDTO::weight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal leaderTotal = elements.stream()
                    .filter(e -> "MULTI_DIMENSIONAL".equals(e.elementTypeCode()))
                    .map(EvaluationElementDTO::weight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            model.addAttribute("memberTotalWeight", memberTotal);
            model.addAttribute("leaderTotalWeight", leaderTotal);
            model.addAttribute("isMemberValid", memberTotal.compareTo(new BigDecimal("100.00")) == 0);
            model.addAttribute("isLeaderValid", leaderTotal.compareTo(new BigDecimal("100.00")) == 0);
        } else {
            model.addAttribute("infoMessage", "등록된 평가 차수가 없습니다.");
        }

        model.addAttribute("periods", periods);
        model.addAttribute("departments", departments);
        model.addAttribute("activeMenu", "elements");
    }

    /**
     * 해당 차수가 수정 가능한 상태(PLANNED)인지 검증합니다.
     * PLANNED 이외의 상태에서는 평가요소 변경이 불가합니다.
     */
    private void validateEditable(Long periodId) {
        EvaluationPeriodDTO period = periodService.getPeriodById(periodId);
        if (!"PLANNED".equals(period.statusCode())) {
            throw new IllegalStateException(
                    "평가가 이미 시작된 차수('" + period.periodName() + "')의 평가요소는 변경할 수 없습니다.");
        }
    }

    @PostMapping("/type-weights")
    public String saveTypeWeights(@RequestParam Long periodId,
            @RequestParam(required = false) Long deptId,
            @RequestParam String targetRoleCode,
            @RequestParam List<String> types,
            @RequestParam List<BigDecimal> weights,
            @RequestHeader(value = "HX-Request", required = false) boolean isHtmx,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            validateEditable(periodId);
            java.util.List<com.ees.eval.dto.EvaluationTypeWeightDTO> dtoList = new java.util.ArrayList<>();
            for (int i = 0; i < types.size(); i++) {
                dtoList.add(com.ees.eval.dto.EvaluationTypeWeightDTO.builder()
                        .elementTypeCode(types.get(i))
                        .weight(weights.get(i))
                        .build());
            }
            typeWeightService.saveTypeWeights(periodId, deptId, targetRoleCode, dtoList);
            model.addAttribute("successMessage", targetRoleCode + "용 유형별 비중이 저장되었습니다.");
        } catch (Exception e) {
            log.error("유형별 가중치 저장 실패", e);
            model.addAttribute("errorMessage", e.getMessage());
        }

        if (isHtmx) {
            prepareListModel(periodId, deptId, model);
            return "eval/elements/list :: content";
        }

        redirectAttributes.addFlashAttribute("successMessage", targetRoleCode + "용 유형별 비중이 저장되었습니다.");
        String redirectUrl = "redirect:/eval/elements?periodId=" + periodId;
        if (deptId != null)
            redirectUrl += "&deptId=" + deptId;
        return redirectUrl;
    }

    @PostMapping
    public String createElement(@ModelAttribute EvaluationElementDTO dto,
            @RequestHeader(value = "HX-Request", required = false) boolean isHtmx,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            validateEditable(dto.periodId());
            elementService.createElement(dto);
            model.addAttribute("successMessage", "평가 항목이 추가되었습니다.");
        } catch (Exception e) {
            log.error("평가 항목 생성 실패", e);
            model.addAttribute("errorMessage", e.getMessage());
        }

        if (isHtmx) {
            prepareListModel(dto.periodId(), dto.deptId(), model);
            return "eval/elements/list :: content";
        }

        redirectAttributes.addFlashAttribute("successMessage", "평가 항목이 추가되었습니다.");
        String redirectUrl = "redirect:/eval/elements?periodId=" + dto.periodId();
        if (dto.deptId() != null) {
            redirectUrl += "&deptId=" + dto.deptId();
        }
        return redirectUrl;
    }

    @PostMapping("/update")
    public String updateElement(@ModelAttribute EvaluationElementDTO dto,
            @RequestHeader(value = "HX-Request", required = false) boolean isHtmx,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            validateEditable(dto.periodId());
            elementService.updateElement(dto);
            model.addAttribute("successMessage", "평가 항목이 수정되었습니다.");
        } catch (Exception e) {
            log.error("평가 항목 수정 실패", e);
            model.addAttribute("errorMessage", e.getMessage());
        }

        if (isHtmx) {
            prepareListModel(dto.periodId(), dto.deptId(), model);
            return "eval/elements/list :: content";
        }

        redirectAttributes.addFlashAttribute("successMessage", "평가 항목이 수정되었습니다.");
        String redirectUrl = "redirect:/eval/elements?periodId=" + dto.periodId();
        if (dto.deptId() != null) {
            redirectUrl += "&deptId=" + dto.deptId();
        }
        return redirectUrl;
    }

    @PostMapping("/copy-common")
    public String copyCommonElements(@RequestParam Long periodId,
            @RequestParam Long deptId,
            @RequestHeader(value = "HX-Request", required = false) boolean isHtmx,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            validateEditable(periodId);
            elementService.copyCommonElementsToDept(periodId, deptId);
            model.addAttribute("successMessage", "전사 공통 항목을 성공적으로 불러왔습니다.");
        } catch (Exception e) {
            log.error("공통 항목 복사 실패", e);
            model.addAttribute("errorMessage", e.getMessage());
        }

        if (isHtmx) {
            prepareListModel(periodId, deptId, model);
            return "eval/elements/list :: content";
        }

        redirectAttributes.addFlashAttribute("successMessage", "전사 공통 항목을 성공적으로 불러왔습니다.");
        return "redirect:/eval/elements?periodId=" + periodId + "&deptId=" + deptId;
    }

    @PostMapping("/reset")
    public String resetElements(@RequestParam Long periodId,
            @RequestParam(required = false) Long deptId,
            @RequestHeader(value = "HX-Request", required = false) boolean isHtmx,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            validateEditable(periodId);
            elementService.resetElements(periodId, deptId);
            model.addAttribute("successMessage", "평가 항목이 모두 초기화되었습니다.");
        } catch (Exception e) {
            log.error("평가 항목 초기화 실패", e);
            model.addAttribute("errorMessage", e.getMessage());
        }

        if (isHtmx) {
            prepareListModel(periodId, deptId, model);
            return "eval/elements/list :: content";
        }

        redirectAttributes.addFlashAttribute("successMessage", "평가 항목이 모두 초기화되었습니다.");
        String redirectUrl = "redirect:/eval/elements?periodId=" + periodId;
        if (deptId != null) {
            redirectUrl += "&deptId=" + deptId;
        }
        return redirectUrl;
    }

    @PostMapping("/{elementId}/delete")
    public String deleteElement(@PathVariable Long elementId,
            @RequestParam Long periodId,
            @RequestParam(required = false) Long deptId,
            @RequestHeader(value = "HX-Request", required = false) boolean isHtmx,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            validateEditable(periodId);
            elementService.deleteElement(elementId);
            model.addAttribute("successMessage", "평가 항목이 삭제되었습니다.");
        } catch (Exception e) {
            log.error("평가 항목 삭제 실패", e);
            model.addAttribute("errorMessage", e.getMessage());
        }

        if (isHtmx) {
            prepareListModel(periodId, deptId, model);
            return "eval/elements/list :: content";
        }

        redirectAttributes.addFlashAttribute("successMessage", "평가 항목이 삭제되었습니다.");
        String redirectUrl = "redirect:/eval/elements?periodId=" + periodId;
        if (deptId != null) {
            redirectUrl += "&deptId=" + deptId;
        }
        return redirectUrl;
    }
}
