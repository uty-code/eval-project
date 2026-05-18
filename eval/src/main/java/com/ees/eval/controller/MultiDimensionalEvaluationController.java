package com.ees.eval.controller;


import com.ees.eval.domain.Employee;
import com.ees.eval.domain.Evaluation;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationPeriodService;
import com.ees.eval.service.EvaluationService;
import com.ees.eval.service.EvaluationTypeWeightService;
import com.ees.eval.service.EvaluatorMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 다면평가(Multi-dimensional Evaluation) 컨트롤러
 * 부서원(Subordinate)이 부서장(Leader)을 평가하는 기능을 담당합니다.
 */
@Slf4j
@Controller
@RequestMapping("/eval/multi-dimensional")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MultiDimensionalEvaluationController {

    private final EvaluationPeriodService periodService;
    private final EvaluatorMappingService mappingService;
    private final EvaluationElementService elementService;
    private final EvaluationTypeWeightService typeWeightService;
    private final EvaluationService evaluationService;
    private final EvaluationMapper evaluationMapper;
    private final EmployeeMapper employeeMapper;
    private final EvaluatorMappingMapper evaluatorMappingMapper;
    private final com.ees.eval.service.DepartmentService departmentService;
    private final com.ees.eval.support.ui.EvalFilterConfigFactory filterConfigFactory;



    /**
     * 다면평가 대상 목록 페이지
     */
    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) Long periodId,
                       @RequestParam(required = false) Long filterDeptId,
                       @RequestParam(required = false) Long deptId, // fallback
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String search, // fallback
                       @RequestParam(required = false) String filterStatus,
                       @RequestParam(required = false) String status, // fallback
                       @RequestParam(defaultValue = "1") int page,
                       @AuthenticationPrincipal UserDetails userDetails,
                       jakarta.servlet.http.HttpServletRequest request) {

        Long resolvedDeptId = filterDeptId != null ? filterDeptId : deptId;
        String resolvedKeyword = keyword != null ? keyword : search;
        String resolvedStatus = filterStatus != null ? filterStatus : status;

        Long empId = Long.parseLong(userDetails.getUsername());
        model.addAttribute("activeMenu", "multi-dimensional-eval");
        // 부서 목록 조기 방어 바인딩 (NPE 원천 차단)
        model.addAttribute("departments", java.util.Collections.emptyList());

        // 어드민 여부 판별
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        model.addAttribute("isAdminView", isAdmin);

        // 1. 전체 차수 목록 로드 및 정렬 정책 적용 (IN_PROGRESS 우선, 그 외 최신순)
        List<EvaluationPeriodDTO> allPeriods = periodService.getAllPeriods();
        List<EvaluationPeriodDTO> sortedPeriods = new java.util.ArrayList<>(allPeriods);
        sortedPeriods.sort((p1, p2) -> {
            boolean p1Active = "IN_PROGRESS".equals(p1.statusCode());
            boolean p2Active = "IN_PROGRESS".equals(p2.statusCode());
            if (p1Active && !p2Active) return -1;
            if (!p1Active && p2Active) return 1;
            int yearCompare = p2.periodYear().compareTo(p1.periodYear());
            if (yearCompare != 0) return yearCompare;
            return p2.periodId().compareTo(p1.periodId());
        });
        model.addAttribute("periods", sortedPeriods);

        // 2. 파라미터 존재 여부 확인 (최초 진입 vs 명시적 전체 선택 구분)
        boolean hasPeriodParam = request.getParameterMap().containsKey("periodId");
        
        // 3. 최초 진입 시 리다이렉트 처리 (정렬된 순서에 따라 처리)
        if (!hasPeriodParam) {
            EvaluationPeriodDTO defaultPeriod = periodService.resolveSelectedPeriod(null, sortedPeriods);
            if (defaultPeriod != null) {
                return "redirect:/eval/multi-dimensional?periodId=" + defaultPeriod.periodId();
            }
        }

        // 4. 선택된 차수 정보 결정 (null이면 전체 통합 조회)
        EvaluationPeriodDTO selectedPeriod = null;
        if (periodId != null && periodId > 0) {
            selectedPeriod = allPeriods.stream()
                    .filter(p -> p.periodId().equals(periodId))
                    .findFirst()
                    .orElse(null);
        } else if (periodId != null && periodId == 0L) {
            selectedPeriod = EvaluationPeriodDTO.builder()
                .periodId(0L)
                .periodYear(java.time.LocalDate.now().getYear())
                .periodName("전체 차수 통합")
                .statusCode("COMPLETED")
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now())
                .build();
        }
        model.addAttribute("selectedPeriod", selectedPeriod);

        // 5. 데이터 조회
        int pageSize = 10;
        Long targetPeriodId = (selectedPeriod != null && selectedPeriod.periodId() > 0) ? selectedPeriod.periodId() : null;
        boolean isPeriodActive = (selectedPeriod != null && selectedPeriod.periodId() > 0) && periodService.isPeriodActive(selectedPeriod.periodId());
        
        model.addAttribute("isPeriodActive", isPeriodActive);

        com.ees.eval.dto.MultiDimensionalEvalPageDTO pageData;
        if (isAdmin) {
            // 어드민: 전체 다면평가 현황 조회 (evaluator_id 필터 없음)
            pageData = mappingService.getAdminMultiDimensionalTasks(
                    targetPeriodId, resolvedDeptId, resolvedStatus, resolvedKeyword, page, pageSize, isPeriodActive);
        } else {
            // 일반 유저: 기존 로직 유지
            pageData = mappingService.getMultiDimensionalTasks(
                    targetPeriodId, empId, resolvedDeptId, resolvedStatus, resolvedKeyword, page, pageSize, isPeriodActive);
        }
        
        model.addAttribute("pageData", pageData);
        
        // 필터 상태 유지
        model.addAttribute("filterDeptId", resolvedDeptId);
        model.addAttribute("keyword", resolvedKeyword);
        model.addAttribute("filterStatus", resolvedStatus);

        // 6. 필터 드롭다운용 부서 목록 추출 (모두 전체 조직 트리를 조회할 수 있도록 변경)
        java.util.List<com.ees.eval.dto.DepartmentDTO> filterDepts = departmentService.getAllDepartments();
        model.addAttribute("departments", filterDepts);

        if (selectedPeriod != null && "PLANNED".equals(selectedPeriod.statusCode())) {
            // 상단 메시지 제거
        } else if (selectedPeriod == null && pageData.totalCount() == 0) {
            // 상단 메시지 제거
        }

        // 공통 필터 바 구성 DTO 전달
        model.addAttribute("filterConfig", filterConfigFactory.createMultiDimensionalConfig(periodId, resolvedStatus, resolvedKeyword, resolvedDeptId));

        return "eval/multi-dimensional/list";
    }

    /**
     * 다면평가 입력 폼 (어드민은 읽기 전용 열람)
     */
    @GetMapping("/form")
    public String getForm(@RequestParam Long mappingId,
                          Model model,
                          @AuthenticationPrincipal UserDetails userDetails,
                          RedirectAttributes redirectAttributes) {

        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        model.addAttribute("isAdminView", isAdmin);

        EvaluatorMappingDTO mapping = mappingService.getMappingById(mappingId);

        // SUBORDINATE 관계인지 재검증
        if (!"SUBORDINATE".equals(mapping.relationTypeCode())) {
            redirectAttributes.addFlashAttribute("errorMessage", "다면평가 대상이 아닙니다.");
            return "redirect:/eval/multi-dimensional";
        }

        boolean isPeriodActive = periodService.isPeriodActive(mapping.periodId());
        model.addAttribute("isPeriodActive", isPeriodActive);

        // 가중치 검증 (피평가자가 부서장이므로 LEADER 기준)
        Employee evaluatee = employeeMapper.findById(mapping.evaluateeId()).orElse(null);
        Long evaluateeDeptId = (evaluatee != null) ? evaluatee.getDeptId() : null;
        if (!typeWeightService.isWeightSumValid(mapping.periodId(), evaluateeDeptId, "LEADER")) {
            redirectAttributes.addFlashAttribute("errorMessage", "유형별 가중치 설정(LEADER)이 올바르지 않습니다.");
            return "redirect:/eval/multi-dimensional?periodId=" + mapping.periodId();
        }

        model.addAttribute("mapping", mapping);

        // 다면평가 요소(MULTI_DIMENSIONAL)만 필터링
        List<EvaluationElementDTO> allElements = elementService.getElementsWithFallback(mapping.periodId(), evaluateeDeptId);
        List<EvaluationElementDTO> elements = allElements.stream()
                .filter(e -> "MULTI_DIMENSIONAL".equals(e.elementTypeCode()))
                .collect(Collectors.toList());

        model.addAttribute("elements", elements);
        model.addAttribute("mappingId", mappingId);

        // 기존 저장 데이터
        java.util.Map<Long, Evaluation> savedMap = evaluationMapper.findByMappingId(mappingId)
                .stream()
                .collect(Collectors.toMap(Evaluation::getElementId, e -> e, (a, b) -> a));
        model.addAttribute("savedMap", savedMap);

        boolean submitted = savedMap.values().stream()
                .anyMatch(e -> "SUBMITTED".equals(e.getConfirmStatusCode()));
        model.addAttribute("submitted", submitted);

        // 역순 진행 방지 (상위 평가자가 제출했는지 확인) 및 기간 종료 여부 통합 체크
        java.util.Map<String, Object> lockInfo = mappingService.checkEvaluationLock(mappingId);
        boolean isLocked = (boolean) lockInfo.get("isLocked") || !isPeriodActive;
        model.addAttribute("isLocked", isLocked);
        model.addAttribute("lockedBy", lockInfo.get("lockedBy"));

        // 피평가자(부서장)의 자가평가 데이터 조회 (다면평가 참고용)
        List<EvaluatorMappingDTO> evaluateeTasks = mappingService.getMyEvaluationTasks(mapping.periodId(), mapping.evaluateeId());
        EvaluatorMappingDTO evaluateeSelfTask = evaluateeTasks.stream()
                .filter(m -> "SELF".equals(m.relationTypeCode()))
                .findFirst()
                .orElse(null);

        if (evaluateeSelfTask != null) {
            List<Evaluation> evaluateeEvals = evaluationMapper.findByMappingId(evaluateeSelfTask.mappingId());
            java.util.Map<Long, Evaluation> evaluateeSelfEvalMap = evaluateeEvals.stream()
                    .collect(Collectors.toMap(Evaluation::getElementId, e -> e, (a, b) -> a));
            
            boolean isEvaluateeSelfSubmitted = evaluateeEvals.stream()
                    .anyMatch(e -> "SUBMITTED".equals(e.getConfirmStatusCode()));
            
            model.addAttribute("evaluateeSelfEvalMap", evaluateeSelfEvalMap);
            model.addAttribute("isEvaluateeSelfSubmitted", isEvaluateeSelfSubmitted);
        } else {
            model.addAttribute("isEvaluateeSelfSubmitted", false);
        }

        return "eval/multi-dimensional/form";
    }

    /**
     * 다면평가 제출
     */

    @PostMapping("/submit")
    @PreAuthorize("!hasRole('ADMIN')")
    public String submitForm(@RequestParam Long mappingId,
                             @RequestParam java.util.Map<String, String> params,
                             @AuthenticationPrincipal UserDetails userDetails,
                             RedirectAttributes redirectAttributes) {

        Long empId = Long.parseLong(userDetails.getUsername());
        
        // 평가 기간 유효성 검증 (제출 시점 재확인)
        EvaluatorMappingDTO mapping = mappingService.getMappingById(mappingId);
        if (!periodService.isPeriodActive(mapping.periodId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "평가 기간이 종료되어 제출할 수 없습니다.");
            return "redirect:/eval/multi-dimensional?periodId=" + mapping.periodId();
        }

        // 역순 진행 방지 검증
        java.util.Map<String, Object> lockInfo = mappingService.checkEvaluationLock(mappingId);
        if ((Boolean) lockInfo.get("isLocked")) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                lockInfo.get("lockedBy") + "가 평가를 완료하여 더 이상 수정할 수 없습니다.");
            return "redirect:/eval/multi-dimensional/form?mappingId=" + mappingId;
        }

        // 평가 데이터 Upsert 처리
        try {
            evaluationService.upsertEvaluations(mappingId, params, empId);
        } catch (NumberFormatException e) {
            log.warn("[다면평가 제출] 점수 파싱 실패: mappingId={}", mappingId);
            redirectAttributes.addFlashAttribute("errorMessage", "잘못된 점수 형식입니다.");
            return "redirect:/eval/multi-dimensional/form?mappingId=" + mappingId;
        }

        EvaluatorMappingDTO submitMapping = mappingService.getMappingById(mappingId);
        redirectAttributes.addFlashAttribute("successMessage", "다면평가가 제출되었습니다.");
        return "redirect:/eval/multi-dimensional?periodId=" + submitMapping.periodId();
    }
}
