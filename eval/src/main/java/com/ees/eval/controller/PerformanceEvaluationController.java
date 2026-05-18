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
import com.ees.eval.service.ScoreCalculationService;
import com.ees.eval.domain.FinalGrade;
import com.ees.eval.mapper.FinalGradeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final ScoreCalculationService scoreCalculationService;
    private final FinalGradeMapper finalGradeMapper;

    /**
     * 부서별 평가 요소를 캐싱하여 동일 부서에 대한 중복 DB 호출을 방지합니다.
     */
    private List<EvaluationElementDTO> getCachedElements(
            java.util.Map<String, List<EvaluationElementDTO>> cache, Long periodId, Long deptId) {
        String cacheKey = periodId + "_" + (deptId != null ? deptId : -1L);
        return cache.computeIfAbsent(cacheKey, k -> elementService.getElementsWithFallback(periodId, deptId));
    }


    @GetMapping
    public String list(Model model,
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) Long filterDeptId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String filterStatus,
            @RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal UserDetails userDetails,
            jakarta.servlet.http.HttpServletRequest request) {

        model.addAttribute("activeMenu", "performance-eval");

        // 필터 드롭다운용 부서 목록의 기본값 조기 바인딩 (어떤 로직 분기를 타더라도 안전한 렌더링 보장)
        model.addAttribute("departments", java.util.Collections.emptyList());

        Long empId = Long.parseLong(userDetails.getUsername());

        // 1. 전체 차수 목록 로드 및 정렬 정책 적용 (IN_PROGRESS 우선, 그 외 최신순)
        List<EvaluationPeriodDTO> allPeriods = periodService.getAllPeriods();
        List<EvaluationPeriodDTO> sortedPeriods = new java.util.ArrayList<>(allPeriods);
        sortedPeriods.sort((p1, p2) -> {
            boolean p1Active = "IN_PROGRESS".equals(p1.statusCode());
            boolean p2Active = "IN_PROGRESS".equals(p2.statusCode());
            if (p1Active && !p2Active) return -1;
            if (!p1Active && p2Active) return 1;
            // 둘 다 같은 상태거나 둘 다 활성이 아니면 연도/ID 내림차순
            int yearCompare = p2.periodYear().compareTo(p1.periodYear());
            if (yearCompare != 0) return yearCompare;
            return p2.periodId().compareTo(p1.periodId());
        });
        model.addAttribute("periods", sortedPeriods);

        // 2. 파라미터 존재 여부 확인 (최초 진입 vs 명시적 선택 구분)
        boolean hasPeriodParam = request.getParameterMap().containsKey("periodId");

        // 3. 최초 진입 시 리다이렉트 처리 (진행 중인 차수 우선, 없으면 최신 차수)
        if (!hasPeriodParam) {
            EvaluationPeriodDTO defaultPeriod = sortedPeriods.stream()
                    .filter(p -> "IN_PROGRESS".equals(p.statusCode()))
                    .findFirst()
                    .orElse(!sortedPeriods.isEmpty() ? sortedPeriods.get(0) : null);
            
            if (defaultPeriod != null) {
                return "redirect:/eval/performance?periodId=" + defaultPeriod.periodId();
            }
        }

        // 4. 선택된 차수 정보 결정 (null이면 전체 통합 조회)
        EvaluationPeriodDTO selectedPeriod = null;
        if (periodId != null) {
            selectedPeriod = allPeriods.stream()
                    .filter(p -> p.periodId().equals(periodId))
                    .findFirst()
                    .orElse(null);
        }
        model.addAttribute("selectedPeriod", selectedPeriod);

        // 5. 데이터 조회 (periodId가 null이면 전체 기간 조회)
        List<EvaluatorMappingDTO> myTasks = mappingService.getMyEvaluationTasks(periodId, empId);

        if (!myTasks.isEmpty()) {
            // 관련 차수 ID 목록 추출 (N+1 방지용 벌크 조회 대상)
            java.util.List<Long> relevantPeriodIds = myTasks.stream()
                    .map(EvaluatorMappingDTO::periodId)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());

            List<EvaluatorMappingDTO> selfTasks = myTasks.stream()
                    .filter(m -> "SELF".equals(m.relationTypeCode()))
                    .toList();

            List<EvaluatorMappingDTO> teamTasks = myTasks.stream()
                    .filter(m -> "MANAGER".equals(m.relationTypeCode()) || "EXECUTIVE".equals(m.relationTypeCode()))
                    .toList();

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
            java.util.List<Long> allMyMappingIds = new java.util.ArrayList<>(teamMappingIds);
            selfTasks.forEach(s -> allMyMappingIds.add(s.mappingId()));

            // (C) 피평가자 사원 정보 일괄 조회 (1회)
            final java.util.Map<Long, Employee> evaluateeMap = teamEvaluateeIds.isEmpty() ?
                    java.util.Collections.emptyMap() :
                    employeeMapper.findByIds(teamEvaluateeIds).stream()
                            .collect(java.util.stream.Collectors.toMap(Employee::getEmpId, e -> e, (a, b) -> a));

            // (D) 모든 관련 매핑의 평가 데이터 일괄 조회 (1회)
            java.util.Map<Long, java.util.List<Evaluation>> evalGroupMap = new java.util.HashMap<>();
            if (!allMyMappingIds.isEmpty()) {
                evalGroupMap = evaluationMapper.findByMappingIds(allMyMappingIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(Evaluation::getMappingId));
            }

            // (E) 피평가자들의 모든 관련 매핑 일괄 조회 (잠금 체크 및 자가평가 확인용)
            java.util.Map<Long, java.util.List<com.ees.eval.domain.EvaluatorMapping>> allMappingsByEvaluatee = new java.util.HashMap<>();
            java.util.Map<String, com.ees.eval.domain.EvaluatorMapping> selfMappingByEvaluateeAndPeriodMap = new java.util.HashMap<>();
            java.util.List<Long> additionalMappingIds = new java.util.ArrayList<>();

            if (!teamEvaluateeIds.isEmpty()) {
                // 특정 차수가 아닌 태스크에 포함된 모든 차수 대상으로 조회
                java.util.List<com.ees.eval.domain.EvaluatorMapping> allRelatedMappings =
                        evaluatorMappingMapper.findByEvaluateeIds(periodId, teamEvaluateeIds);
                
                // 피평가자별 그룹화
                allMappingsByEvaluatee = allRelatedMappings.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                com.ees.eval.domain.EvaluatorMapping::getEvaluateeId));
                
                for (com.ees.eval.domain.EvaluatorMapping m : allRelatedMappings) {
                    if ("SELF".equals(m.getRelationTypeCode()) && "n".equals(m.getIsDeleted())) {
                        selfMappingByEvaluateeAndPeriodMap.put(m.getPeriodId() + "_" + m.getEvaluateeId(), m);
                        if (!allMyMappingIds.contains(m.getMappingId())) {
                            additionalMappingIds.add(m.getMappingId());
                        }
                    }
                    if ("MANAGER".equals(m.getRelationTypeCode()) || "EXECUTIVE".equals(m.getRelationTypeCode())) {
                        if (!allMyMappingIds.contains(m.getMappingId())) {
                            additionalMappingIds.add(m.getMappingId());
                        }
                    }
                }
            }

            // SELF 및 기타 다운스트림 매핑 평가 데이터 추가 조회
            if (!additionalMappingIds.isEmpty()) {
                java.util.List<Evaluation> additionalEvals = evaluationMapper.findByMappingIds(additionalMappingIds);
                for (Evaluation e : additionalEvals) {
                    evalGroupMap.computeIfAbsent(e.getMappingId(), k -> new java.util.ArrayList<>()).add(e);
                }
            }

            // (F) 평가 요소 부서/차수별 캐싱
            java.util.Map<String, java.util.List<EvaluationElementDTO>> elementCache = new java.util.HashMap<>();

            // (G) 가중치 유효성 캐싱 (periodId_deptId)
            java.util.Map<String, Boolean> weightValidCache = new java.util.HashMap<>();

            // (H) 최종 등급 데이터 조회 (관련 모든 차수 대상 벌크 조회)
            java.util.Map<String, FinalGrade> gradeMap = finalGradeMapper.findByPeriodIds(relevantPeriodIds).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            g -> g.getPeriodId() + "_" + g.getEmpId(), 
                            g -> g, (a, b) -> a));

            // ========== 자가평가 제출 여부 확인 (내 정보) ==========
            // 현재 활성 상태인 차수의 자가평가 상태를 우선 노출 (다수일 경우 첫 번째 것 기준 혹은 필요에 따라 확장)
            boolean selfPerfSubmitted = false;
            boolean selfCompSubmitted = false;
            if (!selfTasks.isEmpty()) {
                final EvaluationPeriodDTO finalSelectedPeriod = selectedPeriod;
                // 가장 최근 혹은 진행 중인 차수 기준 하나 선택 (UX 정책에 따라 조정 가능)
                EvaluatorMappingDTO activeSelfTask = selfTasks.stream()
                        .filter(s -> finalSelectedPeriod == null || s.periodId().equals(finalSelectedPeriod.periodId()))
                        .findFirst()
                        .orElse(selfTasks.get(0));
                
                java.util.List<Evaluation> selfEvals = evalGroupMap.getOrDefault(activeSelfTask.mappingId(), java.util.Collections.emptyList());
                java.util.List<Long> submittedElementIds = selfEvals.stream()
                        .filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))
                        .map(Evaluation::getElementId)
                        .toList();
                
                if (!submittedElementIds.isEmpty()) {
                    java.util.List<EvaluationElementDTO> myElements = getCachedElements(elementCache, activeSelfTask.periodId(), myDeptId);
                    selfPerfSubmitted = myElements.stream()
                            .filter(el -> "PERFORMANCE".equals(el.elementTypeCode()))
                            .anyMatch(el -> submittedElementIds.contains(el.elementId()));
                    selfCompSubmitted = myElements.stream()
                            .filter(el -> "COMPETENCY".equals(el.elementTypeCode()))
                            .anyMatch(el -> submittedElementIds.contains(el.elementId()));
                }
            }
            model.addAttribute("selfPerfSubmitted", selfPerfSubmitted);
            model.addAttribute("selfCompSubmitted", selfCompSubmitted);

            // ========== 팀원별 상태 계산 및 DTO 매핑 ==========
            java.util.List<com.ees.eval.dto.PerformanceEvalRowDTO> rowList = new java.util.ArrayList<>();
            
            // 잠금 체크 사전 실행
            java.util.Map<Long, Boolean> teamLockMap = mappingService.checkEvaluationLockBulk(
                    teamMappingIds, allMappingsByEvaluatee, evalGroupMap);

            // 차수 ID -> 이름 매핑 맵 생성
            java.util.Map<Long, String> periodNameMap = sortedPeriods.stream()
                    .collect(java.util.stream.Collectors.toMap(EvaluationPeriodDTO::periodId, EvaluationPeriodDTO::periodName, (v1, v2) -> v1));

            for (EvaluatorMappingDTO task : teamTasks) {
                Employee evaluatee = evaluateeMap.get(task.evaluateeId());
                if (evaluatee == null) continue;
                
                Long evaluateeDeptId = evaluatee.getDeptId();
                java.util.List<EvaluationElementDTO> elementsForTask = getCachedElements(elementCache, task.periodId(), evaluateeDeptId);

                // 팀원 평가 데이터 (Manager)
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

                // 점수 계산 (Manager)
                java.math.BigDecimal managerPerfScore = calcScore(evals, elementsForTask, "PERFORMANCE");
                java.math.BigDecimal managerCompScore = calcScore(evals, elementsForTask, "COMPETENCY");

                // 피평가자의 자가평가 데이터 (Self)
                com.ees.eval.domain.EvaluatorMapping selfMapping = selfMappingByEvaluateeAndPeriodMap.get(task.periodId() + "_" + task.evaluateeId());
                boolean selfSubmittedForTask = false;
                java.math.BigDecimal selfPerfScore = null;
                java.math.BigDecimal selfCompScore = null;
                
                if (selfMapping != null) {
                    java.util.List<Evaluation> selfEvalsForTask = evalGroupMap.getOrDefault(selfMapping.getMappingId(), java.util.Collections.emptyList());
                    selfSubmittedForTask = selfEvalsForTask.stream()
                            .anyMatch(e -> "SUBMITTED".equals(e.getConfirmStatusCode()));
                    selfPerfScore = calcScore(selfEvalsForTask, elementsForTask, "PERFORMANCE");
                    selfCompScore = calcScore(selfEvalsForTask, elementsForTask, "COMPETENCY");
                }

                // 가중치 유효성
                boolean weightValid = weightValidCache.computeIfAbsent(
                        task.periodId() + "_" + (evaluateeDeptId != null ? evaluateeDeptId : -1L),
                        k -> typeWeightService.isWeightSumValid(task.periodId(), evaluateeDeptId, "STAFF"));

                // 평가 상태 통합 (완료, 진행중, 대기)
                String evalStatus;
                if (perfSubmitted && compSubmitted) evalStatus = "완료";
                else if (perfSubmitted || compSubmitted) evalStatus = "진행중";
                else evalStatus = "대기";

                // CTA 상태 처리
                boolean isLocked = teamLockMap.getOrDefault(task.mappingId(), false);
                String ctaStatus;
                if (isLocked) {
                    ctaStatus = "LOCKED";
                } else if (!weightValid) {
                    ctaStatus = "WEIGHT_ERROR";
                } else if (!selfSubmittedForTask) {
                    ctaStatus = "WAITING";
                } else {
                    ctaStatus = "PRIMARY";
                }

                String gradeKey = task.periodId() + "_" + task.evaluateeId();
                rowList.add(com.ees.eval.dto.PerformanceEvalRowDTO.builder()
                        .mappingId(task.mappingId())
                        .evaluateeId(task.evaluateeId())
                        .deptName(evaluatee.getDeptName())
                        .positionName(evaluatee.getPositionName())
                        .titleName(periodNameMap.getOrDefault(task.periodId(), "Unknown Period")) // 전체 조회 시 차수명을 성명 옆 등에 표시하기 위해 활용
                        .empId(evaluatee.getEmpId())
                        .name(evaluatee.getName())
                        .selfPerfScore(selfPerfScore)
                        .managerPerfScore(managerPerfScore)
                        .selfCompScore(selfCompScore)
                        .managerCompScore(managerCompScore)
                        .evalStatus(evalStatus)
                        .ctaStatus(ctaStatus)
                        .deptId(evaluatee.getDeptId())
                        .expectedGrade(gradeMap.containsKey(gradeKey) ? gradeMap.get(gradeKey).getFinalGradeCode() : "-")
                        .totalScore(gradeMap.containsKey(gradeKey) ? gradeMap.get(gradeKey).getTotalScore() : null)
                        .build());
            }

            // ========== 필터링 ==========
            java.util.List<com.ees.eval.dto.PerformanceEvalRowDTO> filteredList = rowList.stream()
                    .filter(r -> filterDeptId == null || filterDeptId.equals(r.deptId()))
                    .filter(r -> filterStatus == null || filterStatus.isEmpty() || filterStatus.equals(r.evalStatus()))
                    .filter(r -> keyword == null || keyword.isEmpty() || 
                                 r.name().contains(keyword) || 
                                 r.empId().toString().contains(keyword))
                    .collect(java.util.stream.Collectors.toList());

            // ========== 정렬 (기본: 부서, 직급, 이름 순) ==========
            filteredList.sort(java.util.Comparator
                    .comparing(com.ees.eval.dto.PerformanceEvalRowDTO::deptName, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                    .thenComparing(com.ees.eval.dto.PerformanceEvalRowDTO::positionName, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                    .thenComparing(com.ees.eval.dto.PerformanceEvalRowDTO::name, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));

            // ========== 페이징 ==========
            int pageSize = 10;
            int totalCount = filteredList.size();
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            if (totalPages == 0) totalPages = 1;
            int currentPage = Math.max(1, Math.min(page, totalPages));
            int startIndex = (currentPage - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalCount);

            java.util.List<com.ees.eval.dto.PerformanceEvalRowDTO> pagedList = filteredList.subList(startIndex, endIndex);

            com.ees.eval.dto.PerformanceEvalPageDTO pageData = new com.ees.eval.dto.PerformanceEvalPageDTO(
                    pagedList, currentPage, totalPages, totalCount, pageSize);
            
            model.addAttribute("pageData", pageData);
            
            // 필터 드롭다운용 부서 목록 추출
            java.util.List<com.ees.eval.dto.DepartmentDTO> filterDepts = teamTasks.stream()
                    .map(t -> evaluateeMap.get(t.evaluateeId()))
                    .filter(java.util.Objects::nonNull)
                    .map(e -> com.ees.eval.dto.DepartmentDTO.builder()
                            .deptId(e.getDeptId())
                            .deptName(e.getDeptName())
                            .build())
                    .filter(d -> d.deptId() != null)
                    .collect(java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toMap(
                                    com.ees.eval.dto.DepartmentDTO::deptId, d -> d, (e1, e2) -> e1
                            ),
                            m -> new java.util.ArrayList<>(m.values())
                    ));
            filterDepts.sort(java.util.Comparator.comparing(com.ees.eval.dto.DepartmentDTO::deptName));
            model.addAttribute("departments", filterDepts);

            // 자가평가 가중치 유효성 (현재 선택된 차수 또는 첫 번째 차수 기준)
            Long targetWeightPeriodId = (selectedPeriod != null) ? selectedPeriod.periodId() : relevantPeriodIds.get(0);
            boolean selfWeightValid = weightValidCache.computeIfAbsent(
                    targetWeightPeriodId + "_" + (myDeptId != null ? myDeptId : -1L),
                    k -> typeWeightService.isWeightSumValid(targetWeightPeriodId, myDeptId, "STAFF"));
            model.addAttribute("selfWeightValid", selfWeightValid);

            if (selectedPeriod != null && "PLANNED".equals(selectedPeriod.statusCode())) {
                // 평가 준비 중 상태일 때 안내 메시지 노출
                model.addAttribute("infoMessage", "현재 평가 시작 전입니다. 정해진 평가 기간에만 작성이 가능합니다.");
            } else if (pageData.totalCount() == 0) {
                // 상단 메시지 제거
            }
        } else {
            if (selectedPeriod != null && "PLANNED".equals(selectedPeriod.statusCode())) {
                // 평가 준비 중 상태이면서 할당된 태스크가 없을 때도 안전하게 안내 메시지 노출
                model.addAttribute("infoMessage", "현재 평가 시작 전입니다. 정해진 평가 기간에만 작성이 가능합니다.");
            }
            // 빈 페이지 데이터 설정
            model.addAttribute("pageData", new com.ees.eval.dto.PerformanceEvalPageDTO(
                    Collections.emptyList(), 1, 1, 0, 10));
        }

        // 필터 상태 유지
        model.addAttribute("periodId", periodId);
        model.addAttribute("filterDeptId", filterDeptId);
        model.addAttribute("keyword", keyword);
        model.addAttribute("filterStatus", filterStatus);

        return "eval/performance/list";
    }

    /**
     * 특정 평가 유형(PERFORMANCE, COMPETENCY 등)의 가중치 적용 점수를 산출합니다.
     */
    private java.math.BigDecimal calcScore(java.util.List<Evaluation> evals, java.util.List<EvaluationElementDTO> elements, String typeCode) {
        if (evals == null || evals.isEmpty()) return null;
        java.util.List<Evaluation> submittedEvals = evals.stream().filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode())).toList();
        if (submittedEvals.isEmpty()) return null;

        java.util.Map<Long, Evaluation> evalByElement = submittedEvals.stream()
                .collect(java.util.stream.Collectors.toMap(Evaluation::getElementId, e -> e, (a, b) -> a));

        java.util.List<EvaluationElementDTO> typeElements = elements.stream()
                .filter(e -> typeCode.equals(e.elementTypeCode()))
                .toList();
        if (typeElements.isEmpty()) return null;

        java.math.BigDecimal weightedSum = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalWeight = java.math.BigDecimal.ZERO;
        for (EvaluationElementDTO elem : typeElements) {
            Evaluation eval = evalByElement.get(elem.elementId());
            if (eval == null || eval.getScore() == null) continue;
            java.math.BigDecimal maxScore = elem.maxScore();
            if (maxScore.compareTo(java.math.BigDecimal.ZERO) == 0) continue;

            java.math.BigDecimal normalized = java.math.BigDecimal.valueOf(eval.getScore())
                    .divide(maxScore, 10, java.math.RoundingMode.HALF_UP)
                    .multiply(elem.weight());
            weightedSum = weightedSum.add(normalized);
            totalWeight = totalWeight.add(elem.weight());
        }

        if (totalWeight.compareTo(java.math.BigDecimal.ZERO) == 0) return null;

        return weightedSum.divide(totalWeight, 10, java.math.RoundingMode.HALF_UP)
                .multiply(java.math.BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    @GetMapping("/form")
    public String getForm(@RequestParam Long mappingId,
            @RequestParam(required = false) String evalType,
            Model model,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        // 매핑 정보 조회 (피평가자 정보, 차수 정보 포함)
        EvaluatorMappingDTO mapping = mappingService.getMappingById(mappingId);
        EvaluationPeriodDTO period = periodService.getPeriodById(mapping.periodId());

        // 평가 차수가 준비 중(PLANNED)인 경우 접근 차단
        if (period != null && "PLANNED".equals(period.statusCode())) {
            redirectAttributes.addFlashAttribute("errorMessage", "평가 시작 전입니다. 평가 기간에 다시 접속해 주세요.");
            return "redirect:/eval/performance?periodId=" + mapping.periodId();
        }

        // 평가 기간 활성 여부 확인
        boolean isPeriodActive = periodService.isPeriodActive(mapping.periodId());
        model.addAttribute("isPeriodActive", isPeriodActive);

        // 부서별 유형별 가중치 합계 100 검증
        Employee evaluatee = employeeMapper.findById(mapping.evaluateeId()).orElse(null);
        Long evaluateeDeptId = (evaluatee != null) ? evaluatee.getDeptId() : null;
        if (!typeWeightService.isWeightSumValid(mapping.periodId(), evaluateeDeptId, "STAFF")) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "유형별 가중치 합계가 100%가 아닙니다. 관리자에게 가중치 설정을 요청하세요.");
            return "redirect:/eval/performance?periodId=" + mapping.periodId();
        }

        model.addAttribute("mapping", mapping);
        model.addAttribute("activeMenu", "performance-eval");

        // 해당 차수의 평가요소 목록 조회 (부서 전용 → 전사 공통 폴백)
        List<EvaluationElementDTO> allElements = elementService.getElementsWithFallback(mapping.periodId(), evaluateeDeptId);
        model.addAttribute("mappingId", mappingId);

        // 기존에 제출된 평가 내용 조회 → elementId 기준 Map으로 변환
        java.util.Map<Long, com.ees.eval.domain.Evaluation> savedMap = evaluationMapper
                .findByMappingId(mappingId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.ees.eval.domain.Evaluation::getElementId,
                        e -> e,
                        (a, b) -> a
                ));
        model.addAttribute("savedMap", savedMap);

        // 역순 진행 방지 (상위 평가자가 제출했는지 확인) 및 기간 종료 여부 통합 체크
        java.util.Map<String, Object> lockInfo = mappingService.checkEvaluationLock(mappingId);
        boolean isLocked = (boolean) lockInfo.get("isLocked") || !isPeriodActive;
        model.addAttribute("isLocked", isLocked);
        model.addAttribute("lockedBy", lockInfo.get("lockedBy"));
        model.addAttribute("lockReason", !isPeriodActive ? "평가 기간 종료" : lockInfo.get("lockedBy"));

        // ========= 자가평가(SELF): 기존 form.html 유지 =========
        if ("SELF".equals(mapping.relationTypeCode())) {
            // evalType 기반으로 해당 유형 요소만 필터링
            String resolvedEvalType = (evalType != null && "COMPETENCY".equals(evalType)) ? "COMPETENCY" : "PERFORMANCE";
            model.addAttribute("evalType", resolvedEvalType);

            List<EvaluationElementDTO> elements = allElements.stream()
                    .filter(e -> resolvedEvalType.equals(e.elementTypeCode()))
                    .toList();
            model.addAttribute("elements", elements);

            // 현재 evalType 항목 중 하나라도 SUBMITTED면 제출 완료로 판단
            java.util.Set<Long> currentTypeElementIds = elements.stream()
                    .map(EvaluationElementDTO::elementId)
                    .collect(java.util.stream.Collectors.toSet());
            boolean submitted = savedMap.entrySet().stream()
                    .filter(entry -> currentTypeElementIds.contains(entry.getKey()))
                    .anyMatch(entry -> "SUBMITTED".equals(entry.getValue().getConfirmStatusCode()));
            model.addAttribute("submitted", submitted);

            return "eval/performance/form";
        }

        // ========= MANAGER/EXECUTIVE: 통합 Wizard =========
        List<EvaluationElementDTO> performanceElements = allElements.stream()
                .filter(e -> "PERFORMANCE".equals(e.elementTypeCode()))
                .toList();
        List<EvaluationElementDTO> competencyElements = allElements.stream()
                .filter(e -> "COMPETENCY".equals(e.elementTypeCode()))
                .toList();
        model.addAttribute("performanceElements", performanceElements);
        model.addAttribute("competencyElements", competencyElements);

        // 모든 성과+역량 항목이 SUBMITTED인지 확인
        java.util.Set<Long> allTargetIds = new java.util.HashSet<>();
        performanceElements.forEach(e -> allTargetIds.add(e.elementId()));
        competencyElements.forEach(e -> allTargetIds.add(e.elementId()));
        boolean submitted = !allTargetIds.isEmpty() && savedMap.entrySet().stream()
                .filter(entry -> allTargetIds.contains(entry.getKey()))
                .allMatch(entry -> "SUBMITTED".equals(entry.getValue().getConfirmStatusCode()));
        model.addAttribute("submitted", submitted);

        // 피평가자의 자가평가 내용을 참고용으로 조회
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

        // EXECUTIVE인 경우: 1차 평가자(MANAGER) 내용도 참조
        if ("EXECUTIVE".equals(mapping.relationTypeCode())) {
            java.util.Map<Long, com.ees.eval.domain.Evaluation> managerEvalMap = evaluatorMappingMapper
                    .findByEvaluateeId(mapping.periodId(), mapping.evaluateeId())
                    .stream()
                    .filter(m -> "MANAGER".equals(m.getRelationTypeCode()) && "n".equals(m.getIsDeleted()))
                    .findFirst()
                    .map(managerMapping -> evaluationMapper.findByMappingId(managerMapping.getMappingId())
                            .stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    com.ees.eval.domain.Evaluation::getElementId,
                                    e -> e,
                                    (a, b) -> a)))
                    .orElse(java.util.Collections.emptyMap());
            model.addAttribute("managerEvalMap", managerEvalMap);
        }

        return "eval/performance/wizard";
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

        // 평가 기간 유효성 검증 (제출 시점 재확인)
        EvaluatorMappingDTO submitMapping = mappingService.getMappingById(mappingId);
        if (!periodService.isPeriodActive(submitMapping.periodId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "평가 기간이 종료되어 제출할 수 없습니다.");
            return "redirect:/eval/performance?periodId=" + submitMapping.periodId();
        }

        // 역순 진행 방지 검증
        java.util.Map<String, Object> lockInfo = mappingService.checkEvaluationLock(mappingId);
        if ((Boolean) lockInfo.get("isLocked")) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                lockInfo.get("lockedBy") + "가 평가를 완료하여 더 이상 수정할 수 없습니다.");
            return "redirect:/eval/performance/form?mappingId=" + mappingId;
        }

        // 부서별 유형별 가중치 합계 100 검증
        Employee submitEvaluatee = employeeMapper.findById(submitMapping.evaluateeId()).orElse(null);
        Long submitDeptId = (submitEvaluatee != null) ? submitEvaluatee.getDeptId() : null;
        if (!typeWeightService.isWeightSumValid(submitMapping.periodId(), submitDeptId, "STAFF")) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "유형별 가중치 합계가 100%가 아니어서 평가를 제출할 수 없습니다.");
            return "redirect:/eval/performance/form?mappingId=" + mappingId;
        }

        // 평가 데이터 Upsert 처리
        try {
            evaluationService.upsertEvaluations(mappingId, params, empId);
        } catch (NumberFormatException e) {
            log.warn("[평가제출] 점수 파싱 실패: mappingId={}", mappingId);
            redirectAttributes.addFlashAttribute("errorMessage", "잘못된 점수 형식입니다.");
            return "redirect:/eval/performance/form?mappingId=" + mappingId;
        }

        // 제출 후 실시간 등급 재계산 로직 추가
        try {
            log.info("[등급재계산] 시작 - periodId={}, evaluateeId={}, deptId={}", submitMapping.periodId(), submitMapping.evaluateeId(), submitDeptId);
            // 1. 총점 계산
            Integer totalScore = scoreCalculationService.calculateTotalScore(submitMapping.periodId(), submitMapping.evaluateeId());
            log.info("[등급재계산] 총점 산출 결과: {}", totalScore);
            
            if (totalScore != null) {
                // 2. FinalGrade 업데이트
                FinalGrade fg = finalGradeMapper.findByPeriodIdAndEmpId(submitMapping.periodId(), submitMapping.evaluateeId())
                        .orElse(new FinalGrade());
                
                log.info("[등급재계산] 기존 FinalGrade 존재 여부: {}", fg.getGradeId() != null);
            
            if (fg.getPeriodId() == null) {
                fg.setPeriodId(submitMapping.periodId());
                fg.setEmpId(submitMapping.evaluateeId());
                fg.setTotalScore(totalScore);
                fg.setFinalGradeCode("-"); 
                fg.setIsDeleted("n");
                fg.setVersion(1);
                fg.setCreatedAt(java.time.LocalDateTime.now());
                fg.setCreatedBy(empId);
                fg.setUpdatedAt(java.time.LocalDateTime.now());
                fg.setUpdatedBy(empId);
                finalGradeMapper.insert(fg);
            } else {
                fg.setTotalScore(totalScore);
                fg.setUpdatedAt(java.time.LocalDateTime.now());
                fg.setUpdatedBy(empId);
                finalGradeMapper.update(fg);
            }

            // 3. 부서 전체 등급 재산출 (상대평가) - 2차 평가(EXECUTIVE) 완료 시에만 실행
            // MANAGER/SELF 제출 시에는 1차 점수를 기반으로 잘못된 등급 코드가 DB에 저장되는 것을 방지
            if (submitDeptId != null && "EXECUTIVE".equals(submitMapping.relationTypeCode())) {
                log.info("[등급재계산] 부서 상대평가 시작 - deptId={}", submitDeptId);
                scoreCalculationService.calculateRelativeGradesForDepartment(submitMapping.periodId(), submitDeptId);
                log.info("[등급재계산] 부서 상대평가 완료");
            } else if (!"EXECUTIVE".equals(submitMapping.relationTypeCode())) {
                log.info("[등급재계산] 1차 평가 제출 - 상대평가 등급 재계산 건너뜀 (relationType={})", submitMapping.relationTypeCode());
            }
          } // if (totalScore != null) 닫기
        } catch (Exception e) {
            log.error("[등급재계산] 오류 발생: mappingId={}, error={}", mappingId, e.getMessage(), e);
        }

        // 제출 후 목록 페이지로 이동
        redirectAttributes.addFlashAttribute("successMessage", "평가가 성공적으로 제출되었습니다.");
        return "redirect:/eval/performance?periodId=" + submitMapping.periodId();
    }
}
