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
 * 어드민은 읽기 전용으로 전체 자가평가 현황을 열람할 수 있습니다.
 */
@Slf4j
@Controller
@RequestMapping("/eval/my-evaluation")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MyEvaluationController {

    private final MyEvaluationFacadeService myEvaluationFacadeService;
    private final com.ees.eval.support.ui.EvalFilterConfigFactory filterConfigFactory;

    /**
     * 자가평가 메인 페이지 (대시보드 리스트)
     * 어드민의 경우 전체 자가평가 현황을 읽기 전용으로 조회합니다.
     */
    @GetMapping({"", "/list"})
    public String list(Model model,
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) String filterStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long filterDeptId,
            @RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal UserDetails userDetails) {

        model.addAttribute("activeMenu", "my-evaluation");

        // 어드민 여부 판별
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        // 부서 필터는 오직 어드민(관리자) 뷰에서만 활성화됩니다.
        Long resolvedDeptId = isAdmin ? filterDeptId : null;

        if (isAdmin) {
            // 어드민: 전체 자가평가 현황 조회 (evaluator_id 필터 없음)
            Map<String, Object> dashboardData = myEvaluationFacadeService.getAdminDashboardData(periodId, filterStatus, keyword, resolvedDeptId, page, 10);
            model.addAllAttributes(dashboardData);
            model.addAttribute("isAdminView", true);
        } else {
            // 일반 유저: 기존 로직 유지
            Long empId = Long.parseLong(userDetails.getUsername());
            Map<String, Object> dashboardData = myEvaluationFacadeService.getDashboardData(empId, periodId, filterStatus, keyword, page, 10);
            model.addAllAttributes(dashboardData);
            model.addAttribute("isAdminView", false);
        }

        // 공통 필터 바용 구성 설정 주입
        model.addAttribute("filterConfig", filterConfigFactory.createMyEvalConfig(periodId, filterStatus, keyword, resolvedDeptId, isAdmin));

        return "eval/my-evaluation/list";
    }

    /**
     * 자가평가 통합 마법사 페이지 (어드민은 읽기 전용 열람)
     */
    @GetMapping("/wizard")
    public String getWizard(@RequestParam Long mappingId,
            Model model,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        model.addAttribute("isAdminView", isAdmin);

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
     * 자가평가 제출 (어드민 접근 차단)
     */
    @PostMapping("/submit")
    @PreAuthorize("!hasRole('ADMIN')")
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
