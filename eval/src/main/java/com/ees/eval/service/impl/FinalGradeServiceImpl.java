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
    public List<FinalGradeTaskDTO> getFinalGradeTasks(Long periodId, Long executiveEmpId) {
        // 1. 임원의 평가 대상 목록(EXECUTIVE 매핑) 조회
        List<EvaluatorMapping> teamTasks = mappingMapper.findByEvaluatorId(periodId, executiveEmpId);
        if (teamTasks.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 피평가자 ID 목록 추출 및 부서 전체 인원(모수) 확장
        List<Long> targetEvaluateeIds = teamTasks.stream()
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

        // 3. 전체 모수의 모든 매핑 정보 벌크 조회
        List<EvaluatorMapping> allMappingsForEvaluatees = mappingMapper.findByEvaluateeIds(periodId, evaluateeIds);

        Map<Long, Long> selfMappingIdMap = allMappingsForEvaluatees.stream()
                .filter(m -> "SELF".equals(m.getRelationTypeCode()))
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

        // 4. 매핑 ID별 평가 결과 벌크 조회
        List<Long> allRelatedMappingIds = allMappingsForEvaluatees.stream()
                .map(EvaluatorMapping::getMappingId)
                .collect(Collectors.toList());

        List<Evaluation> allEvals = evaluationMapper.findByMappingIds(allRelatedMappingIds);
        Map<Long, List<Evaluation>> evalGroupMap = allEvals.stream()
                .collect(Collectors.groupingBy(Evaluation::getMappingId));

        // 5. FinalGrade 벌크 조회
        List<FinalGrade> allGrades = finalGradeMapper.findByPeriodId(periodId);
        Map<Long, FinalGrade> gradeMap = allGrades.stream()
                .collect(Collectors.toMap(FinalGrade::getEmpId, g -> g, (a, b) -> a));

        // 6. 캐싱
        Map<Long, Boolean> weightValidCache = new HashMap<>();
        List<EvaluationElementDTO> globalElements = elementService.getElementsByPeriodId(periodId, null);
        Set<Long> leaderEmpIds = departmentMapper.findAll().stream()
                .map(com.ees.eval.domain.Department::getLeaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, List<com.ees.eval.dto.EvaluationTypeWeightDTO>> typeWeightsCache = new HashMap<>();
        Map<Long, List<EvaluationElementDTO>> elementCacheByDeptId = new HashMap<>();

        // 7. 모수 전원의 종합 점수 추산 및 부서별(deptId) 예상 등급 부여
        Map<Long, Integer> totalScoreMap = new HashMap<>();
        Map<Long, String> expectedGradeMap = new HashMap<>();

        // 7-1. 점수 계산
        for (Long empId : evaluateeIds) {
            Employee evaluatee = employeeMap.get(empId);
            Long deptId = (evaluatee != null) ? evaluatee.getDeptId() : null;
            boolean isLeader = leaderEmpIds.contains(empId);
            String targetRole = isLeader ? "LEADER" : "STAFF";

            String twKey = (deptId != null ? deptId : "null") + "_" + targetRole;
            List<com.ees.eval.dto.EvaluationTypeWeightDTO> typeWeights = typeWeightsCache.computeIfAbsent(
                    twKey, k -> typeWeightService.getTypeWeights(periodId, deptId, targetRole));

            Long elemCacheKey = deptId != null ? deptId : -1L;
            List<EvaluationElementDTO> allElements = elementCacheByDeptId.computeIfAbsent(elemCacheKey,
                    k -> {
                        if (deptId == null) return globalElements;
                        List<EvaluationElementDTO> deptElements = elementService.getElementsByPeriodId(periodId, deptId);
                        return deptElements.isEmpty() ? globalElements : deptElements;
                    });

            Long selfId = selfMappingIdMap.get(empId);
            Long mgrId = managerMappingIdMap.get(empId);
            Long execId = executiveMappingIdMap.get(empId);

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

            // FinalGrade가 이미 확정된 경우 해당 점수를 쓰고, 아니면 추정
            FinalGrade fg = gradeMap.get(empId);
            if (fg != null && fg.getTotalScore() != null) {
                totalScoreMap.put(empId, fg.getTotalScore());
                if (fg.getFinalGradeCode() != null) {
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

        // 7-2. 부서별로 그룹화하여 동적 상대평가 적용
        Map<Long, List<Long>> empsByDept = evaluateeIds.stream()
                .filter(id -> {
                    Employee e = employeeMap.get(id);
                    return e != null && e.getDeptId() != null && totalScoreMap.get(id) != null;
                })
                .collect(Collectors.groupingBy(id -> employeeMap.get(id).getDeptId()));

        for (Map.Entry<Long, List<Long>> entry : empsByDept.entrySet()) {
            Long deptId = entry.getKey();
            List<Long> deptEmpIds = entry.getValue();

            int totalEligible = deptEmpIds.size();
            if (totalEligible == 0) continue;

            com.ees.eval.dto.EvaluationGradeRatioDTO ratio = gradeRatioService.getGradeRatio(periodId, deptId);
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

            // 점수순 내림차순 정렬
            deptEmpIds.sort((id1, id2) -> totalScoreMap.get(id2).compareTo(totalScoreMap.get(id1)));

            String[] gradeNames = {"S", "A", "B", "C", "D"};
            int currentGradeIndex = 0;
            int countInCurrentGrade = 0;

            for (Long empId : deptEmpIds) {
                if (!expectedGradeMap.containsKey(empId)) {
                    while (currentGradeIndex < 5 && countInCurrentGrade >= targets[currentGradeIndex]) {
                        currentGradeIndex++;
                        countInCurrentGrade = 0;
                    }
                    if (currentGradeIndex < 5) {
                        expectedGradeMap.put(empId, gradeNames[currentGradeIndex]);
                        countInCurrentGrade++;
                    } else {
                        expectedGradeMap.put(empId, "D"); // fallback
                    }
                } else {
                    countInCurrentGrade++;
                    while (currentGradeIndex < 5 && countInCurrentGrade > targets[currentGradeIndex]) {
                        currentGradeIndex++;
                        countInCurrentGrade = 1;
                    }
                }
            }
        }

        // 8. 대상자(teamTasks)에 대한 결과 DTO 조립
        return teamTasks.stream().map(task -> {
            Employee evaluatee = employeeMap.get(task.getEvaluateeId());
            Long deptId = (evaluatee != null) ? evaluatee.getDeptId() : null;
            boolean isLeader = leaderEmpIds.contains(task.getEvaluateeId());
            String targetRole = isLeader ? "LEADER" : "STAFF";

            boolean weightValid = weightValidCache.computeIfAbsent(
                    deptId != null ? deptId * 31 + targetRole.hashCode() : targetRole.hashCode() * 31L,
                    id -> typeWeightService.isWeightSumValid(periodId, deptId, targetRole));

            String twKey = (deptId != null ? deptId : "null") + "_" + targetRole;
            List<com.ees.eval.dto.EvaluationTypeWeightDTO> typeWeights = typeWeightsCache.computeIfAbsent(
                    twKey, k -> typeWeightService.getTypeWeights(periodId, deptId, targetRole));
            List<String> requiredTypes = typeWeights.stream()
                    .map(com.ees.eval.dto.EvaluationTypeWeightDTO::elementTypeCode)
                    .collect(Collectors.toList());

            Long elemCacheKey = deptId != null ? deptId : -1L;
            List<EvaluationElementDTO> allElements = elementCacheByDeptId.computeIfAbsent(elemCacheKey,
                    k -> {
                        if (deptId == null) return globalElements;
                        List<EvaluationElementDTO> deptElements = elementService.getElementsByPeriodId(periodId, deptId);
                        return deptElements.isEmpty() ? globalElements : deptElements;
                    });
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

            return FinalGradeTaskDTO.builder()
                    .mappingId(task.getMappingId())
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
                    .build();
        })
        .sorted(Comparator.comparing(FinalGradeTaskDTO::deptName, Comparator.nullsLast(String::compareTo)))
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
     * 종합 점수 기반으로 절대평가 등급을 추정합니다.
     * FinalGrade 레코드가 없는 경우 UI에 예상 등급을 표시하기 위한 간이 기준입니다.
     */
    private String deriveGradeFromScore(int score) {
        if (score >= 90) return "S";
        if (score >= 80) return "A";
        if (score >= 70) return "B";
        if (score >= 60) return "C";
        return "D";
    }
}
