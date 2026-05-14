package com.ees.eval.controller;


import com.ees.eval.service.MyEvaluationFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * 나의 자가평가 전용 컨트롤러
 * 로그인한 사용자의 자가평가(SELF) 현황 및 작성을 전담합니다.
 */
@Slf4j
@Controller
@RequestMapping("/eval/my-evaluation")
@RequiredArgsConstructor
@PreAuthorize("!hasRole('ADMIN')")
public class MyEvaluationController {

    private final MyEvaluationFacadeService myEvaluationFacadeService;

    /**
     * 나의 자가평가 메인 페이지 (대시보드 리스트)
     */
    @GetMapping({"", "/list"})
    public String list(Model model,
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) String filterStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal UserDetails userDetails) {

        model.addAttribute("activeMenu", "my-evaluation");
        Long empId = Long.parseLong(userDetails.getUsername());

        Map<String, Object> dashboardData = myEvaluationFacadeService.getDashboardData(empId, periodId, filterStatus, keyword, page, 10);
        model.addAllAttributes(dashboardData);

        return "eval/my-evaluation/list";
    }

    /**
     * 자가평가 통합 마법사 페이지
     */
    @GetMapping("/wizard")
    public String getWizard(@RequestParam Long mappingId,
            Model model,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            Long empId = Long.parseLong(userDetails.getUsername());
            Map<String, Object> wizardData = myEvaluationFacadeService.getWizardData(mappingId, empId);
            model.addAllAttributes(wizardData);
            return "eval/my-evaluation/wizard";
        } catch (Exception e) {
            log.error("[MyEvaluation] wizard error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/eval/my-evaluation";
        }
    }

    /**
     * 자가평가 제출
     */
    @PostMapping("/submit")
    public String submitForm(@RequestParam Long mappingId,
            @RequestParam Map<String, String> params,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            Long empId = Long.parseLong(userDetails.getUsername());
            myEvaluationFacadeService.submitEvaluation(mappingId, params, empId);
            redirectAttributes.addFlashAttribute("successMessage", "자가평가가 성공적으로 제출되었습니다.");
            return "redirect:/eval/my-evaluation";
        } catch (Exception e) {
            log.warn("[MyEvaluation] submit error: mappingId={}, msg={}", mappingId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/eval/my-evaluation/wizard?mappingId=" + mappingId;
        }
    }
}
