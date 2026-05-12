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
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationTypeWeightService;
import com.ees.eval.service.ScoreCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 평가 점수 산출 및 절대평가 등급 매핑을 담당하는 서비스 구현체입니다.
 *
 * <p>점수 산출 공식:</p>
 * <pre>
 * 종합점수 = Σ (유형별 가중치 × 유형 내 환산점수)
 * 유형 내 환산점수 = Σ (항목 점수 / 항목 만점 × 항목 가중치) / Σ(항목 가중치) × 100
 * </pre>
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

    @Override
    public String determineGrade(int totalScore) {
        if (totalScore >= 95) return "S";
        if (totalScore >= 85) return "A";
        if (totalScore >= 75) return "B";
        if (totalScore >= 60) return "C";
        return "D";
    }

    @Override
    @Transactional(readOnly = true)
    public Integer calculateTotalScore(Long periodId, Long empId) {
        // 1. 해당 사원의 EXECUTIVE 매핑에서 제출된 평가 데이터를 조회
        List<EvaluatorMapping> mappings = mappingMapper.findByEvaluateeId(periodId, empId);
        EvaluatorMapping execMapping = mappings.stream()
                .filter(m -> "EXECUTIVE".equals(m.getRelationTypeCode()))
                .findFirst()
                .orElse(null);

        if (execMapping == null) {
            log.debug("[ScoreCalc] No EXECUTIVE mapping found for periodId={}, empId={}", periodId, empId);
            return null;
        }

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

        log.info("[ScoreCalc] periodId={}, empId={}, totalScore={}, grade={}", 
                 periodId, empId, finalScore, determineGrade(finalScore));
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
}
