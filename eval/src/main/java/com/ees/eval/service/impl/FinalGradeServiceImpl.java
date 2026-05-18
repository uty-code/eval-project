package com.ees.eval.service.impl;

import com.ees.eval.domain.Employee;
import com.ees.eval.domain.Evaluation;
import com.ees.eval.domain.EvaluatorMapping;
import com.ees.eval.domain.FinalGrade;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.FinalGradeTaskDTO;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.mapper.FinalGradeMapper;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationTypeWeightService;
import com.ees.eval.service.FinalGradeService;
import com.ees.eval.dto.enums.RelationType;
import com.ees.eval.service.ScoreCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FinalGradeService의 구현체입니다.
 * 벌크 조회 및 메모리 매핑을 통해 성능을 최적화합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinalGradeServiceImpl implements FinalGradeService {

    private final EvaluatorMappingMapper mappingMapper;
    private final EvaluationMapper evaluationMapper;
    private final EmployeeMapper employeeMapper;
    private final com.ees.eval.mapper.DepartmentMapper departmentMapper;
    private final EvaluationElementService elementService;
    private final EvaluationTypeWeightService typeWeightService;
    private final FinalGradeMapper finalGradeMapper;
    private final ScoreCalculationService scoreCalculationService;
    private final com.ees.eval.service.EvaluationGradeRatioService gradeRatioService;

    @Override
    @Transactional(readOnly = true)
    public List<FinalGradeTaskDTO> getFinalGradeTasks(Long executiveEmpId, com.ees.eval.dto.FinalGradeSearchCondition condition) {
        Long periodId = condition.periodId();
        // 1. 임원의 평가 대상 목록(EXECUTIVE 매핑) 조회 (DB 필터링 적용)
        List<EvaluatorMapping> teamTasks = mappingMapper.findByEvaluatorId(periodId, executiveEmpId, RelationType.EXECUTIVE.getCode());
        if (teamTasks.isEmpty()) {
            return Collections.emptyList();
        }
        // 공통 계산 파이프라인 메서드를 호출하여 점수 및 예상 등급을 계산
        return buildFinalGradeTaskDTOs(periodId, teamTasks, condition);
    }

    /**
     * 공통 계산 및 DTO 빌드 파이프라인 메서드입니다.
     * 일반 임원 조회 및 관리자 전체 조회 경로에서 동일한 계산 로직을 공유하도록 합니다.
     * 모든 주석은 한국어로 작성하며 비즈니스 로직 단계를 상세히 명시합니다.
     *
     * @param periodId 평가 차수 ID
     * @param targetTasks 계산 대상 매핑 목록
     * @param condition 검색/필터링 조건
     * @return 계산 및 필터링이 완료된 FinalGradeTaskDTO 목록
     */
    private List<FinalGradeTaskDTO> buildFinalGradeTaskDTOs(
            Long periodId,
            List<EvaluatorMapping> targetTasks,
            com.ees.eval.dto.FinalGradeSearchCondition condition) {

        // 1. 피평가자 ID 목록 추출 및 부서 전체 인원(모수) 확장
        List<Long> targetEvaluateeIds = targetTasks.stream()
                .map(EvaluatorMapping::getEvaluateeId)
                .distinct()
                .collect(Collectors.toList());
        
        List<Employee> targetEmployees = employeeMapper.findByIds(targetEvaluateeIds);
        List<Long> targetDeptIds = targetEmployees.stream()
                .map(Employee::getDeptId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 해당 부서에 속한 전체 인원을 모수로 산정하기 위해 ID 수집
        List<Long> evaluateeIds = new ArrayList<>();
        for (Long deptId : targetDeptIds) {
            evaluateeIds.addAll(employeeMapper.findByDeptId(deptId).stream()
                    .map(Employee::getEmpId)
                    .collect(Collectors.toList()));
        }
        // 원래 타겟도 확실히 포함
        evaluateeIds.addAll(targetEvaluateeIds);
        evaluateeIds = evaluateeIds.stream().distinct().collect(Collectors.toList());

        Map<Long, Employee> employeeMap = employeeMapper.findByIds(evaluateeIds).stream()
                .collect(Collectors.toMap(Employee::getEmpId, e -> e));

        // 2. 전체 모수의 모든 매핑 정보 벌크 조회
        List<EvaluatorMapping> allMappingsForEvaluatees = mappingMapper.findByEvaluateeIds(periodId, evaluateeIds);

        Map<Long, Long> selfMappingIdMap = allMappingsForEvaluatees.stream()
                .filter(m -> RelationType.SELF.getCode().equals(m.getRelationTypeCode()))
                .collect(Collectors.toMap(EvaluatorMapping::getEvaluateeId, EvaluatorMapping::getMappingId, (a, b) -> a));

        Map<Long, Long> managerMappingIdMap = allMappingsForEvaluatees.stream()
                .filter(m -> "MANAGER".equals(m.getRelationTypeCode()))
                .collect(Collectors.toMap(EvaluatorMapping::getEvaluateeId, EvaluatorMapping::getMappingId, (a, b) -> a));

        Map<Long, Long> executiveMappingIdMap = allMappingsForEvaluatees.stream()
                .filter(m -> "EXECUTIVE".equals(m.getRelationTypeCode()))
                .collect(Collectors.toMap(EvaluatorMapping::getEvaluateeId, EvaluatorMapping::getMappingId, (a, b) -> a));

        Map<Long, List<Long>> subordinateMappingIdsMap = allMappingsForEvaluatees.stream()
                .filter(m -> "SUBORDINATE".equals(m.getRelationTypeCode()))
                .collect(Collectors.groupingBy(EvaluatorMapping::getEvaluateeId,
                        Collectors.mapping(EvaluatorMapping::getMappingId, Collectors.toList())));

        // 3. 매핑 ID별 평가 결과 벌크 조회
        List<Long> allRelatedMappingIds = allMappingsForEvaluatees.stream()
                .map(EvaluatorMapping::getMappingId)
                .collect(Collectors.toList());

        List<Evaluation> allEvals = evaluationMapper.findByMappingIds(allRelatedMappingIds);
        Map<Long, List<Evaluation>> evalGroupMap = allEvals.stream()
                .collect(Collectors.groupingBy(Evaluation::getMappingId));

        // 4. FinalGrade 벌크 조회
        List<FinalGrade> allGrades = finalGradeMapper.findByPeriodId(periodId);
        // 전체 차수 조회 시 empId만으로는 부족하므로 periodId + empId 복합키 사용
        Map<String, FinalGrade> gradeMap = allGrades.stream()
                .collect(Collectors.toMap(g -> g.getPeriodId() + "_" + g.getEmpId(), g -> g, (a, b) -> a));

        // 5. 캐싱 및 차수별 데이터 준비
        Map<Long, Boolean> weightValidCache = new HashMap<>();
        Map<String, List<com.ees.eval.dto.EvaluationTypeWeightDTO>> typeWeightsCache = new HashMap<>();
        Map<String, List<EvaluationElementDTO>> elementCache = new HashMap<>();
        Map<Long, List<EvaluationElementDTO>> globalElementsCache = new HashMap<>();
        
        List<com.ees.eval.domain.Department> allDepts = departmentMapper.findAll();
        Set<Long> leaderEmpIds = allDepts.stream()
                .map(com.ees.eval.domain.Department::getLeaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, com.ees.eval.domain.Department> deptMap = allDepts.stream()
                .collect(Collectors.toMap(com.ees.eval.domain.Department::getDeptId, d -> d, (a, b) -> a));

        // 6. 모수 전원의 종합 점수 추산 및 차수/부서별 예상 등급 부여
        Map<Long, Integer> totalScoreMap = new HashMap<>();
        Map<Long, String> expectedGradeMap = new HashMap<>();

        // 6-1. 점수 계산 (각 매핑의 실제 periodId 사용)
        for (EvaluatorMapping mapping : allMappingsForEvaluatees) {
            Long currentPeriodId = mapping.getPeriodId();
            Long empId = mapping.getEvaluateeId();
            Employee evaluatee = employeeMap.get(empId);
            if (evaluatee == null) continue;
            
            Long deptId = evaluatee.getDeptId();
            boolean isLeader = leaderEmpIds.contains(empId);
            String targetRole = isLeader ? "LEADER" : "STAFF";

            // 캐시 키: periodId + deptId + role
            String cacheKey = currentPeriodId + "_" + (deptId != null ? deptId : "null") + "_" + targetRole;
            
            List<com.ees.eval.dto.EvaluationTypeWeightDTO> typeWeights = typeWeightsCache.computeIfAbsent(
                    cacheKey, k -> typeWeightService.getTypeWeights(currentPeriodId, deptId, targetRole));

            List<EvaluationElementDTO> allElements = elementCache.computeIfAbsent(currentPeriodId + "_" + (deptId != null ? deptId : "null"),
                    k -> {
                        List<EvaluationElementDTO> global = globalElementsCache.computeIfAbsent(currentPeriodId, 
                                pid -> elementService.getElementsByPeriodId(pid, null));
                        if (deptId == null) return global;
                        List<EvaluationElementDTO> deptElements = elementService.getElementsByPeriodId(currentPeriodId, deptId);
                        return deptElements.isEmpty() ? global : deptElements;
                    });

            Long selfId = selfMappingIdMap.get(empId);
            Long mgrId = managerMappingIdMap.get(empId);
            Long execId = executiveMappingIdMap.get(empId);
            
            // 현재 매핑에 해당하는 평가만 필터링 (벌크 조회 결과에서)
            if (totalScoreMap.containsKey(empId) && periodId != null) continue;

            List<Evaluation> selfEvals = selfId != null ? evalGroupMap.getOrDefault(selfId, Collections.emptyList()) : Collections.emptyList();
            List<Evaluation> mgrEvals = mgrId != null ? evalGroupMap.getOrDefault(mgrId, Collections.emptyList()) : Collections.emptyList();
            List<Evaluation> execEvals = execId != null ? evalGroupMap.getOrDefault(execId, Collections.emptyList()) : Collections.emptyList();

            BigDecimal selfPerf = null, mgrPerf = null, execPerf = null;
            BigDecimal selfComp = null, mgrComp = null, execComp = null;
            BigDecimal selfMulti = null, mgrMulti = null, execMulti = null;

            if (isLeader) {
                selfMulti = calcScore(selfEvals, allElements, "MULTI_DIMENSIONAL");
                List<Long> subIds = subordinateMappingIdsMap.getOrDefault(empId, Collections.emptyList());
                mgrMulti = calcSubordinateAvgScore(subIds, evalGroupMap, allElements, "MULTI_DIMENSIONAL");
                execMulti = calcScore(execEvals, allElements, "MULTI_DIMENSIONAL");
            } else {
                selfPerf = calcScore(selfEvals, allElements, "PERFORMANCE");
                mgrPerf = calcScore(mgrEvals, allElements, "PERFORMANCE");
                execPerf = calcScore(execEvals, allElements, "PERFORMANCE");
                selfComp = calcScore(selfEvals, allElements, "COMPETENCY");
                mgrComp = calcScore(mgrEvals, allElements, "COMPETENCY");
                execComp = calcScore(execEvals, allElements, "COMPETENCY");
            }

            FinalGrade fg = gradeMap.get(currentPeriodId + "_" + empId);
            if (fg != null && fg.getTotalScore() != null) {
                totalScoreMap.put(empId, fg.getTotalScore());
                if (fg.getFinalGradeCode() != null && !"-".equals(fg.getFinalGradeCode())) {
                    expectedGradeMap.put(empId, fg.getFinalGradeCode());
                }
            } else {
                BigDecimal estimatedScore = estimateTotalScore(isLeader, typeWeights, allElements,
                        selfPerf, mgrPerf, execPerf, selfComp, mgrComp, execComp, selfMulti, mgrMulti, execMulti);
                if (estimatedScore != null) {
                    totalScoreMap.put(empId, estimatedScore.setScale(0, RoundingMode.HALF_UP).intValue());
                }
            }
        }

        // 6-2. 차수별 + 부서별(일반) 또는 차수별 + 본부별(부서장)로 그룹화하여 동적 상대평가 적용
        Map<String, List<Long>> empsByGroup = new HashMap<>();
        for (EvaluatorMapping m : allMappingsForEvaluatees) {
            Employee e = employeeMap.get(m.getEvaluateeId());
            if (e == null || e.getDeptId() == null) {
                continue;
            }
            
            boolean isLeader = leaderEmpIds.contains(m.getEvaluateeId());
            String key;
            if (isLeader) {
                com.ees.eval.domain.Department dept = deptMap.get(e.getDeptId());
                Long parentDeptId = (dept != null) ? dept.getParentDeptId() : null;
                if (parentDeptId != null) {
                    key = m.getPeriodId() + "_leader_" + parentDeptId;
                } else {
                    key = m.getPeriodId() + "_leader_global";
                }
            } else {
                key = m.getPeriodId() + "_staff_" + e.getDeptId();
            }
            empsByGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(m.getEvaluateeId());
        }

        Map<Long, Map<Long, com.ees.eval.dto.EvaluationGradeRatioDTO>> periodRatioMapCache = new HashMap<>();

        for (Map.Entry<String, List<Long>> groupEntry : empsByGroup.entrySet()) {
            String groupKey = groupEntry.getKey();
            String[] parts = groupKey.split("_");
            Long currentPeriodId = Long.parseLong(parts[0]);
            String roleType = parts[1]; // "leader" or "staff"
            
            Long deptId = null;
            if ("staff".equals(roleType)) {
                deptId = Long.parseLong(parts[2]);
            } else if ("leader".equals(roleType) && !"global".equals(parts[2])) {
                deptId = Long.parseLong(parts[2]); // 본부 ID (parentDeptId)
            }
            
            List<Long> deptEmpIds = groupEntry.getValue().stream().distinct().collect(Collectors.toList());

            int totalEligible = deptEmpIds.size();
            if (totalEligible == 0) continue;

            Map<Long, com.ees.eval.dto.EvaluationGradeRatioDTO> ratioMap = periodRatioMapCache.computeIfAbsent(
                    currentPeriodId, pid -> gradeRatioService.getAllRatiosByPeriodMap(pid)
            );
            
            // 만약 리더 그룹이거나 global인 경우 ratio Query 시 deptId = null로 처리하여 전사 공통 비율 적용
            Long ratioDeptId = "staff".equals(roleType) ? deptId : null;
            com.ees.eval.dto.EvaluationGradeRatioDTO ratio = gradeRatioService.getGradeRatioFromMap(ratioMap, currentPeriodId, ratioDeptId);
            
            double[] exact = {
                    totalEligible * ratio.gradeSRatio() / 100.0,
                    totalEligible * ratio.gradeARatio() / 100.0,
                    totalEligible * ratio.gradeBRatio() / 100.0,
                    totalEligible * ratio.gradeCRatio() / 100.0,
                    totalEligible * ratio.gradeDRatio() / 100.0
            };

            int[] targets = new int[5];
            double[] remainders = new double[5];
            int assigned = 0;
            for (int i = 0; i < 5; i++) {
                targets[i] = (int) exact[i];
                remainders[i] = exact[i] - targets[i];
                assigned += targets[i];
            }

            int remaining = totalEligible - assigned;
            List<Integer> indices = new ArrayList<>(List.of(0, 1, 2, 3, 4));
            indices.sort((i1, i2) -> Double.compare(remainders[i2], remainders[i1]));
            for (int i = 0; i < remaining; i++) {
                targets[indices.get(i)]++;
            }

            deptEmpIds.sort((id1, id2) -> {
                Integer score1 = totalScoreMap.get(id1);
                Integer score2 = totalScoreMap.get(id2);
                int s1 = (score1 != null) ? score1 : -1;
                int s2 = (score2 != null) ? score2 : -1;
                return Integer.compare(s2, s1);
            });

            String[] gradeNames = {"S", "A", "B", "C", "D"};

            int[] finalizedCounts = new int[5];
            for (Long empId : deptEmpIds) {
                if (expectedGradeMap.containsKey(empId)) {
                    String existingGrade = expectedGradeMap.get(empId);
                    for (int i = 0; i < 5; i++) {
                        if (gradeNames[i].equals(existingGrade)) {
                            finalizedCounts[i]++;
                            break;
                        }
                    }
                }
            }

            int[] remainingTargets = new int[5];
            for (int i = 0; i < 5; i++) {
                remainingTargets[i] = Math.max(0, targets[i] - finalizedCounts[i]);
            }

            // [DEBUG] 그룹별 LRM 계산 현황 로그
            log.debug("[LRM-DEBUG] groupKey={}, totalEligible={}, ratio=S{}:A{}:B{}:C{}:D{}",
                    groupKey, totalEligible,
                    ratio.gradeSRatio(), ratio.gradeARatio(), ratio.gradeBRatio(), ratio.gradeCRatio(), ratio.gradeDRatio());
            log.debug("[LRM-DEBUG] targets=S{}:A{}:B{}:C{}:D{}", targets[0], targets[1], targets[2], targets[3], targets[4]);
            log.debug("[LRM-DEBUG] finalizedCounts=S{}:A{}:B{}:C{}:D{}", finalizedCounts[0], finalizedCounts[1], finalizedCounts[2], finalizedCounts[3], finalizedCounts[4]);
            log.debug("[LRM-DEBUG] remainingTargets=S{}:A{}:B{}:C{}:D{}", remainingTargets[0], remainingTargets[1], remainingTargets[2], remainingTargets[3], remainingTargets[4]);
            for (Long empId : deptEmpIds) {
                log.debug("[LRM-DEBUG]   empId={}, score={}, preAssignedGrade={}", empId, totalScoreMap.get(empId), expectedGradeMap.get(empId));
            }

            int currentGradeIndex = 0;
            for (Long empId : deptEmpIds) {
                if (!expectedGradeMap.containsKey(empId)) {
                    while (currentGradeIndex < 5 && remainingTargets[currentGradeIndex] <= 0) {
                        currentGradeIndex++;
                    }
                    if (currentGradeIndex < 5) {
                        expectedGradeMap.put(empId, gradeNames[currentGradeIndex]);
                        remainingTargets[currentGradeIndex]--;
                    } else {
                        expectedGradeMap.put(empId, "D");
                    }
                }
            }
        }

        // 7. 대상자(targetTasks)에 대한 결과 DTO 조립
        List<FinalGradeTaskDTO> dtoList = targetTasks.stream().map(task -> {
            Long currentPeriodId = task.getPeriodId();
            Employee evaluatee = employeeMap.get(task.getEvaluateeId());
            Long deptId = (evaluatee != null) ? evaluatee.getDeptId() : null;
            boolean isLeader = leaderEmpIds.contains(task.getEvaluateeId());
            String targetRole = isLeader ? "LEADER" : "STAFF";

            String cacheKey = currentPeriodId + "_" + (deptId != null ? deptId : "null") + "_" + targetRole;

            boolean weightValid = weightValidCache.computeIfAbsent(
                    (long) cacheKey.hashCode(),
                    id -> typeWeightService.isWeightSumValid(currentPeriodId, deptId, targetRole));

            List<com.ees.eval.dto.EvaluationTypeWeightDTO> typeWeights = typeWeightsCache.computeIfAbsent(
                    cacheKey, k -> typeWeightService.getTypeWeights(currentPeriodId, deptId, targetRole));
            List<String> requiredTypes = typeWeights.stream()
                    .map(com.ees.eval.dto.EvaluationTypeWeightDTO::elementTypeCode)
                    .collect(Collectors.toList());

            List<EvaluationElementDTO> allElements = elementCache.get(currentPeriodId + "_" + (deptId != null ? deptId : "null"));
            if (allElements == null) {
                List<EvaluationElementDTO> global = globalElementsCache.computeIfAbsent(currentPeriodId, 
                        pid -> elementService.getElementsByPeriodId(pid, null));
                if (deptId == null) {
                    allElements = global;
                } else {
                    List<EvaluationElementDTO> deptElements = elementService.getElementsByPeriodId(currentPeriodId, deptId);
                    allElements = deptElements.isEmpty() ? global : deptElements;
                }
            }

            List<EvaluationElementDTO> requiredElements = allElements.stream()
                    .filter(e -> requiredTypes.contains(e.elementTypeCode()))
                    .collect(Collectors.toList());

            Long selfId = selfMappingIdMap.get(task.getEvaluateeId());
            Long mgrId = managerMappingIdMap.get(task.getEvaluateeId());
            Long execId = executiveMappingIdMap.get(task.getEvaluateeId());

            List<Evaluation> selfEvals = selfId != null ? evalGroupMap.getOrDefault(selfId, Collections.emptyList()) : Collections.emptyList();
            List<Evaluation> mgrEvals = mgrId != null ? evalGroupMap.getOrDefault(mgrId, Collections.emptyList()) : Collections.emptyList();
            List<Evaluation> execEvals = execId != null ? evalGroupMap.getOrDefault(execId, Collections.emptyList()) : Collections.emptyList();

            boolean allSubmitted = isAllSubmitted(requiredElements, execEvals);
            boolean selfSubmitted = selfId != null && isAllSubmitted(requiredElements, selfEvals);

            BigDecimal selfPerfScore = null, managerPerfScore = null, executivePerfScore = null;
            BigDecimal selfCompScore = null, managerCompScore = null, executiveCompScore = null;
            BigDecimal selfMultiScore = null, managerMultiScore = null, executiveMultiScore = null;

            if (isLeader) {
                selfMultiScore = calcScore(selfEvals, allElements, "MULTI_DIMENSIONAL");
                List<Long> subIds = subordinateMappingIdsMap.getOrDefault(task.getEvaluateeId(), Collections.emptyList());
                managerMultiScore = calcSubordinateAvgScore(subIds, evalGroupMap, allElements, "MULTI_DIMENSIONAL");
                executiveMultiScore = calcScore(execEvals, allElements, "MULTI_DIMENSIONAL");
            } else {
                selfPerfScore = calcScore(selfEvals, allElements, "PERFORMANCE");
                managerPerfScore = calcScore(mgrEvals, allElements, "PERFORMANCE");
                executivePerfScore = calcScore(execEvals, allElements, "PERFORMANCE");
                selfCompScore = calcScore(selfEvals, allElements, "COMPETENCY");
                managerCompScore = calcScore(mgrEvals, allElements, "COMPETENCY");
                executiveCompScore = calcScore(execEvals, allElements, "COMPETENCY");
            }

            Integer totalScore = totalScoreMap.get(task.getEvaluateeId());
            String expectedGrade = expectedGradeMap.getOrDefault(task.getEvaluateeId(), "-");
            if (totalScore == null) {
                expectedGrade = "-";
            }

            // 부서장인 경우: 부서원(SUBORDINATE) 다면평가 전원 제출 여부 계산 (벌크 데이터 활용, 추가 DB 호출 없음)
            int subordinateTotal = 0;
            int subordinateSubmittedCount = 0;
            boolean subordinateAllSubmitted = true;

            if (isLeader) {
                List<Long> subIds = subordinateMappingIdsMap.getOrDefault(task.getEvaluateeId(), Collections.emptyList());
                subordinateTotal = subIds.size();

                List<EvaluationElementDTO> multiElements = requiredElements.stream()
                        .filter(e -> "MULTI_DIMENSIONAL".equals(e.elementTypeCode()))
                        .collect(Collectors.toList());

                for (Long subMappingId : subIds) {
                    List<Evaluation> subEvals = evalGroupMap.getOrDefault(subMappingId, Collections.emptyList());
                    boolean thisSubSubmitted = !multiElements.isEmpty() && multiElements.stream()
                            .allMatch(elem -> subEvals.stream()
                                    .anyMatch(e -> elem.elementId().equals(e.getElementId())
                                            && "SUBMITTED".equals(e.getConfirmStatusCode())));
                    if (thisSubSubmitted) {
                        subordinateSubmittedCount++;
                    }
                }
                subordinateAllSubmitted = subordinateTotal > 0 && subordinateSubmittedCount == subordinateTotal;
            }

            return FinalGradeTaskDTO.builder()
                    .mappingId(task.getMappingId())
                    .periodId(task.getPeriodId())
                    .periodName(task.getPeriodName())
                    .periodYear(task.getPeriodYear())
                    .evaluateeId(task.getEvaluateeId())
                    .evaluateeName(task.getEvaluateeName())
                    .deptName(evaluatee != null ? evaluatee.getDeptName() : task.getDeptName())
                    .titleName(evaluatee != null ? evaluatee.getPositionName() : null)
                    .empId(task.getEvaluateeId())
                    .selfPerfScore(selfPerfScore)
                    .managerPerfScore(managerPerfScore)
                    .executivePerfScore(executivePerfScore)
                    .selfCompScore(selfCompScore)
                    .managerCompScore(managerCompScore)
                    .executiveCompScore(executiveCompScore)
                    .selfMultiScore(selfMultiScore)
                    .managerMultiScore(managerMultiScore)
                    .executiveMultiScore(executiveMultiScore)
                    .expectedGrade(expectedGrade)
                    .totalScore(totalScore)
                    .allSubmitted(allSubmitted)
                    .selfSubmitted(selfSubmitted)
                    .weightValid(weightValid)
                    .isLeader(isLeader)
                    .deptId(deptId)
                    .subordinateAllSubmitted(isLeader ? subordinateAllSubmitted : true)
                    .subordinateTotal(subordinateTotal)
                    .subordinateSubmittedCount(subordinateSubmittedCount)
                    .build();
        }).collect(Collectors.toList());

        // 8. 실무형 후처리 필터링 적용 (상대평가 정합성 유지를 위해 조립 후 필터링)
        String normalizedSearch = condition.getNormalizedSearch();
        Long filterDeptId = condition.deptId();

        return dtoList.stream()
                .filter(task -> filterDeptId == null || filterDeptId.equals(task.deptId()))
                .filter(task -> {
                    if (normalizedSearch == null) return true;
                    String empName = task.evaluateeName() != null ? task.evaluateeName().toLowerCase() : "";
                    String empIdStr = task.empId() != null ? task.empId().toString() : "";
                    return empName.contains(normalizedSearch) || empIdStr.contains(normalizedSearch);
                })
                .filter(task -> {
                    if (!org.springframework.util.StringUtils.hasText(condition.status())) return true;
                    if ("DONE".equals(condition.status())) return task.allSubmitted();
                    if ("WAIT".equals(condition.status())) return !task.allSubmitted();
                    return true;
                })
                .sorted(Comparator.comparing(FinalGradeTaskDTO::deptName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(FinalGradeTaskDTO::evaluateeName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    private boolean isAllSubmitted(List<EvaluationElementDTO> elements, List<Evaluation> evaluations) {
        if (elements.isEmpty()) return false;
        Set<Long> submittedElementIds = evaluations.stream()
                .filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))
                .map(Evaluation::getElementId)
                .collect(Collectors.toSet());
        return elements.stream().allMatch(e -> submittedElementIds.contains(e.elementId()));
    }

    /**
     * 특정 유형(PERFORMANCE, COMPETENCY, MULTI_DIMENSIONAL)의 가중 평균 점수를 계산합니다.
     */
    private BigDecimal calcScore(List<Evaluation> evals, List<EvaluationElementDTO> elements, String typeCode) {
        if (evals == null || evals.isEmpty()) return null;
        List<Evaluation> submittedEvals = evals.stream()
                .filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode())).toList();
        if (submittedEvals.isEmpty()) return null;

        Map<Long, Evaluation> evalByElement = submittedEvals.stream()
                .collect(Collectors.toMap(Evaluation::getElementId, e -> e, (a, b) -> a));

        List<EvaluationElementDTO> typeElements = elements.stream()
                .filter(e -> typeCode.equals(e.elementTypeCode()))
                .toList();
        if (typeElements.isEmpty()) return null;

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (EvaluationElementDTO elem : typeElements) {
            Evaluation eval = evalByElement.get(elem.elementId());
            if (eval == null || eval.getScore() == null) continue;
            BigDecimal maxScore = elem.maxScore();
            if (maxScore.compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal normalized = BigDecimal.valueOf(eval.getScore())
                    .divide(maxScore, 10, RoundingMode.HALF_UP)
                    .multiply(elem.weight());
            weightedSum = weightedSum.add(normalized);
            totalWeight = totalWeight.add(elem.weight());
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) return null;

        return weightedSum.divide(totalWeight, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 다수 SUBORDINATE 매핑의 특정 유형 점수 평균을 계산합니다.
     * 부서장 다면평가에서 부서원(1차평가) 평균 점수 산출용.
     */
    private BigDecimal calcSubordinateAvgScore(List<Long> subMappingIds, 
                                                Map<Long, List<Evaluation>> evalGroupMap,
                                                List<EvaluationElementDTO> allElements, 
                                                String typeCode) {
        if (subMappingIds.isEmpty()) return null;

        List<BigDecimal> scores = new ArrayList<>();
        for (Long mappingId : subMappingIds) {
            List<Evaluation> evals = evalGroupMap.getOrDefault(mappingId, Collections.emptyList());
            BigDecimal score = calcScore(evals, allElements, typeCode);
            if (score != null) {
                scores.add(score);
            }
        }

        if (scores.isEmpty()) return null;

        BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * 이미 계산된 1차/2차 점수를 유형별 가중치에 맞게 합산하여 종합 점수를 추정합니다.
     * 우선순위: 2차(EXECUTIVE) → 1차(MANAGER/SUBORDINATE) 순으로 가용한 점수 사용. 자가평가 제외.
     */
    private BigDecimal estimateTotalScore(boolean isLeader,
                                           List<com.ees.eval.dto.EvaluationTypeWeightDTO> typeWeights,
                                           List<EvaluationElementDTO> allElements,
                                           BigDecimal selfPerf, BigDecimal mgrPerf, BigDecimal execPerf,
                                           BigDecimal selfComp, BigDecimal mgrComp, BigDecimal execComp,
                                           BigDecimal selfMulti, BigDecimal mgrMulti, BigDecimal execMulti) {
        if (typeWeights.isEmpty()) return null;

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        boolean hasAny = false;

        for (com.ees.eval.dto.EvaluationTypeWeightDTO tw : typeWeights) {
            String typeCode = tw.elementTypeCode();
            BigDecimal typeWeight = tw.weight();

            // 해당 유형의 대표 점수: 2차 → 1차 (자가평가는 제외)
            BigDecimal bestScore = null;
            switch (typeCode) {
                case "PERFORMANCE":
                    bestScore = pickBest(execPerf, mgrPerf);
                    break;
                case "COMPETENCY":
                    bestScore = pickBest(execComp, mgrComp);
                    break;
                case "MULTI_DIMENSIONAL":
                    bestScore = pickBest(execMulti, mgrMulti);
                    break;
            }

            if (bestScore != null) {
                // bestScore는 0~100 범위의 환산점수, typeWeight도 비율(예: 60)
                totalScore = totalScore.add(bestScore.multiply(typeWeight));
                totalWeight = totalWeight.add(typeWeight);
                hasAny = true;
            }
        }

        if (!hasAny || totalWeight.compareTo(BigDecimal.ZERO) == 0) return null;

        return totalScore.divide(totalWeight, 2, RoundingMode.HALF_UP);
    }

    /** 2차 → 1차 순으로 null이 아닌 첫 번째 값 반환 (자가평가 무시) */
    private BigDecimal pickBest(BigDecimal exec, BigDecimal mgr) {
        if (exec != null) return exec;
        return mgr;
    }



    /**
     * 어드민용: 전체 최종 등급 대상자 목록 조회
     * 임원 필터 없이 모든 EXECUTIVE 매핑의 피평가자를 대상으로 합니다.
     * 기존 getFinalGradeTasks 로직과 동일한 점수/등급 계산을 수행합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<FinalGradeTaskDTO> getAdminFinalGradeTasks(com.ees.eval.dto.FinalGradeSearchCondition condition) {
        Long periodId = condition.periodId();
        // 1. 전체 EXECUTIVE 매핑 조회 (evaluator_id 필터 없음)
        List<EvaluatorMapping> allExecTasks = mappingMapper.findAllByPeriodIdAndRelationType(periodId, RelationType.EXECUTIVE.getCode());
        if (allExecTasks.isEmpty()) {
            return Collections.emptyList();
        }
        // 공통 계산 파이프라인 메서드를 호출하여 전체 직원의 최종 등급 현황을 점수 계산 포함하여 계산
        return buildFinalGradeTaskDTOs(periodId, allExecTasks, condition);
    }
}
