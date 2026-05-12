package com.ees.eval.controller;

import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'EXECUTIVE')")
public class PerformanceEvaluationController {

    private final EvaluationPeriodService periodService;
    private final EvaluatorMappingService mappingService;
    private final EvaluationElementService elementService;
    private final EvaluationTypeWeightService typeWeightService;
    private final EvaluationService evaluationService;
    private final EvaluationMapper evaluationMapper;
    private final EvaluatorMappingMapper evaluatorMappingMapper;
    private final EmployeeMapper employeeMapper;

    /**
     * 부서별 평가 요소를 캐싱하여 동일 부서에 대한 중복 DB 호출을 방지합니다.
     */
    private List<EvaluationElementDTO> getCachedElements(
            java.util.Map<Long, List<EvaluationElementDTO>> cache, Long periodId, Long deptId) {
        Long cacheKey = deptId != null ? deptId : -1L;
        return cache.computeIfAbsent(cacheKey, k -> elementService.getElementsWithFallback(periodId, deptId));
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

        List<EvaluationPeriodDTO> periods = periodService.getInProgressPeriods();
        model.addAttribute("periods", periods);

        EvaluationPeriodDTO selectedPeriod = periodService.resolveSelectedPeriod(periodId, periods);

        if (selectedPeriod != null) {
            final Long finalPeriodId = selectedPeriod.periodId();
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

            // ========== [최적화] 루프 밖에서 데이터 일괄 조회 ==========

            // (A) 로그인 사용자 정보 조회 (1회)
            Employee currentEmp = employeeMapper.findById(empId).orElse(null);
            Long myDeptId = (currentEmp != null) ? currentEmp.getDeptId() : null;

            // (B) 팀 태스크의 피평가자 ID 및 매핑 ID 수집
            java.util.List<Long> teamEvaluateeIds = teamTasks.stream()
                    .map(EvaluatorMappingDTO::evaluateeId)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            java.util.List<Long> teamMappingIds = teamTasks.stream()
                    .map(EvaluatorMappingDTO::mappingId)
                    .collect(java.util.stream.Collectors.toList());

            // 자가평가 매핑 ID도 포함
            if (selfTask != null) {
                teamMappingIds = new java.util.ArrayList<>(teamMappingIds);
                teamMappingIds.add(selfTask.mappingId());
            }

            // (C) 피평가자 사원 정보 일괄 조회 (1회)
            java.util.Map<Long, Employee> evaluateeMap = new java.util.HashMap<>();
            if (!teamEvaluateeIds.isEmpty()) {
                evaluateeMap = employeeMapper.findByIds(teamEvaluateeIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Employee::getEmpId, e -> e, (a, b) -> a));
            }

            // (D) 모든 관련 매핑의 평가 데이터 일괄 조회 (1회)
            java.util.Map<Long, java.util.List<Evaluation>> evalGroupMap = new java.util.HashMap<>();
            if (!teamMappingIds.isEmpty()) {
                evalGroupMap = evaluationMapper.findByMappingIds(teamMappingIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(Evaluation::getMappingId));
            }

            // (E) 피평가자들의 SELF 매핑 일괄 조회 (1회) — 자가평가 제출 여부 확인용
            java.util.Map<Long, com.ees.eval.domain.EvaluatorMapping> selfMappingByEvaluateeMap = new java.util.HashMap<>();
            java.util.List<Long> selfMappingIdsToFetch = new java.util.ArrayList<>();
            // 피평가자별 전체 매핑 그룹 (잠금 체크용으로도 재사용)
            java.util.Map<Long, java.util.List<com.ees.eval.domain.EvaluatorMapping>> allMappingsByEvaluatee = new java.util.HashMap<>();
            java.util.List<Long> allDownstreamMappingIds = new java.util.ArrayList<>();
            if (!teamEvaluateeIds.isEmpty()) {
                java.util.List<com.ees.eval.domain.EvaluatorMapping> allRelatedMappings =
                        evaluatorMappingMapper.findByEvaluateeIds(selectedPeriod.periodId(), teamEvaluateeIds);
                // 피평가자별 그룹화
                allMappingsByEvaluatee = allRelatedMappings.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                com.ees.eval.domain.EvaluatorMapping::getEvaluateeId));
                for (com.ees.eval.domain.EvaluatorMapping m : allRelatedMappings) {
                    if ("SELF".equals(m.getRelationTypeCode()) && "n".equals(m.getIsDeleted())) {
                        selfMappingByEvaluateeMap.put(m.getEvaluateeId(), m);
                        selfMappingIdsToFetch.add(m.getMappingId());
                    }
                    // MANAGER/EXECUTIVE 매핑의 평가 데이터도 잠금 체크에 필요
                    if ("MANAGER".equals(m.getRelationTypeCode()) || "EXECUTIVE".equals(m.getRelationTypeCode())) {
                        if (!teamMappingIds.contains(m.getMappingId())) {
                            allDownstreamMappingIds.add(m.getMappingId());
                        }
                    }
                }
            }

            // SELF 매핑들의 평가 데이터도 일괄 조회 (1회)
            // + 다운스트림 매핑(잠금 체크용) 평가 데이터도 함께 조회
            java.util.List<Long> additionalMappingIds = new java.util.ArrayList<>(selfMappingIdsToFetch);
            additionalMappingIds.addAll(allDownstreamMappingIds);
            if (!additionalMappingIds.isEmpty()) {
                java.util.List<Evaluation> additionalEvals = evaluationMapper.findByMappingIds(additionalMappingIds);
                for (Evaluation e : additionalEvals) {
                    evalGroupMap.computeIfAbsent(e.getMappingId(), k -> new java.util.ArrayList<>()).add(e);
                }
            }

            // (F) 평가 요소 부서별 캐싱 (부서당 1회만 조회)
            java.util.Map<Long, java.util.List<EvaluationElementDTO>> elementCacheByDeptId = new java.util.HashMap<>();

            // (G) 가중치 유효성 부서별 캐싱
            java.util.Map<Long, Boolean> weightValidCacheByDeptId = new java.util.HashMap<>();

            // ========== 자가평가 제출 여부 확인 (메모리에서) ==========
            boolean selfPerfSubmitted = false;
            boolean selfCompSubmitted = false;
            if (selfTask != null) {
                java.util.List<Evaluation> selfEvals = evalGroupMap.getOrDefault(selfTask.mappingId(), java.util.Collections.emptyList());
                java.util.List<Long> submittedElementIds = selfEvals.stream()
                        .filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))
                        .map(Evaluation::getElementId)
                        .toList();
                if (!submittedElementIds.isEmpty()) {
                    java.util.List<EvaluationElementDTO> allElements = getCachedElements(elementCacheByDeptId, selectedPeriod.periodId(), myDeptId);
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

            // ========== 팀원별 상태 계산 (메모리에서 — DB 호출 없음) ==========
            java.util.Map<Long, Boolean> teamPerfSubmittedMap = new java.util.HashMap<>();
            java.util.Map<Long, Boolean> teamCompSubmittedMap = new java.util.HashMap<>();
            java.util.Map<Long, Boolean> evaluateeSelfSubmittedMap = new java.util.HashMap<>();
            java.util.Map<Long, Boolean> teamWeightValidMap = new java.util.HashMap<>();

            for (EvaluatorMappingDTO task : teamTasks) {
                Employee evaluatee = evaluateeMap.get(task.evaluateeId());
                Long evaluateeDeptId = (evaluatee != null) ? evaluatee.getDeptId() : null;
                java.util.List<EvaluationElementDTO> elementsForTask = getCachedElements(elementCacheByDeptId, selectedPeriod.periodId(), evaluateeDeptId);

                // 팀원 성과/역량 제출 여부 (메모리에서 확인)
                java.util.List<Evaluation> evals = evalGroupMap.getOrDefault(task.mappingId(), java.util.Collections.emptyList());
                java.util.List<Long> submittedIds = evals.stream()
                        .filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))
                        .map(Evaluation::getElementId)
                        .toList();

                boolean perfSubmitted = elementsForTask.stream()
                        .filter(el -> "PERFORMANCE".equals(el.elementTypeCode()))
                        .anyMatch(el -> submittedIds.contains(el.elementId()));
                boolean compSubmitted = elementsForTask.stream()
                        .filter(el -> "COMPETENCY".equals(el.elementTypeCode()))
                        .anyMatch(el -> submittedIds.contains(el.elementId()));
                teamPerfSubmittedMap.put(task.mappingId(), perfSubmitted);
                teamCompSubmittedMap.put(task.mappingId(), compSubmitted);

                // 피평가자의 자가평가 제출 여부 (메모리에서 확인)
                com.ees.eval.domain.EvaluatorMapping selfMapping = selfMappingByEvaluateeMap.get(task.evaluateeId());
                boolean selfSubmittedForTask = false;
                if (selfMapping != null) {
                    java.util.List<Evaluation> selfEvalsForTask = evalGroupMap.getOrDefault(selfMapping.getMappingId(), java.util.Collections.emptyList());
                    selfSubmittedForTask = selfEvalsForTask.stream()
                            .anyMatch(e -> "SUBMITTED".equals(e.getConfirmStatusCode()));
                }
                evaluateeSelfSubmittedMap.put(task.mappingId(), selfSubmittedForTask);

                // 가중치 유효성 (부서별 캐싱)
                Long cacheKey = evaluateeDeptId != null ? evaluateeDeptId : -1L;
                final Long finalEvaluateeDeptId = evaluateeDeptId;
                boolean weightValid = weightValidCacheByDeptId.computeIfAbsent(cacheKey,
                        k -> typeWeightService.isWeightSumValid(finalPeriodId, finalEvaluateeDeptId, "STAFF"));
                teamWeightValidMap.put(task.mappingId(), weightValid);
            }
            model.addAttribute("teamPerfSubmittedMap", teamPerfSubmittedMap);
            model.addAttribute("teamCompSubmittedMap", teamCompSubmittedMap);
            model.addAttribute("evaluateeSelfSubmittedMap", evaluateeSelfSubmittedMap);
            model.addAttribute("teamWeightValidMap", teamWeightValidMap);

            // ========== 잠금 체크 일괄화 (사전 조회 데이터 활용 — 추가 DB 호출 없음) ==========
            java.util.List<Long> teamMappingIdList = teamTasks.stream()
                    .map(EvaluatorMappingDTO::mappingId)
                    .collect(java.util.stream.Collectors.toList());
            java.util.Map<Long, Boolean> teamLockMap = mappingService.checkEvaluationLockBulk(
                    teamMappingIdList, allMappingsByEvaluatee, evalGroupMap);
            model.addAttribute("teamLockMap", teamLockMap);

            // 자가평가 가중치 유효성
            final Long finalMyDeptId = myDeptId;
            boolean selfWeightValid = weightValidCacheByDeptId.computeIfAbsent(
                    myDeptId != null ? myDeptId : -1L,
                    k -> typeWeightService.isWeightSumValid(finalPeriodId, finalMyDeptId, "STAFF"));
            model.addAttribute("selfWeightValid", selfWeightValid);

            // 평가 시작 전(PLANNED) 알림 처리
            if ("PLANNED".equals(selectedPeriod.statusCode())) {
                model.addAttribute("infoMessage", "현재 평가 시작 전입니다. 정해진 평가 기간에만 작성이 가능합니다.");
            }
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

        // 평가 시작 전(PLANNED) 접근 차단
        EvaluationPeriodDTO period = periodService.getPeriodById(mapping.periodId());
        if ("PLANNED".equals(period.statusCode())) {
            redirectAttributes.addFlashAttribute("errorMessage", "평가 시작 전입니다. 평가 기간에 다시 접속해 주세요.");
            return "redirect:/eval/performance?periodId=" + mapping.periodId() + "&evalType=" + evalType;
        }

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
        List<EvaluationElementDTO> allElements = elementService.getElementsWithFallback(mapping.periodId(), evaluateeDeptId);
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
                        (a, b) -> a // 중복 키 충돌 방지
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

        // 역순 진행 방지 (상위 평가자가 제출했는지 확인)
        java.util.Map<String, Object> lockInfo = mappingService.checkEvaluationLock(mappingId);
        model.addAttribute("isLocked", lockInfo.get("isLocked"));
        model.addAttribute("lockedBy", lockInfo.get("lockedBy"));

        // MANAGER/EXECUTIVE 평가인 경우: 피평가자의 자가평가 내용을 참고용으로 조회
        if ("MANAGER".equals(mapping.relationTypeCode()) || "EXECUTIVE".equals(mapping.relationTypeCode())) {
            java.util.Map<Long, com.ees.eval.domain.Evaluation> selfEvalMap = evaluatorMappingMapper
                    .findByEvaluateeId(mapping.periodId(), mapping.evaluateeId())
                    .stream()
                    .filter(m -> "SELF".equals(m.getRelationTypeCode()) && "n".equals(m.getIsDeleted()))
                    .findFirst()
                    .map(selfMapping -> evaluationMapper.findByMappingId(selfMapping.getMappingId())
                            .stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    com.ees.eval.domain.Evaluation::getElementId,
                                    e -> e,
                                    (a, b) -> a)))
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

        // 역순 진행 방지 검증
        java.util.Map<String, Object> lockInfo = mappingService.checkEvaluationLock(mappingId);
        if ((Boolean) lockInfo.get("isLocked")) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                lockInfo.get("lockedBy") + "가 평가를 완료하여 더 이상 수정할 수 없습니다.");
            return "redirect:/eval/performance/form?mappingId=" + mappingId;
        }

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

        // 평가 데이터 Upsert 처리
        try {
            evaluationService.upsertEvaluations(mappingId, params, empId);
        } catch (NumberFormatException e) {
            log.warn("[평가제출] 점수 파싱 실패: mappingId={}", mappingId);
            String currentEvalType = params.getOrDefault("evalType", "PERFORMANCE");
            redirectAttributes.addFlashAttribute("errorMessage", "잘못된 점수 형식입니다.");
            return "redirect:/eval/performance/form?mappingId=" + mappingId + "&evalType=" + currentEvalType;
        }

        // 제출 후 목록 페이지로 이동
        redirectAttributes.addFlashAttribute("successMessage", "평가가 성공적으로 제출되었습니다.");
        return "redirect:/eval/performance?periodId=" + submitMapping.periodId();
    }
}
