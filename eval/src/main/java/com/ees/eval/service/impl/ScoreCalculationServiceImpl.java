package com.ees.eval.service.impl;

import com.ees.eval.domain.Employee;
import com.ees.eval.domain.Evaluation;
import com.ees.eval.domain.EvaluatorMapping;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.EvaluationTypeWeightDTO;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.domain.FinalGrade;
import com.ees.eval.dto.EvaluationGradeRatioDTO;
import com.ees.eval.mapper.FinalGradeMapper;
import com.ees.eval.service.EvaluationGradeRatioService;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationTypeWeightService;
import com.ees.eval.service.ScoreCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 평가 점수 산출 및 등급 매핑을 담당하는 서비스 구현체입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreCalculationServiceImpl implements ScoreCalculationService {

    private final EvaluatorMappingMapper mappingMapper;
    private final EvaluationMapper evaluationMapper;
    private final EmployeeMapper employeeMapper;
    private final EvaluationElementService elementService;
    private final EvaluationTypeWeightService typeWeightService;
    private final DepartmentMapper departmentMapper;
    private final FinalGradeMapper finalGradeMapper;
    private final EvaluationGradeRatioService gradeRatioService;

    @Override
    @Transactional(readOnly = true)
    public Integer calculateTotalScore(Long periodId, Long empId) {
        // 1. 해당 사원의 EXECUTIVE 매핑 우선 조회, 없으면 1ST 매핑 조회
        List<EvaluatorMapping> mappings = mappingMapper.findByEvaluateeId(periodId, empId);
        EvaluatorMapping execMapping = mappings.stream()
                .filter(m -> "EXECUTIVE".equals(m.getRelationTypeCode()))
                .findFirst()
                .orElse(null);

        // EXECUTIVE 점수가 없으면 1차 평가자(MANAGER) 점수라도 가져와서 '예상 등급'용으로 사용
        if (execMapping == null || evaluationMapper.findByMappingId(execMapping.getMappingId()).stream()
                .noneMatch(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))) {
            log.info("[ScoreCalc] EXECUTIVE 점수 없음. MANAGER 매핑 조회 시도 (empId={})", empId);
            execMapping = mappings.stream()
                    .filter(m -> "MANAGER".equals(m.getRelationTypeCode()))
                    .findFirst()
                    .orElse(null);
        }

        if (execMapping == null) {
            log.info("[ScoreCalc] EXECUTIVE 및 MANAGER 매핑 모두 없음 (empId={})", empId);
            return null;
        }
        
        log.info("[ScoreCalc] 선택된 매핑: mappingId={}, relation={}", execMapping.getMappingId(), execMapping.getRelationTypeCode());

        List<Evaluation> execEvals = evaluationMapper.findByMappingId(execMapping.getMappingId());
        List<Evaluation> submittedEvals = execEvals.stream()
                .filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))
                .collect(Collectors.toList());

        if (submittedEvals.isEmpty()) {
            log.debug("[ScoreCalc] No submitted evaluations for mappingId={}", execMapping.getMappingId());
            return null;
        }

        // 2. 사원의 역할 확인 (부서장인지 여부)
        boolean isLeader = departmentMapper.countDepartmentsByLeaderId(empId) > 0;
        String targetRole = isLeader ? "LEADER" : "STAFF";

        // 3. 부서 정보 확인
        Long deptId = employeeMapper.findById(empId)
                .map(Employee::getDeptId)
                .orElse(null);

        // 4. 유형별 가중치 조회
        List<EvaluationTypeWeightDTO> typeWeights = typeWeightService.getTypeWeights(periodId, deptId, targetRole);
        if (typeWeights.isEmpty()) {
            log.debug("[ScoreCalc] No type weights found for periodId={}, deptId={}, role={}", periodId, deptId, targetRole);
            return null;
        }

        List<EvaluationElementDTO> allElements = elementService.getElementsWithFallback(periodId, deptId);

        // 6. 제출된 평가를 elementId 기준으로 매핑
        Map<Long, Evaluation> evalByElementId = submittedEvals.stream()
                .collect(Collectors.toMap(Evaluation::getElementId, e -> e, (a, b) -> a));

        // 7. 유형별 가중 합산
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalTypeWeightSum = BigDecimal.ZERO;

        for (EvaluationTypeWeightDTO tw : typeWeights) {
            String typeCode = tw.elementTypeCode();
            BigDecimal typeWeight = tw.weight(); // 유형별 가중치 (예: 60, 40)

            // 해당 유형의 평가 요소들
            List<EvaluationElementDTO> typeElements = allElements.stream()
                    .filter(e -> typeCode.equals(e.elementTypeCode()))
                    .collect(Collectors.toList());

            if (typeElements.isEmpty()) continue;

            // 유형 내 환산점수 계산
            BigDecimal typeScore = calculateTypeScore(typeElements, evalByElementId);

            // 유형별 가중치 적용
            totalScore = totalScore.add(typeScore.multiply(typeWeight));
            totalTypeWeightSum = totalTypeWeightSum.add(typeWeight);
        }

        if (totalTypeWeightSum.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        // 최종 점수 = (합산) / 가중치합 (가중치 합이 100이면 그대로 100점 만점)
        int finalScore = totalScore.divide(totalTypeWeightSum, 0, RoundingMode.HALF_UP).intValue();

        // 0~100 범위 제한
        finalScore = Math.max(0, Math.min(100, finalScore));

        log.info("[ScoreCalc] periodId={}, empId={}, totalScore={}", 
                 periodId, empId, finalScore);
        return finalScore;
    }

    /**
     * 유형 내 환산 점수를 계산합니다.
     * 각 항목의 (점수/만점 × 항목가중치)를 합산하고, 항목 가중치 합으로 나눠 0~100 범위로 환산합니다.
     */
    private BigDecimal calculateTypeScore(List<EvaluationElementDTO> elements, Map<Long, Evaluation> evalByElementId) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (EvaluationElementDTO elem : elements) {
            Evaluation eval = evalByElementId.get(elem.elementId());
            if (eval == null || eval.getScore() == null) continue;

            BigDecimal score = BigDecimal.valueOf(eval.getScore());
            BigDecimal maxScore = elem.maxScore();
            BigDecimal weight = elem.weight();

            if (maxScore.compareTo(BigDecimal.ZERO) == 0) continue;

            // (점수 / 만점) × 항목가중치
            BigDecimal normalizedScore = score.divide(maxScore, 10, RoundingMode.HALF_UP).multiply(weight);
            weightedSum = weightedSum.add(normalizedScore);
            totalWeight = totalWeight.add(weight);
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // 0~100 범위로 환산
        return weightedSum.divide(totalWeight, 10, RoundingMode.HALF_UP)
                          .multiply(BigDecimal.valueOf(100))
                          .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public void calculateRelativeGradesForDepartment(Long periodId, Long deptId) {
        // 1. 해당 부서의 대상자 중 일반 직원(STAFF)만 모수로 필터링 (부서장은 제외)
        List<Long> empIdsInDept = employeeMapper.findByDeptId(deptId).stream()
                .map(Employee::getEmpId)
                .filter(empId -> departmentMapper.countDepartmentsByLeaderId(empId) == 0) // 부서장 제외
                .collect(Collectors.toList());
                
        if (empIdsInDept.isEmpty()) return;

        // 실제 평가 대상자로 등록된 인원만 대상 모수로 산정
        List<EvaluatorMapping> allMappingsInDept = mappingMapper.findByEvaluateeIds(periodId, empIdsInDept);
        List<EvaluatorMapping> mappings = allMappingsInDept.stream()
                .filter(m -> "EXECUTIVE".equals(m.getRelationTypeCode()))
                .collect(Collectors.toList());
        
        if (mappings.isEmpty()) {
            log.info("[ScoreCalc] EXECUTIVE 매핑 없음, MANAGER 매핑으로 폴백 (deptId={})", deptId);
            mappings = allMappingsInDept.stream()
                    .filter(m -> "MANAGER".equals(m.getRelationTypeCode()))
                    .collect(Collectors.toList());
        }

        // [핵심] EXECUTIVE 평가를 실제 제출 완료한 사람만 상대평가 대상으로 산정
        // 1차만 완료된 사람은 totalScore가 DB에 있어도 상대평가에서 제외
        List<Long> execMappingIds = mappings.stream()
                .map(EvaluatorMapping::getMappingId)
                .collect(Collectors.toList());

        Set<Long> submittedMappingIds = new HashSet<>();
        if (!execMappingIds.isEmpty()) {
            evaluationMapper.findByMappingIds(execMappingIds).stream()
                    .filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))
                    .map(Evaluation::getMappingId)
                    .forEach(submittedMappingIds::add);
        }

        Set<Long> execCompletedEmpIds = mappings.stream()
                .filter(m -> submittedMappingIds.contains(m.getMappingId()))
                .map(EvaluatorMapping::getEvaluateeId)
                .collect(Collectors.toSet());

        log.info("[ScoreCalc] 부서 상대평가 대상: 전체 매핑={}, EXECUTIVE 완료={} (deptId={})",
                mappings.size(), execCompletedEmpIds.size(), deptId);

        int totalEligible = execCompletedEmpIds.size();
        if (totalEligible == 0) return;

        // 2. 부서 비율 조회 및 TO(티오) 계산 (최대 잔여법)
        EvaluationGradeRatioDTO ratio = gradeRatioService.getGradeRatio(periodId, deptId);
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
        
        // 3. EXECUTIVE 제출 완료자이면서 점수가 있는 인원만 가져와서 내림차순 정렬
        List<FinalGrade> grades = finalGradeMapper.findByPeriodIdAndDeptId(periodId, deptId);
        grades.removeIf(g -> g.getTotalScore() == null || !execCompletedEmpIds.contains(g.getEmpId()));
        grades.sort(Comparator.comparing(FinalGrade::getTotalScore).reversed());
        
        // 4. 등급 부여 (동점자 동일 등급 처리 포함)
        String[] gradeLabels = {"S", "A", "B", "C", "D"};
        int currentGradeIdx = 0;
        int currentGradeAssigned = 0;
        Integer prevScore = null;
        String prevGrade = null;
        
        List<FinalGrade> gradesToUpdate = new ArrayList<>();
        
        for (FinalGrade fg : grades) {
            String assignedGrade;
            Integer score = fg.getTotalScore();
            
            // 동점자 처리: 이전 사람과 점수가 같으면 같은 등급 부여
            if (prevScore != null && prevScore.equals(score)) {
                assignedGrade = prevGrade;
                currentGradeAssigned++;
            } else {
                // 다음 등급 TO 찾기
                while (currentGradeIdx < 5 && currentGradeAssigned >= targets[currentGradeIdx]) {
                    currentGradeIdx++;
                    currentGradeAssigned = 0;
                }
                // 안전장치 (TO를 모두 소진했더라도 남은 사람에게 최하 등급 부여)
                if (currentGradeIdx >= 5) currentGradeIdx = 4;
                
                assignedGrade = gradeLabels[currentGradeIdx];
                currentGradeAssigned++;
            }
            
            fg.setFinalGradeCode(assignedGrade);
            gradesToUpdate.add(fg);
            
            prevScore = score;
            prevGrade = assignedGrade;
            
            log.debug("[ScoreCalc] 상대평가 등급 부여 완료 - empId={}, score={}, grade={}", fg.getEmpId(), score, assignedGrade);
        }
        
        if (!gradesToUpdate.isEmpty()) {
            finalGradeMapper.updateBatch(gradesToUpdate);
        }
    }

    @Override
    @Transactional
    public void calculateRelativeGradesForLeadersInHQ(Long periodId, Long parentDeptId) {
        if (parentDeptId == null) return;

        // 1. parentDeptId 산하의 모든 하위 부서의 리더(팀장) 사번 목록 조회
        List<Long> leaderEmpIds = departmentMapper.findSubordinateLeadersByParentDeptId(parentDeptId);
        if (leaderEmpIds.isEmpty()) return;

        // 2. 실제 평가 대상자로 등록된 인원만 대상 모수로 산정 (EXECUTIVE 매핑 기준)
        List<EvaluatorMapping> allMappingsInDept = mappingMapper.findByEvaluateeIds(periodId, leaderEmpIds);
        List<EvaluatorMapping> mappings = allMappingsInDept.stream()
                .filter(m -> "EXECUTIVE".equals(m.getRelationTypeCode()))
                .collect(Collectors.toList());

        if (mappings.isEmpty()) {
            log.info("[ScoreCalc] 본부 내 팀장들의 EXECUTIVE 매핑 없음, MANAGER 매핑으로 폴백 (parentDeptId={})", parentDeptId);
            mappings = allMappingsInDept.stream()
                    .filter(m -> "MANAGER".equals(m.getRelationTypeCode()))
                    .collect(Collectors.toList());
        }

        // EXECUTIVE 평가가 최종 완료(제출)된 팀장들만 필터링
        List<Long> execMappingIds = mappings.stream()
                .map(EvaluatorMapping::getMappingId)
                .collect(Collectors.toList());

        Set<Long> submittedMappingIds = new HashSet<>();
        if (!execMappingIds.isEmpty()) {
            evaluationMapper.findByMappingIds(execMappingIds).stream()
                    .filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))
                    .map(Evaluation::getMappingId)
                    .forEach(submittedMappingIds::add);
        }

        Set<Long> execCompletedEmpIds = mappings.stream()
                .filter(m -> submittedMappingIds.contains(m.getMappingId()))
                .map(EvaluatorMapping::getEvaluateeId)
                .collect(Collectors.toSet());

        log.info("[ScoreCalc] 본부 내 팀장 상대평가 대상: 전체 매핑={}, EXECUTIVE 완료={} (parentDeptId={})",
                mappings.size(), execCompletedEmpIds.size(), parentDeptId);

        int totalEligible = execCompletedEmpIds.size();
        if (totalEligible == 0) return;

        // Q2 결정사항: 전사 공통 등급 비율 (deptId = null) 조회
        EvaluationGradeRatioDTO ratio = gradeRatioService.getGradeRatio(periodId, null);
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

        // 본부 하위 부서 ID 목록 조회 (팀장들이 속해있는 부서들의 최종 성적을 모으기 위해)
        List<com.ees.eval.domain.Department> subDepts = departmentMapper.findByParentDeptId(parentDeptId);
        List<Long> subDeptIds = subDepts.stream().map(com.ees.eval.domain.Department::getDeptId).collect(Collectors.toList());
        subDeptIds.add(parentDeptId); // 본부(부모 부서) 소속 팀장도 있을 수 있으므로 추가

        List<FinalGrade> allGrades = new ArrayList<>();
        for (Long subDeptId : subDeptIds) {
            allGrades.addAll(finalGradeMapper.findByPeriodIdAndDeptId(periodId, subDeptId));
        }

        // 이들 중 제출 완료자이면서 점수가 있는 팀장들만 추림
        List<FinalGrade> grades = allGrades.stream()
                .filter(g -> g.getTotalScore() != null && execCompletedEmpIds.contains(g.getEmpId()))
                .collect(Collectors.toList());

        // 점수 내림차순 정렬
        grades.sort(Comparator.comparing(FinalGrade::getTotalScore).reversed());

        // 등급 부여 (동점자 처리 포함)
        String[] gradeLabels = {"S", "A", "B", "C", "D"};
        int currentGradeIdx = 0;
        int currentGradeAssigned = 0;
        Integer prevScore = null;
        String prevGrade = null;

        List<FinalGrade> gradesToUpdate = new ArrayList<>();

        for (FinalGrade fg : grades) {
            String assignedGrade;
            Integer score = fg.getTotalScore();

            if (prevScore != null && prevScore.equals(score)) {
                assignedGrade = prevGrade;
                currentGradeAssigned++;
            } else {
                while (currentGradeIdx < 5 && currentGradeAssigned >= targets[currentGradeIdx]) {
                    currentGradeIdx++;
                    currentGradeAssigned = 0;
                }
                if (currentGradeIdx >= 5) currentGradeIdx = 4;

                assignedGrade = gradeLabels[currentGradeIdx];
                currentGradeAssigned++;
            }

             fg.setFinalGradeCode(assignedGrade);
             gradesToUpdate.add(fg);

             prevScore = score;
             prevGrade = assignedGrade;

             log.debug("[ScoreCalc] 본부 팀장 상대평가 등급 부여 완료 - empId={}, score={}, grade={}", fg.getEmpId(), score, assignedGrade);
        }

        if (!gradesToUpdate.isEmpty()) {
            finalGradeMapper.updateBatch(gradesToUpdate);
        }
    }
}
