package com.ees.eval.service.impl;

import com.ees.eval.domain.Employee;
import com.ees.eval.domain.Evaluation;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.dto.enums.RelationType;
import com.ees.eval.dto.enums.ConfirmStatus;
import com.ees.eval.dto.enums.EvaluationElementType;
import com.ees.eval.dto.enums.EvaluationPeriodStatus;
import com.ees.eval.dto.enums.WeightTargetRole;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MyEvaluationFacadeServiceImpl implements MyEvaluationFacadeService {

    private final EvaluationPeriodService periodService;
    private final EvaluatorMappingService mappingService;
    private final EvaluationElementService elementService;
    private final EvaluationTypeWeightService typeWeightService;
    private final EvaluationService evaluationService;
    private final EvaluationMapper evaluationMapper;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardData(Long empId, Long periodId, String status, String keyword, int page, int pageSize) {
        Map<String, Object> data = new HashMap<>();

        // 1. 차수 목록 조회 및 정렬
        List<EvaluationPeriodDTO> allPeriods = periodService.getAllPeriods();
        List<EvaluationPeriodDTO> sortedPeriods = new ArrayList<>(allPeriods);
        sortedPeriods.sort((p1, p2) -> {
            boolean p1Active = EvaluationPeriodStatus.IN_PROGRESS.getCode().equals(p1.statusCode());
            boolean p2Active = EvaluationPeriodStatus.IN_PROGRESS.getCode().equals(p2.statusCode());
            if (p1Active && !p2Active) return -1;
            if (!p1Active && p2Active) return 1;
            int yearCompare = p2.periodYear().compareTo(p1.periodYear());
            if (yearCompare != 0) return yearCompare;
            return p2.periodId().compareTo(p1.periodId());
        });
        data.put("periods", sortedPeriods);

        // 2. 자가평가 태스크 페이징 조회
        var pageData = mappingService.getMyEvaluationDashboardTasks(empId, periodId, status, keyword, page, pageSize);
        data.put("pageData", pageData);
        data.put("selectedPeriodId", periodId);
        data.put("filterStatus", status);
        data.put("keyword", keyword);

        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getWizardData(Long mappingId, Long empId) {
        Map<String, Object> data = new HashMap<>();

        // 1. 매핑 조회 및 기본 검증
        EvaluatorMappingDTO mapping = validateAndGetMapping(mappingId, empId);
        data.put("mapping", mapping);
        data.put("mappingId", mappingId);

        // 2. 피평가자 정보 및 가중치 검증
        Employee evaluatee = employeeMapper.findById(mapping.evaluateeId())
                .orElseThrow(() -> new IllegalArgumentException("피평가자 정보를 찾을 수 없습니다."));
        
        boolean evaluateeIsLeader = departmentMapper.countDepartmentsByLeaderId(mapping.evaluateeId()) > 0;
        String targetRole = evaluateeIsLeader ? WeightTargetRole.LEADER.getCode() : WeightTargetRole.STAFF.getCode();

        if (!typeWeightService.isWeightSumValid(mapping.periodId(), evaluatee.getDeptId(), targetRole)) {
            throw new IllegalStateException("[" + targetRole + "] 유형별 가중치 합계가 100%가 아닙니다.");
        }
        data.put("evaluateeIsLeader", evaluateeIsLeader);

        // 3. 평가요소 조회 및 분류
        List<EvaluationElementDTO> allElements = elementService.getElementsWithFallback(mapping.periodId(), evaluatee.getDeptId());
        
        data.put("perfElements", filterElementsByType(allElements, EvaluationElementType.PERFORMANCE));
        data.put("compElements", filterElementsByType(allElements, EvaluationElementType.COMPETENCY));
        data.put("peerElements", filterElementsByType(allElements, EvaluationElementType.MULTI_DIMENSIONAL));

        // 4. 기존 데이터 및 상태 조회
        List<Evaluation> savedEvaluations = evaluationMapper.findByMappingId(mappingId);
        Map<Long, Evaluation> savedMap = savedEvaluations.stream()
                .collect(Collectors.toMap(Evaluation::getElementId, e -> e, (a, b) -> a));
        data.put("savedMap", savedMap);

        boolean submitted = savedEvaluations.stream()
                .anyMatch(e -> ConfirmStatus.SUBMITTED.getCode().equals(e.getConfirmStatusCode()));
        data.put("submitted", submitted);

        // 5. 잠금 상태 확인
        data.putAll(mappingService.checkEvaluationLock(mappingId));

        return data;
    }

    @Override
    @Transactional
    public void submitEvaluation(Long mappingId, Map<String, String> params, Long empId) {
        // 1. 매핑 조회 및 검증 (권한, SELF, 기간, 잠금)
        EvaluatorMappingDTO mapping = validateAndGetMapping(mappingId, empId);

        // 2. 가중치 재검증
        Employee evaluatee = employeeMapper.findById(mapping.evaluateeId()).orElse(null);
        Long deptId = (evaluatee != null) ? evaluatee.getDeptId() : null;
        boolean isLeader = departmentMapper.countDepartmentsByLeaderId(mapping.evaluateeId()) > 0;
        String targetRole = isLeader ? WeightTargetRole.LEADER.getCode() : WeightTargetRole.STAFF.getCode();

        if (!typeWeightService.isWeightSumValid(mapping.periodId(), deptId, targetRole)) {
            throw new IllegalStateException("유형별 가중치 합계가 100%가 아니어서 제출할 수 없습니다.");
        }

        // 3. 잠금 상태 재확인
        Map<String, Object> lockInfo = mappingService.checkEvaluationLock(mappingId);
        if ((Boolean) lockInfo.get("isLocked")) {
            throw new IllegalStateException(lockInfo.get("lockedBy") + "가 평가를 완료하여 더 이상 수정할 수 없습니다.");
        }

        // 4. 데이터 저장
        evaluationService.upsertEvaluations(mappingId, params, empId);
    }

    /**
     * 매핑 데이터를 조회하고 자가평가 가능 여부를 통합 검증합니다.
     */
    private EvaluatorMappingDTO validateAndGetMapping(Long mappingId, Long empId) {
        EvaluatorMappingDTO mapping = mappingService.getMappingById(mappingId);

        // 권한 검증: 본인의 매핑인지 확인
        if (!mapping.evaluatorId().equals(empId)) {
            throw new SecurityException("해당 평가에 대한 접근 권한이 없습니다.");
        }

        // 유형 검증: SELF 매핑인지 확인
        if (!RelationType.SELF.getCode().equals(mapping.relationTypeCode())) {
            throw new IllegalArgumentException("자가평가 매핑이 아닙니다.");
        }

        // 기간 검증: 차수가 활성 상태인지 확인
        if (!periodService.isPeriodActive(mapping.periodId())) {
            throw new IllegalStateException("평가 기간이 아니거나 이미 종료되었습니다.");
        }

        return mapping;
    }

    private List<EvaluationElementDTO> filterElementsByType(List<EvaluationElementDTO> elements, EvaluationElementType type) {
        return elements.stream()
                .filter(e -> type.getCode().equals(e.elementTypeCode()))
                .collect(Collectors.toList());
    }
}
