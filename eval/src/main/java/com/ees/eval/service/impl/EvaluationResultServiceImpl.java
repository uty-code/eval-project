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
import com.ees.eval.service.EvaluationPeriodService;
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
    private final EvaluationPeriodService periodService;
    private final ScoreCalculationService scoreCalculationService;

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationResultDTO> getResults(Long periodId, Long deptId, String search) {
        // periodId가 0(전체)인 경우 null로 판단하여 전체 차수 통합 조회 처리
        Long targetPeriodId = (periodId != null && periodId == 0L) ? null : periodId;

        // 1. 해당 차수(혹은 전체 차수)의 모든 매핑을 한 번에 조회 (N+1 방지)
        List<EvaluatorMapping> allMappings = mappingMapper.findAllByPeriodId(targetPeriodId);
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

        // 3. 사원 정보 벌크 조회 (1회 쿼리, MSSQL 2100 제한 Chunking)
        List<Employee> allEmployees = new java.util.ArrayList<>();
        int chunkSize = 1000;
        for (int i = 0; i < evaluateeIds.size(); i += chunkSize) {
            allEmployees.addAll(employeeMapper.findByIds(evaluateeIds.subList(i, Math.min(i + chunkSize, evaluateeIds.size()))));
        }
        Map<Long, Employee> employeeMap = allEmployees.stream()
                .collect(Collectors.toMap(Employee::getEmpId, e -> e, (a, b) -> a));

        // 4. 필터 적용 (부서 + 검색어)
        evaluateeIds = evaluateeIds.stream()
                .filter(empId -> {
                    Employee emp = employeeMap.get(empId);
                    if (emp == null) return false;

                    // 부서 필터
                    if (deptId != null && !deptId.equals(emp.getDeptId())) return false;

                    // 검색어 필터 (성명 또는 사번)
                    if (search != null && !search.trim().isEmpty()) {
                        String s = search.trim().toLowerCase();
                        boolean nameMatch = emp.getName() != null && emp.getName().toLowerCase().contains(s);
                        boolean idMatch = emp.getEmpId() != null && String.valueOf(emp.getEmpId()).contains(s);
                        if (!nameMatch && !idMatch) return false;
                    }

                    return true;
                })
                .collect(Collectors.toList());

        if (evaluateeIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 10. 결과 DTO 조립 및 필터링/정렬
        return assembleAndProcessResults(targetPeriodId, evaluateeIds, employeeMap, allMappings, null, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public EvaluationResultDTO getResultByEmpId(Long periodId, Long empId) {
        // 1. 해당 사원의 매핑만 조회
        List<EvaluatorMapping> mappings = mappingMapper.findByEvaluateeId(periodId, empId);
        if (mappings.isEmpty()) return null;

        mappings = mappings.stream().filter(m -> "n".equals(m.getIsDeleted())).collect(Collectors.toList());
        if (mappings.isEmpty()) return null;

        // 2. 결과 조립 (단건 리스트로 처리하여 기존 로직 최대한 재사용)
        List<EvaluationResultDTO> results = assembleAndProcessResults(periodId, List.of(empId), null, mappings, null, null, null, null);
        
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 벌크 데이터를 기반으로 결과 DTO를 조립하고 필터링/정렬을 수행합니다.
     */
    private List<EvaluationResultDTO> assembleAndProcessResults(
            Long periodId,
            List<Long> evaluateeIds,
            Map<Long, Employee> employeeMap,
            List<EvaluatorMapping> allMappings,
            Map<Long, FinalGrade> gradeMap,
            Set<Long> allLeaderIds,
            Map<Long, List<EvaluationElementDTO>> elementsByDept,
            List<EvaluationElementDTO> commonElements) {

        // --- 부족한 데이터 보충 (단건 조회 시 등을 위해) ---
        if (employeeMap == null) {
            List<Employee> allEmployees = new java.util.ArrayList<>();
            int chunkSize = 1000;
            for (int i = 0; i < evaluateeIds.size(); i += chunkSize) {
                allEmployees.addAll(employeeMapper.findByIds(evaluateeIds.subList(i, Math.min(i + chunkSize, evaluateeIds.size()))));
            }
            employeeMap = allEmployees.stream()
                    .collect(Collectors.toMap(Employee::getEmpId, e -> e, (a, b) -> a));
        }

        // 전체 차수 통합 조회를 위해 periodId가 0 또는 null인 경우 null 처리
        Long targetPeriodId = (periodId != null && periodId == 0L) ? null : periodId;

        // 다차수 안전한 FinalGrade 수집 맵 생성 (periodId + "_" + empId 복합 키)
        Map<String, FinalGrade> customGradeMap = finalGradeMapper.findByPeriodId(targetPeriodId).stream()
                .collect(Collectors.toMap(
                        g -> g.getPeriodId() + "_" + g.getEmpId(),
                        g -> g,
                        (a, b) -> a
                ));

        if (allLeaderIds == null) {
            allLeaderIds = new HashSet<>(departmentMapper.findAllLeaderIds());
        }

        // 차수별/부서별 평가 요소를 안전하게 수집하기 위한 동적 맵 구축
        List<Long> deptIds = evaluateeIds.stream()
                .map(employeeMap::get).filter(Objects::nonNull)
                .map(Employee::getDeptId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        
        List<EvaluationElementDTO> allElements = elementService.getElementsByPeriodIdAndDeptIds(targetPeriodId, deptIds);
        
        // elementsMap의 키: "periodId_deptId" 또는 "periodId_COMMON"
        Map<String, List<EvaluationElementDTO>> elementsMap = allElements.stream()
                .collect(Collectors.groupingBy(e -> e.periodId() + "_" + (e.deptId() != null ? e.deptId() : "COMMON")));

        // 차수 메타데이터 캐싱 (periodId -> EvaluationPeriodDTO)
        Map<Long, com.ees.eval.dto.EvaluationPeriodDTO> periodMetadataMap = periodService.getAllPeriods().stream()
                .collect(Collectors.toMap(com.ees.eval.dto.EvaluationPeriodDTO::periodId, p -> p, (a, b) -> a));

        // 매핑 분류 (다차수 통합을 위해 복합 키 "periodId_evaluateeId" 기반 맵 사용)
        Map<String, EvaluatorMapping> execByEmp = filterMappingByPeriodAndType(allMappings, "EXECUTIVE");
        Map<String, EvaluatorMapping> mgrByEmp = filterMappingByPeriodAndType(allMappings, "MANAGER");
        Map<String, EvaluatorMapping> selfByEmp = filterMappingByPeriodAndType(allMappings, "SELF");
        Map<String, List<EvaluatorMapping>> subByEmp = allMappings.stream()
                .filter(m -> "SUBORDINATE".equals(m.getRelationTypeCode()) && "n".equals(m.getIsDeleted()))
                .collect(Collectors.groupingBy(m -> m.getPeriodId() + "_" + m.getEvaluateeId()));

        List<Long> allMappingIds = allMappings.stream().map(EvaluatorMapping::getMappingId).collect(Collectors.toList());
        List<Evaluation> fetchedEvals = new java.util.ArrayList<>();
        if (!allMappingIds.isEmpty()) {
            int chunkSize = 1000;
            for (int i = 0; i < allMappingIds.size(); i += chunkSize) {
                fetchedEvals.addAll(evaluationMapper.findByMappingIds(allMappingIds.subList(i, Math.min(i + chunkSize, allMappingIds.size()))));
            }
        }
        Map<Long, List<Evaluation>> evalGroupMap = !fetchedEvals.isEmpty()
                ? fetchedEvals.stream().collect(Collectors.groupingBy(Evaluation::getMappingId))
                : Collections.emptyMap();

        // 피평가자 ID + 차수 ID의 고유한 키 조합 추출 (중복 제거)
        List<String> evalPeriodKeys = allMappings.stream()
                .filter(m -> "n".equals(m.getIsDeleted()) && evaluateeIds.contains(m.getEvaluateeId()))
                .map(m -> m.getEvaluateeId() + "_" + m.getPeriodId())
                .distinct()
                .collect(Collectors.toList());

        List<EvaluationResultDTO> results = new ArrayList<>();
        for (String key : evalPeriodKeys) {
            String[] parts = key.split("_");
            Long empId = Long.parseLong(parts[0]);
            Long currPeriodId = Long.parseLong(parts[1]);

            Employee emp = employeeMap.get(empId);
            if (emp == null) continue;

            boolean isLeader = allLeaderIds.contains(empId);
            
            // 해당 차수 & 해당 부서(또는 공통)에 해당하는 평가 항목 추출
            String deptKey = currPeriodId + "_" + (emp.getDeptId() != null ? emp.getDeptId() : "COMMON");
            List<EvaluationElementDTO> elements = elementsMap.get(deptKey);
            if (elements == null || elements.isEmpty()) {
                elements = elementsMap.get(currPeriodId + "_COMMON");
            }
            if (elements == null) {
                elements = Collections.emptyList();
            }

            String mapKey = currPeriodId + "_" + empId;

            // 점수 산출
            BigDecimal mboSelf = calcTypeScore(selfByEmp.get(mapKey), evalGroupMap, elements, "PERFORMANCE");
            BigDecimal mbo1st = calcTypeScore(mgrByEmp.get(mapKey), evalGroupMap, elements, "PERFORMANCE");
            BigDecimal mbo2nd = calcTypeScore(execByEmp.get(mapKey), evalGroupMap, elements, "PERFORMANCE");
            BigDecimal compSelf = calcTypeScore(selfByEmp.get(mapKey), evalGroupMap, elements, "COMPETENCY");
            BigDecimal comp1st = calcTypeScore(mgrByEmp.get(mapKey), evalGroupMap, elements, "COMPETENCY");
            BigDecimal comp2nd = calcTypeScore(execByEmp.get(mapKey), evalGroupMap, elements, "COMPETENCY");
            BigDecimal multiSelf = calcTypeScore(selfByEmp.get(mapKey), evalGroupMap, elements, "MULTI_DIMENSIONAL");
            BigDecimal multi1st = calcSubordinateAverage(subByEmp.get(mapKey), evalGroupMap, elements);
            BigDecimal multi2nd = calcTypeScore(execByEmp.get(mapKey), evalGroupMap, elements, "MULTI_DIMENSIONAL");

            // 상태 판단
            String mboStatus = determineStatus(mgrByEmp.get(mapKey), execByEmp.get(mapKey), evalGroupMap, elements, "PERFORMANCE");
            String compStatus = determineStatus(mgrByEmp.get(mapKey), execByEmp.get(mapKey), evalGroupMap, elements, "COMPETENCY");
            String multiStatus = determineMultiStatus(subByEmp.get(mapKey), execByEmp.get(mapKey), evalGroupMap, elements);

            FinalGrade fg = customGradeMap.get(mapKey);
            BigDecimal totalScore = (fg != null && fg.getTotalScore() != null) ? new BigDecimal(fg.getTotalScore()) : null;
            String gradeCode = (fg != null) ? fg.getFinalGradeCode() : null;

            boolean isFullyDone = isLeader ? "2차평가완료".equals(multiStatus)
                    : (("2차평가완료".equals(mboStatus) || "미배정".equals(mboStatus)) && ("2차평가완료".equals(compStatus) || "미배정".equals(compStatus)));

            com.ees.eval.dto.EvaluationPeriodDTO periodDto = periodMetadataMap.get(currPeriodId);
            String pName = periodDto != null ? periodDto.periodName() : "";
            Integer pYear = periodDto != null ? periodDto.periodYear() : 0;

            results.add(EvaluationResultDTO.builder()
                    .empId(empId).empName(emp.getName()).deptName(emp.getDeptName()).positionName(emp.getPositionName())
                    .jobTitle(isLeader ? "부서장" : "팀원").isLeader(isLeader)
                    .mboSelfScore(mboSelf).mbo1stScore(mbo1st).mbo2ndScore(mbo2nd).mboFinalScore(mbo2nd).mboStatus(mboStatus)
                    .compSelfScore(compSelf).comp1stScore(comp1st).comp2ndScore(comp2nd).compFinalScore(comp2nd).compStatus(compStatus)
                    .multiSelfScore(multiSelf).multi1stScore(multi1st).multi2ndScore(multi2nd).multiFinalScore(multi2nd).multiStatus(multiStatus)
                    .totalScore(totalScore).gradeCode(gradeCode).isConfirmed(isFullyDone)
                    .periodId(currPeriodId).periodName(pName).periodYear(pYear)
                    .build());
        }

        // 1차 평가 이상 진행된 사원만 필터링
        results = results.stream()
                .filter(r -> "1차평가완료".equals(r.mboStatus()) || "2차평가완료".equals(r.mboStatus()) ||
                             "1차평가완료".equals(r.compStatus()) || "2차평가완료".equals(r.compStatus()) ||
                             "1차평가완료".equals(r.multiStatus()) || "2차평가완료".equals(r.multiStatus()))
                .collect(Collectors.toList());

        // 직급 기준 정렬
        Map<Long, Employee> finalEmployeeMap = employeeMap;
        results.sort((a, b) -> {
            Employee empA = finalEmployeeMap.get(a.empId());
            Employee empB = finalEmployeeMap.get(b.empId());
            if (empA == null || empB == null) return 0;
            return Long.compare(empB.getPositionId() != null ? empB.getPositionId() : 0, empA.getPositionId() != null ? empA.getPositionId() : 0);
        });

        return results;
    }

    // ========================================================================
    // 내부 헬퍼 메서드
    // ========================================================================

    /**
     * 특정 관계 유형의 매핑을 차수 + 피평가자 기준으로 추출합니다.
     */
    private Map<String, EvaluatorMapping> filterMappingByPeriodAndType(List<EvaluatorMapping> mappings, String type) {
        return mappings.stream()
                .filter(m -> type.equals(m.getRelationTypeCode()) && "n".equals(m.getIsDeleted()))
                .collect(Collectors.toMap(m -> m.getPeriodId() + "_" + m.getEvaluateeId(), m -> m, (a, b) -> a));
    }

    /**
     * 기존 단일 차수용 필터 메서드 (호환성 유지용)
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
