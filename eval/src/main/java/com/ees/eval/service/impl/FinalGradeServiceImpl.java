package com.ees.eval.service.impl;

import com.ees.eval.domain.Employee;
import com.ees.eval.domain.Evaluation;
import com.ees.eval.domain.EvaluatorMapping;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.FinalGradeTaskDTO;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationTypeWeightService;
import com.ees.eval.service.FinalGradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * FinalGradeService의 구현체입니다.
 * 벌크 조회 및 메모리 매핑을 통해 성능을 최적화합니다.
 */
@Service
@RequiredArgsConstructor
public class FinalGradeServiceImpl implements FinalGradeService {

    private final EvaluatorMappingMapper mappingMapper;
    private final EvaluationMapper evaluationMapper;
    private final EmployeeMapper employeeMapper;
    private final com.ees.eval.mapper.DepartmentMapper departmentMapper;
    private final EvaluationElementService elementService;
    private final EvaluationTypeWeightService typeWeightService;

    @Override
    @Transactional(readOnly = true)
    public List<FinalGradeTaskDTO> getFinalGradeTasks(Long periodId, Long executiveEmpId) {
        // 1. 임원의 평가 대상 목록(EXECUTIVE 매핑) 조회
        List<EvaluatorMapping> teamTasks = mappingMapper.findByEvaluatorId(periodId, executiveEmpId);
        if (teamTasks.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 피평가자 ID 목록 추출 및 사원 정보 벌크 조회
        List<Long> evaluateeIds = teamTasks.stream()
                .map(EvaluatorMapping::getEvaluateeId)
                .distinct()
                .collect(Collectors.toList());
        
        Map<Long, Employee> employeeMap = employeeMapper.findByIds(evaluateeIds).stream()
                .collect(Collectors.toMap(Employee::getEmpId, e -> e));

        // 3. 피평가자들의 모든 매핑 정보(SELF 포함) 벌크 조회
        List<EvaluatorMapping> allMappingsForEvaluatees = mappingMapper.findByEvaluateeIds(periodId, evaluateeIds);
        
        // 피평가자별 SELF 매핑 ID 맵 구성
        Map<Long, Long> selfMappingIdMap = allMappingsForEvaluatees.stream()
                .filter(m -> "SELF".equals(m.getRelationTypeCode()))
                .collect(Collectors.toMap(EvaluatorMapping::getEvaluateeId, EvaluatorMapping::getMappingId));

        // 4. 모든 관련 매핑 ID에 대한 평가 결과 벌크 조회
        List<Long> allRelatedMappingIds = allMappingsForEvaluatees.stream()
                .map(EvaluatorMapping::getMappingId)
                .collect(Collectors.toList());
        
        List<Evaluation> allEvals = evaluationMapper.findByMappingIds(allRelatedMappingIds);
        
        // 매핑 ID별 평가 결과 그룹화
        Map<Long, List<Evaluation>> evalGroupMap = allEvals.stream()
                .collect(Collectors.groupingBy(Evaluation::getMappingId));

        // 5. 부서별 가중치 유효성 및 평가 요소 정보 캐싱
        Map<Long, Boolean> weightValidCache = new HashMap<>();
        // 전사 공통 요소 (deptId = null)
        List<EvaluationElementDTO> globalElements = elementService.getElementsByPeriodId(periodId, null);

        // [최적화] 리더 여부 판별용 — 부서 목록을 1회 조회하여 리더 ID 세트 구성
        Set<Long> leaderEmpIds = departmentMapper.findAll().stream()
                .map(com.ees.eval.domain.Department::getLeaderId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        // [최적화] getTypeWeights 캐싱 (deptId + targetRole 조합)
        Map<String, java.util.List<com.ees.eval.dto.EvaluationTypeWeightDTO>> typeWeightsCache = new HashMap<>();

        // [최적화] 평가 요소 부서별 캐싱
        Map<Long, List<EvaluationElementDTO>> elementCacheByDeptId = new HashMap<>();

        // 6. 결과 DTO 조립
        return teamTasks.stream().map(task -> {
            Employee evaluatee = employeeMap.get(task.getEvaluateeId());
            Long deptId = (evaluatee != null) ? evaluatee.getDeptId() : null;

            // [최적화] 리더 여부: 메모리에서 Set 검색 (DB 호출 제거)
            boolean isLeader = leaderEmpIds.contains(task.getEvaluateeId());
            String targetRole = isLeader ? "LEADER" : "STAFF";

            // 가중치 유효성 체크 (캐시 활용)
            boolean weightValid = weightValidCache.computeIfAbsent(deptId != null ? deptId * 31 + targetRole.hashCode() : targetRole.hashCode() * 31L, 
                id -> typeWeightService.isWeightSumValid(periodId, deptId, targetRole));

            // [최적화] 해당 역할에 필요한 평가 항목 유형 필터링 (캐시 활용)
            String typeWeightsCacheKey = (deptId != null ? deptId : "null") + "_" + targetRole;
            java.util.List<com.ees.eval.dto.EvaluationTypeWeightDTO> typeWeights = typeWeightsCache.computeIfAbsent(
                    typeWeightsCacheKey, k -> typeWeightService.getTypeWeights(periodId, deptId, targetRole));
            List<String> requiredTypes = typeWeights.stream()
                    .map(com.ees.eval.dto.EvaluationTypeWeightDTO::elementTypeCode)
                    .collect(Collectors.toList());

            // [최적화] 평가 요소 부서별 캐싱
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

            // 본인 평가(EXECUTIVE) 완료 여부
            List<Evaluation> myEvals = evalGroupMap.getOrDefault(task.getMappingId(), Collections.emptyList());
            boolean allSubmitted = isAllSubmitted(requiredElements, myEvals);

            // 자가평가(SELF) 제출 여부
            Long selfMappingId = selfMappingIdMap.get(task.getEvaluateeId());
            List<Evaluation> selfEvals = selfMappingId != null ? evalGroupMap.getOrDefault(selfMappingId, Collections.emptyList()) : Collections.emptyList();
            boolean selfSubmitted = selfMappingId != null && isAllSubmitted(requiredElements, selfEvals);

            return FinalGradeTaskDTO.builder()
                    .mappingId(task.getMappingId())
                    .evaluateeId(task.getEvaluateeId())
                    .evaluateeName(task.getEvaluateeName())
                    .deptName(task.getDeptName())
                    .allSubmitted(allSubmitted)
                    .selfSubmitted(selfSubmitted)
                    .weightValid(weightValid)
                    .isLeader(isLeader)
                    .build();
        }).collect(Collectors.toList());
    }


    private boolean isAllSubmitted(List<EvaluationElementDTO> elements, List<Evaluation> evaluations) {
        if (elements.isEmpty()) return false;
        Set<Long> submittedElementIds = evaluations.stream()
                .filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))
                .map(Evaluation::getElementId)
                .collect(Collectors.toSet());
        return elements.stream().allMatch(e -> submittedElementIds.contains(e.elementId()));
    }
}
