package com.ees.eval.service.impl;

import com.ees.eval.domain.Employee;
import com.ees.eval.domain.Evaluation;
import com.ees.eval.domain.EvaluatorMapping;
import com.ees.eval.domain.FinalGrade;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.EvaluationResultDTO;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.mapper.FinalGradeMapper;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationResultService;
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
 * 평가 결과 현황 조회 서비스 구현체입니다.
 * 확정된 사원의 유형별 1차/2차/최종 점수, 상태를 산출합니다.
 *
 * <p>점수 산출 기준:</p>
 * <ul>
 *     <li>1차 점수: MANAGER 매핑 (MBO/COMP), SUBORDINATE 매핑 평균 (MULTI)</li>
 *     <li>2차 점수: EXECUTIVE 매핑</li>
 *     <li>최종 점수: 2차 점수 그대로</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationResultServiceImpl implements EvaluationResultService {

    private final FinalGradeMapper finalGradeMapper;
    private final EvaluatorMappingMapper mappingMapper;
    private final EvaluationMapper evaluationMapper;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final EvaluationElementService elementService;
    private final ScoreCalculationService scoreCalculationService;

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationResultDTO> getResults(Long periodId, Long deptId) {
        // 1. 해당 차수의 모든 매핑을 한 번에 조회 (N+1 방지)
        List<EvaluatorMapping> allMappings = mappingMapper.findAllByPeriodId(periodId);
        if (allMappings.isEmpty()) {
            return Collections.emptyList();
        }

        // 삭제되지 않은 매핑만 필터
        allMappings = allMappings.stream()
                .filter(m -> "n".equals(m.getIsDeleted()))
                .collect(Collectors.toList());

        // 2. 피평가자 ID 추출 (매핑 기반 → 1차만 완료된 사원도 포함)
        List<Long> evaluateeIds = allMappings.stream()
                .map(EvaluatorMapping::getEvaluateeId)
                .distinct()
                .collect(Collectors.toList());

        if (evaluateeIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 사원 정보 벌크 조회 (1회 쿼리)
        Map<Long, Employee> employeeMap = employeeMapper.findByIds(evaluateeIds).stream()
                .collect(Collectors.toMap(Employee::getEmpId, e -> e, (a, b) -> a));

        // 4. 부서 필터 적용
        if (deptId != null) {
            evaluateeIds = evaluateeIds.stream()
                    .filter(empId -> {
                        Employee emp = employeeMap.get(empId);
                        return emp != null && deptId.equals(emp.getDeptId());
                    })
                    .collect(Collectors.toList());
            if (evaluateeIds.isEmpty()) {
                return Collections.emptyList();
            }
        }

        // 5. 관계 유형별 매핑 분류 (메모리 내 그룹핑)
        Map<Long, EvaluatorMapping> execByEmp = filterMappingByType(allMappings, "EXECUTIVE");
        Map<Long, EvaluatorMapping> mgrByEmp = filterMappingByType(allMappings, "MANAGER");
        Map<Long, List<EvaluatorMapping>> subByEmp = allMappings.stream()
                .filter(m -> "SUBORDINATE".equals(m.getRelationTypeCode()))
                .collect(Collectors.groupingBy(EvaluatorMapping::getEvaluateeId));

        // 6. 모든 매핑의 평가 데이터 일괄 조회 (1회 쿼리)
        List<Long> allMappingIds = allMappings.stream()
                .map(EvaluatorMapping::getMappingId).collect(Collectors.toList());
        Map<Long, List<Evaluation>> evalGroupMap = !allMappingIds.isEmpty()
                ? evaluationMapper.findByMappingIds(allMappingIds).stream()
                    .collect(Collectors.groupingBy(Evaluation::getMappingId))
                : Collections.emptyMap();

        // 7. 최종 등급 벌크 조회 (1회 쿼리, 확정된 사원만 존재)
        Map<Long, FinalGrade> gradeMap = finalGradeMapper.findByPeriodId(periodId).stream()
                .collect(Collectors.toMap(FinalGrade::getEmpId, g -> g, (a, b) -> a));

        // 8. 전체 부서장 ID 벌크 조회 (N+1 완벽 방지)
        Set<Long> allLeaderIds = new HashSet<>(departmentMapper.findAllLeaderIds());

        // 9. 평가 요소 벌크 조회 및 부서별 그룹핑 (대상 부서 + 공통 항목만 조회)
        List<Long> deptIdsForElements = evaluateeIds.stream()
                .map(employeeMap::get)
                .filter(Objects::nonNull)
                .map(Employee::getDeptId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<EvaluationElementDTO> allElements =
                elementService.getElementsByPeriodIdAndDeptIds(periodId, deptIdsForElements);
        List<EvaluationElementDTO> commonElements = new ArrayList<>();
        Map<Long, List<EvaluationElementDTO>> elementsByDept = new HashMap<>();
        for (EvaluationElementDTO element : allElements) {
            if (element.deptId() == null) {
                commonElements.add(element);
            } else {
                elementsByDept.computeIfAbsent(element.deptId(), k -> new ArrayList<>()).add(element);
            }
        }

        // 10. 결과 DTO 조립
        List<EvaluationResultDTO> results = new ArrayList<>();
        for (Long empId : evaluateeIds) {
            Employee emp = employeeMap.get(empId);
            if (emp == null) continue;

            // 부서장 여부 (단일 Set 활용)
            boolean isLeader = allLeaderIds.contains(empId);

            // 평가 요소 조회 (메모리 Map 활용 - 부서 전용 없으면 공통으로 폴백)
            List<EvaluationElementDTO> elements = emp.getDeptId() == null
                    ? commonElements
                    : elementsByDept.get(emp.getDeptId());
            if (emp.getDeptId() != null && (elements == null || elements.isEmpty())) {
                elements = commonElements;
            }
            if (elements == null) elements = Collections.emptyList();

            // === MBO/COMP 점수 (1차: MANAGER, 2차: EXECUTIVE) ===
            BigDecimal mbo1st = calcTypeScore(mgrByEmp.get(empId), evalGroupMap, elements, "PERFORMANCE");
            BigDecimal mbo2nd = calcTypeScore(execByEmp.get(empId), evalGroupMap, elements, "PERFORMANCE");
            BigDecimal comp1st = calcTypeScore(mgrByEmp.get(empId), evalGroupMap, elements, "COMPETENCY");
            BigDecimal comp2nd = calcTypeScore(execByEmp.get(empId), evalGroupMap, elements, "COMPETENCY");

            // === MULTI 점수 (1차: SUBORDINATE 평균, 2차: EXECUTIVE) ===
            BigDecimal multi1st = calcSubordinateAverage(subByEmp.get(empId), evalGroupMap, elements);
            BigDecimal multi2nd = calcTypeScore(execByEmp.get(empId), evalGroupMap, elements, "MULTI_DIMENSIONAL");

            // 상태 판단
            String mboStatus = determineStatus(mgrByEmp.get(empId), execByEmp.get(empId), evalGroupMap, elements, "PERFORMANCE");
            String compStatus = determineStatus(mgrByEmp.get(empId), execByEmp.get(empId), evalGroupMap, elements, "COMPETENCY");
            String multiStatus = determineMultiStatus(subByEmp.get(empId), execByEmp.get(empId), evalGroupMap, elements);

            // 종합 점수 (FinalGrade 존재하면 사용, 없으면 null)
            FinalGrade fg = gradeMap.get(empId);
            BigDecimal totalScore = (fg != null && fg.getTotalScore() != null)
                    ? new BigDecimal(fg.getTotalScore()) : null;
            String gradeCode = (fg != null) ? fg.getFinalGradeCode() : null;

            results.add(EvaluationResultDTO.builder()
                    .empId(empId)
                    .empName(emp.getName())
                    .deptName(emp.getDeptName())
                    .positionName(emp.getPositionName())
                    .jobTitle(isLeader ? "부서장" : "팀원")
                    .isLeader(isLeader)
                    .mbo1stScore(mbo1st).mbo2ndScore(mbo2nd)
                    .mboFinalScore(mbo2nd)  // 최종 = 2차
                    .mboStatus(mboStatus)
                    .comp1stScore(comp1st).comp2ndScore(comp2nd)
                    .compFinalScore(comp2nd) // 최종 = 2차
                    .compStatus(compStatus)
                    .multi1stScore(multi1st).multi2ndScore(multi2nd)
                    .multiFinalScore(multi2nd) // 최종 = 2차
                    .multiStatus(multiStatus)
                    .totalScore(totalScore)
                    .gradeCode(gradeCode)
                    .isConfirmed(fg != null)
                    .build());
        }

        // 11. 1차 평가 이상 진행된 사원만 필터링 (사용자 요청)
        results = results.stream()
                .filter(r -> "1차평가완료".equals(r.mboStatus()) || "2차평가완료".equals(r.mboStatus()) ||
                             "1차평가완료".equals(r.compStatus()) || "2차평가완료".equals(r.compStatus()) ||
                             "1차평가완료".equals(r.multiStatus()) || "2차평가완료".equals(r.multiStatus()))
                .collect(Collectors.toList());

        // 12. 직급 기준 정렬
        results.sort((a, b) -> {
            Employee empA = employeeMap.get(a.empId());
            Employee empB = employeeMap.get(b.empId());
            if (empA == null || empB == null) return 0;
            long posA = empA.getPositionId() != null ? empA.getPositionId() : 0;
            long posB = empB.getPositionId() != null ? empB.getPositionId() : 0;
            return Long.compare(posB, posA);
        });

        return results;
    }

    // ========================================================================
    // 내부 헬퍼 메서드
    // ========================================================================

    /**
     * 특정 관계 유형의 매핑을 피평가자 기준으로 추출합니다.
     */
    private Map<Long, EvaluatorMapping> filterMappingByType(List<EvaluatorMapping> mappings, String type) {
        return mappings.stream()
                .filter(m -> type.equals(m.getRelationTypeCode()) && "n".equals(m.getIsDeleted()))
                .collect(Collectors.toMap(EvaluatorMapping::getEvaluateeId, m -> m, (a, b) -> a));
    }

    /**
     * 단일 매핑의 특정 유형 점수를 0~100 범위로 환산합니다.
     *
     * @param mapping  평가자 매핑 (null이면 null 반환)
     * @param evalMap  매핑ID별 평가 데이터
     * @param elements 평가 요소 목록
     * @param typeCode 평가 유형 (PERFORMANCE/COMPETENCY/MULTI_DIMENSIONAL)
     * @return 환산 점수 (소수점 2자리), 데이터 없으면 null
     */
    private BigDecimal calcTypeScore(EvaluatorMapping mapping,
                                      Map<Long, List<Evaluation>> evalMap,
                                      List<EvaluationElementDTO> elements,
                                      String typeCode) {
        if (mapping == null) return null;

        List<Evaluation> evals = evalMap.getOrDefault(mapping.getMappingId(), Collections.emptyList())
                .stream().filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))
                .collect(Collectors.toList());
        if (evals.isEmpty()) return null;

        Map<Long, Evaluation> evalByElement = evals.stream()
                .collect(Collectors.toMap(Evaluation::getElementId, e -> e, (a, b) -> a));

        List<EvaluationElementDTO> typeElements = elements.stream()
                .filter(e -> typeCode.equals(e.elementTypeCode()))
                .collect(Collectors.toList());
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
     * SUBORDINATE 매핑들의 다면평가 점수 평균을 산출합니다.
     *
     * @param subMappings SUBORDINATE 매핑 목록
     * @param evalMap     매핑ID별 평가 데이터
     * @param elements    평가 요소 목록
     * @return 평균 점수 (소수점 2자리), 데이터 없으면 null
     */
    private BigDecimal calcSubordinateAverage(List<EvaluatorMapping> subMappings,
                                               Map<Long, List<Evaluation>> evalMap,
                                               List<EvaluationElementDTO> elements) {
        if (subMappings == null || subMappings.isEmpty()) return null;

        List<BigDecimal> scores = new ArrayList<>();
        for (EvaluatorMapping sub : subMappings) {
            BigDecimal score = calcTypeScore(sub, evalMap, elements, "MULTI_DIMENSIONAL");
            if (score != null) {
                scores.add(score);
            }
        }

        if (scores.isEmpty()) return null;

        BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(scores.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * MBO/COMP 평가 진행 상태를 판단합니다.
     *
     * @return "2차평가완료", "1차평가완료", "평가대기", "미배정"
     */
    private String determineStatus(EvaluatorMapping mgrMapping, EvaluatorMapping execMapping,
                                    Map<Long, List<Evaluation>> evalMap,
                                    List<EvaluationElementDTO> elements, String typeCode) {
        // 2차(EXECUTIVE) 제출 여부 우선 확인
        if (hasSubmittedEvals(execMapping, evalMap, elements, typeCode)) {
            return "2차평가완료";
        }
        // 1차(MANAGER) 제출 여부 확인
        if (hasSubmittedEvals(mgrMapping, evalMap, elements, typeCode)) {
            return "1차평가완료";
        }
        // 매핑 존재 여부
        if (mgrMapping != null || execMapping != null) {
            return "평가대기";
        }
        return "미배정";
    }

    /**
     * 다면평가 진행 상태를 판단합니다.
     */
    private String determineMultiStatus(List<EvaluatorMapping> subMappings, EvaluatorMapping execMapping,
                                         Map<Long, List<Evaluation>> evalMap,
                                         List<EvaluationElementDTO> elements) {
        if (hasSubmittedEvals(execMapping, evalMap, elements, "MULTI_DIMENSIONAL")) {
            return "2차평가완료";
        }
        if (subMappings != null && subMappings.stream()
                .anyMatch(m -> hasSubmittedEvals(m, evalMap, elements, "MULTI_DIMENSIONAL"))) {
            return "1차평가완료";
        }
        if ((subMappings != null && !subMappings.isEmpty()) || execMapping != null) {
            return "평가대기";
        }
        return "미배정";
    }

    /**
     * 특정 매핑에 해당 유형의 제출된 평가가 있는지 확인합니다.
     */
    private boolean hasSubmittedEvals(EvaluatorMapping mapping,
                                      Map<Long, List<Evaluation>> evalMap,
                                      List<EvaluationElementDTO> elements, String typeCode) {
        if (mapping == null) return false;
        List<Evaluation> evals = evalMap.getOrDefault(mapping.getMappingId(), Collections.emptyList());

        // 해당 유형의 요소 ID 집합
        Set<Long> typeElementIds = elements.stream()
                .filter(e -> typeCode.equals(e.elementTypeCode()))
                .map(EvaluationElementDTO::elementId)
                .collect(Collectors.toSet());

        return evals.stream()
                .anyMatch(e -> "SUBMITTED".equals(e.getConfirmStatusCode())
                        && typeElementIds.contains(e.getElementId()));
    }
}
