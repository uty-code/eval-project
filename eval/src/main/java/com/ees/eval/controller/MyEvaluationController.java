package com.ees.eval.controller;

import com.ees.eval.domain.Employee;
import com.ees.eval.domain.Evaluation;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationPeriodService;
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
public class MyEvaluationController {

    private final EvaluationPeriodService periodService;
    private final EvaluatorMappingService mappingService;
    private final EvaluationElementService elementService;
    private final EvaluationTypeWeightService typeWeightService;
    private final EvaluationMapper evaluationMapper;
    private final EmployeeMapper employeeMapper;

    /**
     * 사용자의 부서에 맞는 평가요소를 조회합니다.
     * 부서 전용 설정이 없으면 전사 공통(dept_id IS NULL)으로 폴백합니다.
     */
    private List<EvaluationElementDTO> getElementsWithFallback(Long periodId, Long deptId) {
        // 1차: 부서 전용 항목 조회
        if (deptId != null) {
            List<EvaluationElementDTO> deptElements = elementService.getElementsByPeriodId(periodId, deptId);
            if (!deptElements.isEmpty()) {
                return deptElements;
            }
        }
        // 2차: 전사 공통 항목으로 폴백
        return elementService.getElementsByPeriodId(periodId, null);
    }

    /**
     * 나의 자가평가 메인 페이지
     */
    @GetMapping
    public String list(Model model,
            @RequestParam(required = false) Long periodId,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long empId = Long.parseLong(userDetails.getUsername());

        // 사원 정보 조회
        Employee currentEmp = employeeMapper.findById(empId).orElse(null);
        model.addAttribute("currentEmp", currentEmp);

        // 차수 목록
        List<EvaluationPeriodDTO> periods = periodService.getAllPeriods();
        model.addAttribute("periods", periods);

        EvaluationPeriodDTO selectedPeriod = null;
        if (periodId != null) {
            selectedPeriod = periodService.getPeriodById(periodId);
        } else if (!periods.isEmpty()) {
            selectedPeriod = periods.stream()
                    .filter(p -> "ACTIVE".equals(p.statusCode()))
                    .findFirst()
                    .orElse(periods.get(0));
        }

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
                    List<EvaluationElementDTO> allElements = getElementsWithFallback(selectedPeriod.periodId(),
                            myDeptIdForElements);
                    selfPerfSubmitted = allElements.stream()
                            .filter(el -> "PERFORMANCE".equals(el.elementTypeCode()))
                            .anyMatch(el -> submittedElementIds.contains(el.elementId()));
                    selfCompSubmitted = allElements.stream()
                            .filter(el -> "COMPETENCY".equals(el.elementTypeCode()))
                            .anyMatch(el -> submittedElementIds.contains(el.elementId()));
                }
            }
            model.addAttribute("selfPerfSubmitted", selfPerfSubmitted);
            model.addAttribute("selfCompSubmitted", selfCompSubmitted);

            // 평가요소 목록 (성과/역량 분리) - 부서 전용 → 전사 공통 폴백
            Long myDeptId2 = (currentEmp != null) ? currentEmp.getDeptId() : null;
            List<EvaluationElementDTO> allElements = getElementsWithFallback(selectedPeriod.periodId(), myDeptId2);
            List<EvaluationElementDTO> perfElements = allElements.stream()
                    .filter(e -> "PERFORMANCE".equals(e.elementTypeCode()))
                    .toList();
            List<EvaluationElementDTO> compElements = allElements.stream()
                    .filter(e -> "COMPETENCY".equals(e.elementTypeCode()))
                    .toList();
            model.addAttribute("perfElements", perfElements);
            model.addAttribute("compElements", compElements);
            model.addAttribute("selfEvalMap", selfEvalMap);

            // 가중치 검증
            Long myDeptId = (currentEmp != null) ? currentEmp.getDeptId() : null;
            boolean selfWeightValid = typeWeightService.isWeightSumValid(selectedPeriod.periodId(), myDeptId, "STAFF");
            model.addAttribute("selfWeightValid", selfWeightValid);

            // 성과/역량 가중치 비중 정보
            var typeWeights = typeWeightService.getTypeWeights(selectedPeriod.periodId(), myDeptId, "STAFF");
            model.addAttribute("typeWeights", typeWeights);
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

        if (!"PERFORMANCE".equals(evalType) && !"COMPETENCY".equals(evalType)) {
            evalType = "PERFORMANCE";
        }

        EvaluatorMappingDTO mapping = mappingService.getMappingById(mappingId);

        // SELF만 허용
        if (!"SELF".equals(mapping.relationTypeCode())) {
            redirectAttributes.addFlashAttribute("errorMessage", "자가평가만 이 페이지에서 진행할 수 있습니다.");
            return "redirect:/eval/my-evaluation";
        }

        // 가중치 검증
        Employee evaluatee = employeeMapper.findById(mapping.evaluateeId()).orElse(null);
        Long evaluateeDeptId = (evaluatee != null) ? evaluatee.getDeptId() : null;
        if (!typeWeightService.isWeightSumValid(mapping.periodId(), evaluateeDeptId, "STAFF")) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "유형별 가중치 합계가 100%가 아닙니다. 관리자에게 가중치 설정을 요청하세요.");
            return "redirect:/eval/my-evaluation?periodId=" + mapping.periodId();
        }

        model.addAttribute("mapping", mapping);
        model.addAttribute("evalType", evalType);

        // 해당 차수의 평가요소 필터링 - 부서 전용 → 전사 공통 폴백
        List<EvaluationElementDTO> allElements = getElementsWithFallback(mapping.periodId(), evaluateeDeptId);
        final String finalEvalType = evalType;
        List<EvaluationElementDTO> elements = allElements.stream()
                .filter(e -> finalEvalType.equals(e.elementTypeCode()))
                .toList();
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

        return "eval/my-evaluation/form";
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
        if (!typeWeightService.isWeightSumValid(submitMapping.periodId(), submitDeptId, "STAFF")) {
            String currentEvalType = params.getOrDefault("evalType", "PERFORMANCE");
            redirectAttributes.addFlashAttribute("errorMessage",
                    "유형별 가중치 합계가 100%가 아니어서 평가를 제출할 수 없습니다.");
            return "redirect:/eval/my-evaluation/form?mappingId=" + mappingId + "&evalType=" + currentEvalType;
        }

        // SELF만 허용
        if (!"SELF".equals(submitMapping.relationTypeCode())) {
            redirectAttributes.addFlashAttribute("errorMessage", "자가평가만 이 페이지에서 제출할 수 있습니다.");
            return "redirect:/eval/my-evaluation";
        }

        // elementId 추출 및 데이터 그룹화
        java.util.Set<Long> elementIds = new java.util.HashSet<>();
        params.keySet().forEach(key -> {
            if (key.startsWith("comment_") || key.startsWith("score_")) {
                try {
                    elementIds.add(Long.parseLong(key.substring(key.indexOf("_") + 1)));
                } catch (Exception ignore) {
                }
            }
        });

        for (Long elementId : elementIds) {
            String comment = params.get("comment_" + elementId);
            String scoreStr = params.get("score_" + elementId);

            java.math.BigDecimal score = null;
            if (scoreStr != null && !scoreStr.trim().isEmpty()) {
                try {
                    score = new java.math.BigDecimal(scoreStr.trim());
                } catch (Exception e) {
                    log.warn("[자가평가 제출] 점수 파싱 실패: elementId={}, scoreStr={}", elementId, scoreStr);
                    String currentEvalType = params.getOrDefault("evalType", "PERFORMANCE");
                    redirectAttributes.addFlashAttribute("errorMessage", "잘못된 점수 형식입니다.");
                    return "redirect:/eval/my-evaluation/form?mappingId=" + mappingId + "&evalType=" + currentEvalType;
                }
            }

            final java.math.BigDecimal finalScore = score;
            final String finalComment = (comment != null) ? comment.trim() : "";

            evaluationMapper.findByMappingIdAndElementId(mappingId, elementId)
                    .ifPresentOrElse(
                            existing -> {
                                existing.setComments(finalComment);
                                existing.setScore(finalScore);
                                existing.setConfirmStatusCode("SUBMITTED");
                                existing.preUpdate();
                                evaluationMapper.update(existing);
                            },
                            () -> {
                                Evaluation eval = Evaluation.builder()
                                        .mappingId(mappingId)
                                        .elementId(elementId)
                                        .comments(finalComment)
                                        .score(finalScore)
                                        .confirmStatusCode("SUBMITTED")
                                        .build();
                                eval.prePersist();
                                eval.setCreatedBy(empId);
                                eval.setUpdatedBy(empId);
                                evaluationMapper.insert(eval);
                            });
        }

        String evalType = params.getOrDefault("evalType", "PERFORMANCE");
        redirectAttributes.addFlashAttribute("successMessage", "자가평가가 성공적으로 제출되었습니다.");
        return "redirect:/eval/my-evaluation/form?mappingId=" + mappingId + "&evalType=" + evalType;
    }
}
