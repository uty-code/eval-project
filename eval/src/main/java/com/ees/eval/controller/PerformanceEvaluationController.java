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

/**
 * 성과/역량 평가 컨트롤러
 * evalType 파라미터(PERFORMANCE / COMPETENCY)를 기반으로
 * 자가평가 및 부서장 평가 화면을 범용적으로 제공합니다.
 */
@Slf4j
@Controller
@RequestMapping("/eval/performance")
@RequiredArgsConstructor
public class PerformanceEvaluationController {

    private final EvaluationPeriodService periodService;
    private final EvaluatorMappingService mappingService;
    private final EvaluationElementService elementService;
    private final EvaluationTypeWeightService typeWeightService;
    private final EvaluationMapper evaluationMapper;
    private final EvaluatorMappingMapper evaluatorMappingMapper;
    private final EmployeeMapper employeeMapper;

    /**
     * 피평가자의 부서에 맞는 평가요소를 조회합니다.
     * 부서 전용 설정이 없으면 전사 공통(dept_id IS NULL)으로 폴백합니다.
     */
    private List<EvaluationElementDTO> getElementsWithFallback(Long periodId, Long deptId) {
        if (deptId != null) {
            List<EvaluationElementDTO> deptElements = elementService.getElementsByPeriodId(periodId, deptId);
            if (!deptElements.isEmpty()) {
                return deptElements;
            }
        }
        return elementService.getElementsByPeriodId(periodId, null);
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) Long periodId,
                       @RequestParam(defaultValue = "PERFORMANCE") String evalType,
                       @AuthenticationPrincipal UserDetails userDetails) {

        // evalType 검증
        if (!"PERFORMANCE".equals(evalType) && !"COMPETENCY".equals(evalType)) {
            evalType = "PERFORMANCE";
        }
        model.addAttribute("evalType", evalType);
        model.addAttribute("activeMenu", "COMPETENCY".equals(evalType) ? "competency-eval" : "performance-eval");

        Long empId = Long.parseLong(userDetails.getUsername());

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

            List<EvaluatorMappingDTO> myTasks = mappingService.getMyEvaluationTasks(selectedPeriod.periodId(), empId);

            EvaluatorMappingDTO selfTask = myTasks.stream()
                .filter(m -> "SELF".equals(m.relationTypeCode()))
                .findFirst()
                .orElse(null);

            List<EvaluatorMappingDTO> teamTasks = myTasks.stream()
                .filter(m -> "MANAGER".equals(m.relationTypeCode()) || "EXECUTIVE".equals(m.relationTypeCode()))
                .toList();

            model.addAttribute("selfTask", selfTask);
            model.addAttribute("tasks", teamTasks);

            // 자가평가 성과/역량 각각 제출 여부 확인
            boolean selfPerfSubmitted = false;
            boolean selfCompSubmitted = false;
            if (selfTask != null) {
                List<Evaluation> selfEvals = evaluationMapper.findByMappingId(selfTask.mappingId());
                // 제출된 항목들 중 elementType이 PERFORMANCE인 항목이 있으면 성과 제출 완료로 간주
                List<Long> submittedElementIds = selfEvals.stream()
                    .filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))
                    .map(Evaluation::getElementId)
                    .toList();
                if (!submittedElementIds.isEmpty()) {
                    Employee selfEmpInfo = employeeMapper.findById(empId).orElse(null);
                    Long selfDeptId = (selfEmpInfo != null) ? selfEmpInfo.getDeptId() : null;
                    List<EvaluationElementDTO> allElements =
                        getElementsWithFallback(selectedPeriod.periodId(), selfDeptId);
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

            // 팀원 성과/역량 평가 제출 여부 Map
            java.util.Map<Long, Boolean> teamPerfSubmittedMap = new java.util.HashMap<>();
            java.util.Map<Long, Boolean> teamCompSubmittedMap = new java.util.HashMap<>();
            // 피평가자 자가평가 제출 여부 Map (자가평가가 있어야 부서장 진입 가능)
            java.util.Map<Long, Boolean> evaluateeSelfSubmittedMap = new java.util.HashMap<>();

            List<EvaluationElementDTO> allPeriodElements = selectedPeriod != null
                ? elementService.getElementsByPeriodId(selectedPeriod.periodId(), null)
                : java.util.Collections.emptyList();

            for (EvaluatorMappingDTO task : teamTasks) {
                List<Evaluation> evals = evaluationMapper.findByMappingId(task.mappingId());
                List<Long> submittedIds = evals.stream()
                    .filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))
                    .map(Evaluation::getElementId)
                    .toList();

                boolean perfSubmitted = allPeriodElements.stream()
                    .filter(el -> "PERFORMANCE".equals(el.elementTypeCode()))
                    .anyMatch(el -> submittedIds.contains(el.elementId()));
                boolean compSubmitted = allPeriodElements.stream()
                    .filter(el -> "COMPETENCY".equals(el.elementTypeCode()))
                    .anyMatch(el -> submittedIds.contains(el.elementId()));

                teamPerfSubmittedMap.put(task.mappingId(), perfSubmitted);
                teamCompSubmittedMap.put(task.mappingId(), compSubmitted);

                // 피평가자의 SELF 매핑에서 제출 여부 확인
                boolean selfSubmittedForTask = evaluatorMappingMapper
                    .findByEvaluateeId(selectedPeriod.periodId(), task.evaluateeId())
                    .stream()
                    .filter(m -> "SELF".equals(m.getRelationTypeCode()) && "n".equals(m.getIsDeleted()))
                    .findFirst()
                    .map(selfMapping -> evaluationMapper.findByMappingId(selfMapping.getMappingId())
                        .stream()
                        .anyMatch(e -> "SUBMITTED".equals(e.getConfirmStatusCode())))
                    .orElse(false);
                evaluateeSelfSubmittedMap.put(task.mappingId(), selfSubmittedForTask);
            }
            model.addAttribute("teamPerfSubmittedMap", teamPerfSubmittedMap);
            model.addAttribute("teamCompSubmittedMap", teamCompSubmittedMap);
            model.addAttribute("evaluateeSelfSubmittedMap", evaluateeSelfSubmittedMap);

            // 부서별 유형별 가중치 합계 100 검증
            // 로그인 사용자의 부서 가중치가 유효한지 확인 (자가평가용)
            Employee currentEmp = employeeMapper.findById(empId).orElse(null);
            Long myDeptId = (currentEmp != null) ? currentEmp.getDeptId() : null;
            boolean selfWeightValid = typeWeightService.isWeightSumValid(selectedPeriod.periodId(), myDeptId, "STAFF");
            model.addAttribute("selfWeightValid", selfWeightValid);

            // 팀원별 가중치 유효성 Map (각 팀원의 부서 기준)
            java.util.Map<Long, Boolean> teamWeightValidMap = new java.util.HashMap<>();
            for (EvaluatorMappingDTO task : teamTasks) {
                Employee evaluatee = employeeMapper.findById(task.evaluateeId()).orElse(null);
                Long evaluateeDeptId = (evaluatee != null) ? evaluatee.getDeptId() : null;
                boolean weightValid = typeWeightService.isWeightSumValid(selectedPeriod.periodId(), evaluateeDeptId, "STAFF");
                teamWeightValidMap.put(task.mappingId(), weightValid);
            }
            model.addAttribute("teamWeightValidMap", teamWeightValidMap);
        }

        return "eval/performance/list";
    }

    @GetMapping("/form")
    public String getForm(@RequestParam Long mappingId,
                          @RequestParam(defaultValue = "PERFORMANCE") String evalType,
                          Model model,
                          @AuthenticationPrincipal UserDetails userDetails,
                          RedirectAttributes redirectAttributes) {

        // evalType 검증 (PERFORMANCE 또는 COMPETENCY만 허용)
        if (!"PERFORMANCE".equals(evalType) && !"COMPETENCY".equals(evalType)) {
            evalType = "PERFORMANCE";
        }

        // 매핑 정보 조회 (피평가자 정보, 차수 정보 포함)
        EvaluatorMappingDTO mapping = mappingService.getMappingById(mappingId);

        // 부서별 유형별 가중치 합계 100 검증
        Employee evaluatee = employeeMapper.findById(mapping.evaluateeId()).orElse(null);
        Long evaluateeDeptId = (evaluatee != null) ? evaluatee.getDeptId() : null;
        if (!typeWeightService.isWeightSumValid(mapping.periodId(), evaluateeDeptId, "STAFF")) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "유형별 가중치 합계가 100%가 아닙니다. 관리자에게 가중치 설정을 요청하세요.");
            return "redirect:/eval/performance?periodId=" + mapping.periodId();
        }

        model.addAttribute("mapping", mapping);
        model.addAttribute("evalType", evalType);

        // 해당 차수의 평가요소 목록 조회 → evalType에 맞는 항목만 필터링 (부서 전용 → 전사 공통 폴백)
        List<EvaluationElementDTO> allElements = getElementsWithFallback(mapping.periodId(), evaluateeDeptId);
        final String finalEvalType = evalType;
        List<EvaluationElementDTO> elements = allElements.stream()
            .filter(e -> finalEvalType.equals(e.elementTypeCode()))
            .toList();

        model.addAttribute("elements", elements);
        model.addAttribute("mappingId", mappingId);

        // 기존에 제출된 평가 내용 조회 → elementId 기준 Map으로 변환
        java.util.Map<Long, com.ees.eval.domain.Evaluation> savedMap = evaluationMapper
            .findByMappingId(mappingId)
            .stream()
            .collect(java.util.stream.Collectors.toMap(
                com.ees.eval.domain.Evaluation::getElementId,
                e -> e,
                (a, b) -> a  // 중복 키 충돌 방지
            ));
        model.addAttribute("savedMap", savedMap);

        // 현재 evalType 항목 중 하나라도 SUBMITTED면 제출 완료로 판단
        java.util.Set<Long> currentTypeElementIds = elements.stream()
            .map(EvaluationElementDTO::elementId)
            .collect(java.util.stream.Collectors.toSet());
        boolean submitted = savedMap.entrySet().stream()
            .filter(entry -> currentTypeElementIds.contains(entry.getKey()))
            .anyMatch(entry -> "SUBMITTED".equals(entry.getValue().getConfirmStatusCode()));
        model.addAttribute("submitted", submitted);

        // MANAGER/EXECUTIVE 평가인 경우: 피평가자의 자가평가 내용을 참고용으로 조회
        if ("MANAGER".equals(mapping.relationTypeCode()) || "EXECUTIVE".equals(mapping.relationTypeCode())) {
            java.util.Map<Long, String> selfEvalMap = evaluatorMappingMapper
                .findByEvaluateeId(mapping.periodId(), mapping.evaluateeId())
                .stream()
                .filter(m -> "SELF".equals(m.getRelationTypeCode()) && "n".equals(m.getIsDeleted()))
                .findFirst()
                .map(selfMapping -> evaluationMapper.findByMappingId(selfMapping.getMappingId())
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(
                        com.ees.eval.domain.Evaluation::getElementId,
                        e -> e.getComments() != null ? e.getComments() : "",
                        (a, b) -> a
                    )))
                .orElse(java.util.Collections.emptyMap());
            model.addAttribute("selfEvalMap", selfEvalMap);
        }

        return "eval/performance/form";
    }

    /**
     * 평가 폼 제출 처리 - 각 평가요소에 대한 서술형 코멘트를 저장합니다.
     * 이미 저장된 데이터가 있으면 UPDATE, 없으면 INSERT(Upsert) 방식으로 처리합니다.
     */
    @PostMapping("/submit")
    public String submitForm(@RequestParam Long mappingId,
                             @RequestParam java.util.Map<String, String> params,
                             @AuthenticationPrincipal UserDetails userDetails,
                             RedirectAttributes redirectAttributes) {

        Long empId = Long.parseLong(userDetails.getUsername());
        log.info("[평가제출] empId={}, mappingId={}", empId, mappingId);

        // 부서별 유형별 가중치 합계 100 검증
        EvaluatorMappingDTO submitMapping = mappingService.getMappingById(mappingId);
        Employee submitEvaluatee = employeeMapper.findById(submitMapping.evaluateeId()).orElse(null);
        Long submitDeptId = (submitEvaluatee != null) ? submitEvaluatee.getDeptId() : null;
        if (!typeWeightService.isWeightSumValid(submitMapping.periodId(), submitDeptId, "STAFF")) {
            String currentEvalType = params.getOrDefault("evalType", "PERFORMANCE");
            redirectAttributes.addFlashAttribute("errorMessage",
                "유형별 가중치 합계가 100%가 아니어서 평가를 제출할 수 없습니다.");
            return "redirect:/eval/performance/form?mappingId=" + mappingId + "&evalType=" + currentEvalType;
        }

        // elementId 추출 및 데이터 그룹화
        java.util.Set<Long> elementIds = new java.util.HashSet<>();
        params.keySet().forEach(key -> {
            if (key.startsWith("comment_") || key.startsWith("score_")) {
                try {
                    elementIds.add(Long.parseLong(key.substring(key.indexOf("_") + 1)));
                } catch (Exception ignore) {}
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
                    log.warn("[평가제출] 점수 파싱 실패: elementId={}, scoreStr={}", elementId, scoreStr);
                    String currentEvalType = params.getOrDefault("evalType", "PERFORMANCE");
                    redirectAttributes.addFlashAttribute("errorMessage", "잘못된 점수 형식입니다.");
                    return "redirect:/eval/performance/form?mappingId=" + mappingId + "&evalType=" + currentEvalType;
                }
            }

            final java.math.BigDecimal finalScore = score;
            final String finalComment = (comment != null) ? comment.trim() : "";

            // 이미 저장된 레코드가 있으면 UPDATE, 없으면 INSERT
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
                    }
                );
        }

        // evalType도 함께 넘겨서 제출 후 같은 타입 폼으로 돌아오게 처리
        String evalType = params.getOrDefault("evalType", "PERFORMANCE");
        redirectAttributes.addFlashAttribute("successMessage", "평가가 성공적으로 제출되었습니다.");
        return "redirect:/eval/performance/form?mappingId=" + mappingId + "&evalType=" + evalType;
    }
}
