package com.ees.eval.service.impl;

import com.ees.eval.domain.Employee;
import com.ees.eval.domain.EvaluatorMapping;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.service.EvaluatorMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EvaluatorMappingService의 실제 비즈니스 로직 구현체입니다.
 * 자기평가 검증, 중복 매핑 차단, 일괄 매핑 처리를 수행합니다.
 * 가상 스레드 환경에서 @Transactional로 원자성을 보장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluatorMappingServiceImpl implements EvaluatorMappingService {

    private final EvaluatorMappingMapper mappingMapper;
    private final EmployeeMapper employeeMapper;

    /** 자기 자신을 매핑할 수 없는 관계 유형 목록 */
    private static final String RELATION_SUPERIOR = "SUPERIOR";
    private static final String RELATION_PEER = "PEER";
    private static final String RELATION_SELF = "SELF";
    private static final String RELATION_SUBORDINATE = "SUBORDINATE";

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public EvaluatorMappingDTO getMappingById(Long mappingId) {
        EvaluatorMapping mapping = mappingMapper.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("매핑을 찾을 수 없습니다. mappingId: " + mappingId));
        return enrichDto(mapping);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvaluatorMappingDTO> getMappingsByPeriodIdAndDeptId(Long periodId, Long deptId, Long excludeEmpId) {
        List<EvaluatorMapping> mappings;
        if (deptId == null) {
            // EvaluatorMappingMapper에 findByPeriodId가 원래 있었으므로 이를 활용하거나
            // findByPeriodIdAndDeptId(id, null) 처리
            mappings = mappingMapper.findByPeriodIdAndDeptId(periodId, null);
        } else {
            mappings = mappingMapper.findByPeriodIdAndDeptId(periodId, deptId);
        }
        return mappings.stream()
                .filter(m -> excludeEmpId == null || !m.getEvaluateeId().equals(excludeEmpId))
                .map(this::enrichDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvaluatorMappingDTO> getMyEvaluationTasks(Long periodId, Long evaluatorId) {
        return mappingMapper.findByEvaluatorId(periodId, evaluatorId).stream()
                .map(this::enrichDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvaluatorMappingDTO> getMyEvaluators(Long periodId, Long evaluateeId) {
        return mappingMapper.findByEvaluateeId(periodId, evaluateeId).stream()
                .map(this::enrichDto)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EvaluatorMappingDTO createMapping(EvaluatorMappingDTO mappingDto) {
        validateSelfMapping(mappingDto.evaluateeId(), mappingDto.evaluatorId(), mappingDto.relationTypeCode());
        validateDuplicate(mappingDto.periodId(), mappingDto.evaluateeId(),
                mappingDto.evaluatorId(), mappingDto.relationTypeCode());

        EvaluatorMapping mapping = convertToEntity(mappingDto);
        mapping.prePersist();
        mappingMapper.insert(mapping);

        return enrichDto(mapping);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public List<EvaluatorMappingDTO> createBulkMappings(Long periodId, Long evaluateeId,
            List<Long> evaluatorIds, String relationTypeCode) {
        List<EvaluatorMappingDTO> results = new ArrayList<>();
        for (Long evaluatorId : evaluatorIds) {
            validateSelfMapping(evaluateeId, evaluatorId, relationTypeCode);
            validateDuplicate(periodId, evaluateeId, evaluatorId, relationTypeCode);

            EvaluatorMapping mapping = EvaluatorMapping.builder()
                    .periodId(periodId)
                    .evaluateeId(evaluateeId)
                    .evaluatorId(evaluatorId)
                    .relationTypeCode(relationTypeCode)
                    .build();
            mapping.prePersist();
            mappingMapper.insert(mapping);
            results.add(enrichDto(mapping));
        }
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public int autoGenerateMappings(Long periodId, Long deptId, Long excludeEmpId) {
        // 정책 변경에 따른 기존 SELF 매핑 선제적 정리
        cleanUpInvalidMappings(periodId);

        int count = 0;
        List<Employee> targetEmployees;
        if (deptId == null) {
            targetEmployees = employeeMapper.findAll();
        } else {
            // 부서별 사원 조회 메서드가 필요할 수 있음. 일단 EmployeeMapper.findByDeptId 활용
            targetEmployees = employeeMapper.findByDeptId(deptId);
        }

        for (Employee emp : targetEmployees) {
            // 본인 제외 필터링 (관리자가 부서장인 경우 본인 매핑은 상급자가 수행)
            if (excludeEmpId != null && emp.getEmpId().equals(excludeEmpId)) {
                continue;
            }

            // B. 부서장 평가(MANAGER) 매핑 생성
            if (emp.getDeptId() != null) {
                java.util.Optional<Long> leaderIdOpt = employeeMapper.findDeptLeaderByDeptId(emp.getDeptId());
                if (leaderIdOpt.isPresent()) {
                    Long leaderId = leaderIdOpt.get();
                    if (!emp.getEmpId().equals(leaderId)) {
                        try {
                            validateDuplicate(periodId, emp.getEmpId(), leaderId, "MANAGER");
                            EvaluatorMapping managerMapping = EvaluatorMapping.builder()
                                    .periodId(periodId)
                                    .evaluateeId(emp.getEmpId())
                                    .evaluatorId(leaderId)
                                    .relationTypeCode("MANAGER")
                                    .build();
                            managerMapping.prePersist();
                            mappingMapper.insert(managerMapping);
                            count++;
                        } catch (IllegalStateException e) {
                            log.debug("중복된 MANAGER 매핑 스킵: empId={}, leaderId={}", emp.getEmpId(), leaderId);
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteMapping(Long mappingId) {
        Long currentUserId = 1L;
        int updatedRows = mappingMapper.softDelete(mappingId, currentUserId, LocalDateTime.now());
        if (updatedRows == 0) {
            throw new IllegalArgumentException("삭제 대상 매핑을 찾을 수 없습니다. mappingId: " + mappingId);
        }
    }

    @Override
    @Transactional
    public void cleanUpInvalidMappings(Long periodId) {
        log.info("차수 {} 내 부적절한 매핑(자기 평가 등) 정리 시작", periodId);
        int deleted = mappingMapper.deleteSelfMappingsByPeriod(periodId);
        if (deleted > 0) {
            log.info("차수 {} 내 {}건의 자기 평가 매핑이 정책에 따라 삭제되었습니다.", periodId, deleted);
        }
    }

    private void validateSelfMapping(Long evaluateeId, Long evaluatorId, String relationTypeCode) {
        if (evaluateeId.equals(evaluatorId)) {
            throw new IllegalArgumentException("자기 자신을 평가자로 지정할 수 없습니다. (본인 평가 불가)");
        }
    }

    private void validateDuplicate(Long periodId, Long evaluateeId, Long evaluatorId, String relationTypeCode) {
        int count = mappingMapper.countDuplicate(periodId, evaluateeId, evaluatorId, relationTypeCode);
        if (count > 0) {
            throw new IllegalStateException("동일한 평가 관계가 이미 존재합니다.");
        }
    }

    private EvaluatorMappingDTO enrichDto(EvaluatorMapping mapping) {
        return EvaluatorMappingDTO.builder()
                .mappingId(mapping.getMappingId())
                .periodId(mapping.getPeriodId())
                .evaluateeId(mapping.getEvaluateeId())
                .evaluatorId(mapping.getEvaluatorId())
                .relationTypeCode(mapping.getRelationTypeCode())
                .evaluateeName(mapping.getEvaluateeName() != null ? mapping.getEvaluateeName() : "알 수 없음")
                .evaluatorName(mapping.getEvaluatorName() != null ? mapping.getEvaluatorName() : "알 수 없음")
                .isDeleted(mapping.getIsDeleted())
                .version(mapping.getVersion())
                .createdAt(mapping.getCreatedAt())
                .createdBy(mapping.getCreatedBy())
                .updatedAt(mapping.getUpdatedAt())
                .updatedBy(mapping.getUpdatedBy())
                .build();
    }

    private EvaluatorMapping convertToEntity(EvaluatorMappingDTO dto) {
        EvaluatorMapping mapping = EvaluatorMapping.builder()
                .mappingId(dto.mappingId())
                .periodId(dto.periodId())
                .evaluateeId(dto.evaluateeId())
                .evaluatorId(dto.evaluatorId())
                .relationTypeCode(dto.relationTypeCode())
                .build();
        mapping.setIsDeleted(dto.isDeleted());
        mapping.setVersion(dto.version());
        mapping.setCreatedAt(dto.createdAt());
        mapping.setCreatedBy(dto.createdBy());
        mapping.setUpdatedAt(dto.updatedAt());
        mapping.setUpdatedBy(dto.updatedBy());
        return mapping;
    }
}
