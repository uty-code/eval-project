package com.ees.eval.controller;


import com.ees.eval.domain.Employee;
import com.ees.eval.domain.Evaluation;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationPeriodService;
import com.ees.eval.service.EvaluationService;
import com.ees.eval.service.EvaluationTypeWeightService;
import com.ees.eval.service.EvaluatorMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import java.util.stream.Collectors;

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

    private final EvaluationPeriodService periodService;
    private final EvaluatorMappingService mappingService;
    private final EvaluationElementService elementService;
    private final EvaluationTypeWeightService typeWeightService;
    private final EvaluationService evaluationService;
    private final EvaluationMapper evaluationMapper;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;



    /**
     * 나의 자가평가 메인 페이지
     */
    @GetMapping({"", "/list"})
    public String list(Model model,
            @RequestParam(required = false) Long periodId,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long empId = Long.parseLong(userDetails.getUsername());

        // 사원 정보 조회
        Employee currentEmp = employeeMapper.findById(empId).orElse(null);
        model.addAttribute("currentEmp", currentEmp);

        // 부서장 여부 판별
        boolean isLeader = departmentMapper.countDepartmentsByLeaderId(empId) > 0;
        log.info("[MyEvaluation] list - empId: {}, isLeader: {}", empId, isLeader);
        model.addAttribute("isLeader", isLeader);

        // 차수 목록 (진행 중인 차수만 표시)
        List<EvaluationPeriodDTO> periods = periodService.getInProgressPeriods();
        model.addAttribute("periods", periods);

        EvaluationPeriodDTO selectedPeriod = periodService.resolveSelectedPeriod(periodId, periods);

        if (selectedPeriod != null) {
            model.addAttribute("selectedPeriod", selectedPeriod);

            // 나의 평가 태스크 조회
            List<EvaluatorMappingDTO> myTasks = mappingService.getMyEvaluationTasks(selectedPeriod.periodId(), empId);

            EvaluatorMappingDTO selfTask = myTasks.stream()
                    .filter(m -> "SELF".equals(m.relationTypeCode()))
                    .findFirst()
                    .orElse(null);
            model.addAttribute("selfTask", selfTask);

            // 성과/역량 각각 제출 여부
            boolean selfPerfSubmitted = false;
            boolean selfCompSubmitted = false;
            boolean selfPeerSubmitted = false;
            // 제출된 항목의 elementId별 평가 내용
            java.util.Map<Long, Evaluation> selfEvalMap = new java.util.HashMap<>();

            if (selfTask != null) {
                List<Evaluation> selfEvals = evaluationMapper.findByMappingId(selfTask.mappingId());
                selfEvalMap = selfEvals.stream()
                        .collect(Collectors.toMap(
                                Evaluation::getElementId,
                                e -> e,
                                (a, b) -> a));

                List<Long> submittedElementIds = selfEvals.stream()
                        .filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))
                        .map(Evaluation::getElementId)
                        .toList();

                if (!submittedElementIds.isEmpty()) {
                    Long myDeptIdForElements = (currentEmp != null) ? currentEmp.getDeptId() : null;
                    List<EvaluationElementDTO> allElements = elementService.getElementsWithFallback(selectedPeriod.periodId(),
                            myDeptIdForElements);
                    selfPerfSubmitted = allElements.stream()
                            .filter(el -> "PERFORMANCE".equals(el.elementTypeCode()))
                            .anyMatch(el -> submittedElementIds.contains(el.elementId()));
                    selfCompSubmitted = allElements.stream()
                            .filter(el -> "COMPETENCY".equals(el.elementTypeCode()))
                            .anyMatch(el -> submittedElementIds.contains(el.elementId()));
                    selfPeerSubmitted = allElements.stream()
                            .filter(el -> "MULTI_DIMENSIONAL".equals(el.elementTypeCode()))
                            .anyMatch(el -> submittedElementIds.contains(el.elementId()));
                }
            }
            model.addAttribute("selfPerfSubmitted", selfPerfSubmitted);
            model.addAttribute("selfCompSubmitted", selfCompSubmitted);
            model.addAttribute("selfPeerSubmitted", selfPeerSubmitted);

            // 부서 정보 및 가중치/항목 조회
            Long myDeptId = (currentEmp != null) ? currentEmp.getDeptId() : null;
            String targetRole = isLeader ? "LEADER" : "STAFF";
            
            boolean selfWeightValid = typeWeightService.isWeightSumValid(selectedPeriod.periodId(), myDeptId, targetRole);
            model.addAttribute("selfWeightValid", selfWeightValid);

            var typeWeights = typeWeightService.getTypeWeights(selectedPeriod.periodId(), myDeptId, targetRole);
            model.addAttribute("typeWeights", typeWeights);
            model.addAttribute("targetRole", targetRole);

            // 해당 차수의 평가요소 조회
            List<EvaluationElementDTO> allElements = elementService.getElementsWithFallback(selectedPeriod.periodId(), myDeptId);
            
            List<EvaluationElementDTO> perfElements = allElements.stream()
                    .filter(e -> "PERFORMANCE".equals(e.elementTypeCode()))
                    .toList();
            List<EvaluationElementDTO> compElements = allElements.stream()
                    .filter(e -> "COMPETENCY".equals(e.elementTypeCode()))
                    .toList();
            List<EvaluationElementDTO> peerElements = allElements.stream()
                    .filter(e -> "MULTI_DIMENSIONAL".equals(e.elementTypeCode()))
                    .toList();
            
            model.addAttribute("perfElements", perfElements);
            model.addAttribute("compElements", compElements);
            model.addAttribute("peerElements", peerElements);
            model.addAttribute("selfEvalMap", selfEvalMap);

            // 평가 시작 전(PLANNED) 알림 처리
            if ("PLANNED".equals(selectedPeriod.statusCode())) {
                model.addAttribute("infoMessage", "현재 평가 시작 전입니다. 정해진 평가 기간에만 작성이 가능합니다.");
            }

            // 6. 역순 진행 방지 (상위 평가자가 제출했는지 확인)
            if (selfTask != null) {
                java.util.Map<String, Object> lockInfo = mappingService.checkEvaluationLock(selfTask.mappingId());
                model.addAttribute("isLocked", lockInfo.get("isLocked"));
                model.addAttribute("lockedBy", lockInfo.get("lockedBy"));
            } else {
                model.addAttribute("isLocked", false);
            }
        } else {
            model.addAttribute("isLocked", false);
        }

        return "eval/my-evaluation/list";
    }

    /**
     * 자가평가 폼 페이지 (인라인 방식이 아닌 별도 페이지)
     */
    @GetMapping("/form")
    public String getForm(@RequestParam Long mappingId,
            @RequestParam(defaultValue = "PERFORMANCE") String evalType,
            Model model,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        if (!"PERFORMANCE".equals(evalType) && !"COMPETENCY".equals(evalType) && 
            !"MULTI_DIMENSIONAL".equals(evalType)) {
            evalType = "PERFORMANCE";
        }

        EvaluatorMappingDTO mapping = mappingService.getMappingById(mappingId);

        // SELF만 허용
        if (!"SELF".equals(mapping.relationTypeCode())) {
            redirectAttributes.addFlashAttribute("errorMessage", "자가평가만 이 페이지에서 진행할 수 있습니다.");
            return "redirect:/eval/my-evaluation";
        }

        // 평가 기간 유효성 검증 (상태 + 날짜)
        if (!periodService.isPeriodActive(mapping.periodId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "평가 기간이 아니거나 이미 종료되었습니다.");
            return "redirect:/eval/my-evaluation?periodId=" + mapping.periodId();
        }

        // 가중치 검증
        Employee evaluatee = employeeMapper.findById(mapping.evaluateeId()).orElse(null);
        Long evaluateeDeptId = (evaluatee != null) ? evaluatee.getDeptId() : null;
        
        // 피평가자가 부서장인지 여부 확인
        boolean evaluateeIsLeader = departmentMapper.countDepartmentsByLeaderId(mapping.evaluateeId()) > 0;
        String targetRole = evaluateeIsLeader ? "LEADER" : "STAFF";

        if (!typeWeightService.isWeightSumValid(mapping.periodId(), evaluateeDeptId, targetRole)) {
            log.warn("[MyEvaluation] getForm - Weight sum invalid for role: {}, redirecting to list", targetRole);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "[" + targetRole + "] 유형별 가중치 합계가 100%가 아닙니다. 관리자에게 가중치 설정을 요청하세요.");
            return "redirect:/eval/my-evaluation?periodId=" + mapping.periodId();
        }

        model.addAttribute("mapping", mapping);
        model.addAttribute("evalType", evalType);

        // 해당 차수의 평가요소 필터링 - 부서 전용 → 전사 공통 폴백
        List<EvaluationElementDTO> allElements = elementService.getElementsWithFallback(mapping.periodId(), evaluateeDeptId);
        final String finalEvalType = evalType;
        List<EvaluationElementDTO> elements = allElements.stream()
                .filter(e -> finalEvalType.equals(e.elementTypeCode()))
                .toList();
        
        log.info("[MyEvaluation] getForm - mappingId: {}, evalType: {}, found elements: {}, deptId: {}, role: {}", 
                mappingId, evalType, elements.size(), evaluateeDeptId, targetRole);
        
        model.addAttribute("elements", elements);
        model.addAttribute("mappingId", mappingId);

        // 기존 제출 데이터
        java.util.Map<Long, Evaluation> savedMap = evaluationMapper
                .findByMappingId(mappingId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        Evaluation::getElementId,
                        e -> e,
                        (a, b) -> a));
        model.addAttribute("savedMap", savedMap);

        // 제출 여부
        java.util.Set<Long> currentTypeElementIds = elements.stream()
                .map(EvaluationElementDTO::elementId)
                .collect(java.util.stream.Collectors.toSet());
        boolean submitted = savedMap.entrySet().stream()
                .filter(entry -> currentTypeElementIds.contains(entry.getKey()))
                .anyMatch(entry -> "SUBMITTED".equals(entry.getValue().getConfirmStatusCode()));
        model.addAttribute("submitted", submitted);

        // 6. 역순 진행 방지 (상위 평가자가 제출했는지 확인)
        java.util.Map<String, Object> lockInfo = mappingService.checkEvaluationLock(mappingId);
        model.addAttribute("isLocked", lockInfo.get("isLocked"));
        model.addAttribute("lockedBy", lockInfo.get("lockedBy"));

        return "eval/my-evaluation/form";
    }

    /**
     * 자가평가 통합 마법사 페이지
     */
    @GetMapping("/wizard")
    public String getWizard(@RequestParam Long mappingId,
            Model model,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        EvaluatorMappingDTO mapping = mappingService.getMappingById(mappingId);

        // SELF만 허용
        if (!"SELF".equals(mapping.relationTypeCode())) {
            redirectAttributes.addFlashAttribute("errorMessage", "자가평가만 이 페이지에서 진행할 수 있습니다.");
            return "redirect:/eval/my-evaluation";
        }

        // 평가 기간 유효성 검증 (상태 + 날짜)
        if (!periodService.isPeriodActive(mapping.periodId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "평가 기간이 아니거나 이미 종료되었습니다.");
            return "redirect:/eval/my-evaluation?periodId=" + mapping.periodId();
        }

        // 가중치 검증
        Employee evaluatee = employeeMapper.findById(mapping.evaluateeId()).orElse(null);
        Long evaluateeDeptId = (evaluatee != null) ? evaluatee.getDeptId() : null;
        
        boolean evaluateeIsLeader = departmentMapper.countDepartmentsByLeaderId(mapping.evaluateeId()) > 0;
        String targetRole = evaluateeIsLeader ? "LEADER" : "STAFF";

        if (!typeWeightService.isWeightSumValid(mapping.periodId(), evaluateeDeptId, targetRole)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "[" + targetRole + "] 유형별 가중치 합계가 100%가 아닙니다.");
            return "redirect:/eval/my-evaluation?periodId=" + mapping.periodId();
        }

        model.addAttribute("mapping", mapping);
        model.addAttribute("evaluateeIsLeader", evaluateeIsLeader);

        // 모든 평가요소 조회 및 분류
        List<EvaluationElementDTO> allElements = elementService.getElementsWithFallback(mapping.periodId(), evaluateeDeptId);
        
        List<EvaluationElementDTO> perfElements = new java.util.ArrayList<>();
        List<EvaluationElementDTO> compElements = new java.util.ArrayList<>();
        List<EvaluationElementDTO> peerElements = new java.util.ArrayList<>();

        if (evaluateeIsLeader) {
            // 부서장은 다면평가만 수행
            peerElements = allElements.stream()
                    .filter(e -> "MULTI_DIMENSIONAL".equals(e.elementTypeCode()))
                    .toList();
        } else {
            // 부서원은 성과 및 역량 평가 수행
            perfElements = allElements.stream()
                    .filter(e -> "PERFORMANCE".equals(e.elementTypeCode()))
                    .toList();
            compElements = allElements.stream()
                    .filter(e -> "COMPETENCY".equals(e.elementTypeCode()))
                    .toList();
        }

        model.addAttribute("perfElements", perfElements);
        model.addAttribute("compElements", compElements);
        model.addAttribute("peerElements", peerElements);
        model.addAttribute("mappingId", mappingId);

        // 기존 제출 데이터
        java.util.Map<Long, Evaluation> savedMap = evaluationMapper
                .findByMappingId(mappingId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        Evaluation::getElementId,
                        e -> e,
                        (a, b) -> a));
        model.addAttribute("savedMap", savedMap);

        // 전체 제출 여부 (하나라도 SUBMITTED면 submitted로 간주하여 문구 표시)
        boolean submitted = savedMap.values().stream()
                .anyMatch(e -> "SUBMITTED".equals(e.getConfirmStatusCode()));
        model.addAttribute("submitted", submitted);

        // 잠금 상태
        java.util.Map<String, Object> lockInfo = mappingService.checkEvaluationLock(mappingId);
        model.addAttribute("isLocked", lockInfo.get("isLocked"));
        model.addAttribute("lockedBy", lockInfo.get("lockedBy"));

        return "eval/my-evaluation/wizard";
    }

    /**
     * 자가평가 제출
     */

    @PostMapping("/submit")
    public String submitForm(@RequestParam Long mappingId,
            @RequestParam java.util.Map<String, String> params,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        Long empId = Long.parseLong(userDetails.getUsername());
        log.info("[자가평가 제출] empId={}, mappingId={}", empId, mappingId);

        // 가중치 검증
        EvaluatorMappingDTO submitMapping = mappingService.getMappingById(mappingId);
        Employee submitEvaluatee = employeeMapper.findById(submitMapping.evaluateeId()).orElse(null);
        Long submitDeptId = (submitEvaluatee != null) ? submitEvaluatee.getDeptId() : null;
        
        boolean submitIsLeader = departmentMapper.countDepartmentsByLeaderId(submitMapping.evaluateeId()) > 0;
        String targetRole = submitIsLeader ? "LEADER" : "STAFF";

        if (!typeWeightService.isWeightSumValid(submitMapping.periodId(), submitDeptId, targetRole)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "유형별 가중치 합계가 100%가 아니어서 평가를 제출할 수 없습니다.");
            return "redirect:/eval/my-evaluation/wizard?mappingId=" + mappingId;
        }

        // 평가 기간 유효성 검증 (제출 시점 재확인)
        if (!periodService.isPeriodActive(submitMapping.periodId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "평가 기간이 종료되어 제출할 수 없습니다.");
            return "redirect:/eval/my-evaluation?periodId=" + submitMapping.periodId();
        }

        // SELF만 허용
        if (!"SELF".equals(submitMapping.relationTypeCode())) {
            redirectAttributes.addFlashAttribute("errorMessage", "자가평가만 이 페이지에서 제출할 수 있습니다.");
            return "redirect:/eval/my-evaluation";
        }

        // 역순 진행 방지 검증
        java.util.Map<String, Object> lockInfo = mappingService.checkEvaluationLock(mappingId);
        if ((Boolean) lockInfo.get("isLocked")) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                lockInfo.get("lockedBy") + "가 평가를 완료하여 더 이상 수정할 수 없습니다.");
            return "redirect:/eval/my-evaluation/wizard?mappingId=" + mappingId;
        }

        // 평가 데이터 Upsert 처리
        try {
            evaluationService.upsertEvaluations(mappingId, params, empId);
        } catch (NumberFormatException e) {
            log.warn("[자가평가 제출] 점수 파싱 실패: mappingId={}", mappingId);
            redirectAttributes.addFlashAttribute("errorMessage", "잘못된 점수 형식입니다.");
            return "redirect:/eval/my-evaluation/wizard?mappingId=" + mappingId;
        }

        redirectAttributes.addFlashAttribute("successMessage", "자가평가가 성공적으로 제출되었습니다.");
        return "redirect:/eval/my-evaluation?periodId=" + submitMapping.periodId();
    }
}
