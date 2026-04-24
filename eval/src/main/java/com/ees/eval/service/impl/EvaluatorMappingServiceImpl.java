package com.ees.eval.service.impl;

import com.ees.eval.domain.Employee;
import com.ees.eval.domain.EvaluatorMapping;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.mapper.DepartmentMapper;
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
    private final DepartmentMapper departmentMapper;

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
    public List<EvaluatorMappingDTO> getMappingsByPeriodIdAndDeptId(Long periodId, Long deptId, String searchName) {
        List<EvaluatorMapping> mappings = mappingMapper.findByPeriodIdAndDeptId(periodId, deptId, searchName);
        return mappings.stream()
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
        
        if ("EXECUTIVE".equals(mappingDto.relationTypeCode())) {
            validateExecutiveMapping(mappingDto.evaluatorId());
        }

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

            if ("EXECUTIVE".equals(relationTypeCode)) {
                validateExecutiveMapping(evaluatorId);
            }

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
        int count = 0;
        List<Employee> targetEmployees;
        if (deptId == null) {
            targetEmployees = employeeMapper.findAll();
        } else {
            targetEmployees = employeeMapper.findByDeptId(deptId);
        }

        // 최상위 부서장(임원진) 목록 확보
        // 부서 계층 구조 파악을 위한 전체 부서 맵 캐싱
        List<com.ees.eval.domain.Department> allDepts = departmentMapper.findAll();
        java.util.Map<Long, com.ees.eval.domain.Department> deptMap = new java.util.HashMap<>();
        for (com.ees.eval.domain.Department d : allDepts) {
            deptMap.put(d.getDeptId(), d);
        }

        for (Employee emp : targetEmployees) {
            // 퇴사/휴직자 제외
            if (!"EMPLOYED".equals(emp.getStatusCode())) {
                continue;
            }
            if (excludeEmpId != null && emp.getEmpId().equals(excludeEmpId)) {
                continue;
            }

            Long evaluateeId = emp.getEmpId();

            // 1. 본인 평가(SELF) 매핑 생성
            // 임원(ROLE_EXECUTIVE), 시스템 관리자(ROLE_ADMIN) 역할의 사원은 자가 평가 대상에서 제외
            List<String> empRoles = employeeMapper.findRoleNamesByEmpId(evaluateeId);
            boolean isExcludedFromSelf = empRoles.stream()
                    .anyMatch(role -> "ROLE_EXECUTIVE".equals(role) || "ROLE_ADMIN".equals(role));
            if (!isExcludedFromSelf) {
                count += safeInsertMapping(periodId, evaluateeId, evaluateeId, "SELF");
            }
            boolean isLeader = false;
            if (emp.getDeptId() != null) {
                java.util.Optional<Long> leaderIdOpt = employeeMapper.findDeptLeaderByDeptId(emp.getDeptId());
                if (leaderIdOpt.isPresent() && leaderIdOpt.get().equals(evaluateeId)) {
                    isLeader = true;
                }

                if (isLeader) {
                    // 2. 부서장인 경우 -> 소속 부서원 전원(SUBORDINATE) 다면 평가
                    List<Employee> subordinates = employeeMapper.findByDeptId(emp.getDeptId());
                    for (Employee sub : subordinates) {
                        if ("EMPLOYED".equals(sub.getStatusCode()) && !sub.getEmpId().equals(evaluateeId)) {
                            count += safeInsertMapping(periodId, evaluateeId, sub.getEmpId(), "SUBORDINATE");
                        }
                    }
                } else if (leaderIdOpt.isPresent()) {
                    // 3. 일반 사원인 경우 -> 부서장(MANAGER) 매핑
                    count += safeInsertMapping(periodId, evaluateeId, leaderIdOpt.get(), "MANAGER");
                }
                
                // 4. 최종 평가자(EXECUTIVE) 매핑 생성
                // 사원이 속한 부서의 최상위 부서(임원급 부서)를 찾아서 해당 부서의 '임원(ROLE_EXECUTIVE)'을 최종 평가자로 지정
                com.ees.eval.domain.Department currentDept = deptMap.get(emp.getDeptId());
                Long rootDeptId = null;
                while (currentDept != null) {
                    if (currentDept.getParentDeptId() == null) {
                        rootDeptId = currentDept.getDeptId();
                        break;
                    }
                    currentDept = deptMap.get(currentDept.getParentDeptId());
                }

                if (rootDeptId != null) {
                    List<Employee> executives = employeeMapper.findByDeptIdAndRoleName(rootDeptId, "ROLE_EXECUTIVE");
                    for (Employee exec : executives) {
                        if (!exec.getEmpId().equals(evaluateeId)) {
                            count += safeInsertMapping(periodId, evaluateeId, exec.getEmpId(), "EXECUTIVE");
                        }
                    }
                }
            }
        }
        return count;
    }

    private int safeInsertMapping(Long periodId, Long evaluateeId, Long evaluatorId, String relationTypeCode) {
        if (evaluateeId.equals(evaluatorId) && !"SELF".equals(relationTypeCode)) {
            return 0; // 본인 평가(SELF)가 아닌데 평가자와 피평가자가 같으면 불가
        }
        int count = mappingMapper.countDuplicate(periodId, evaluateeId, evaluatorId, relationTypeCode);
        if (count == 0) {
            EvaluatorMapping mapping = EvaluatorMapping.builder()
                    .periodId(periodId)
                    .evaluateeId(evaluateeId)
                    .evaluatorId(evaluatorId)
                    .relationTypeCode(relationTypeCode)
                    .build();
            mapping.prePersist();
            mappingMapper.insert(mapping);
            return 1;
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteMapping(Long mappingId) {
        Long currentUserId = com.ees.eval.util.SecurityUtil.getCurrentEmployeeId();
        int updatedRows = mappingMapper.softDelete(mappingId, currentUserId, LocalDateTime.now());
        if (updatedRows == 0) {
            throw new IllegalArgumentException("삭제 대상 매핑을 찾을 수 없습니다. mappingId: " + mappingId);
        }
    }

    @Override
    @Transactional
    public EvaluatorMappingDTO updateMapping(Long mappingId, Long evaluatorId) {
        EvaluatorMapping mapping = mappingMapper.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("매핑을 찾을 수 없습니다. mappingId: " + mappingId));
        
        validateSelfMapping(mapping.getEvaluateeId(), evaluatorId, mapping.getRelationTypeCode());
        validateDuplicate(mapping.getPeriodId(), mapping.getEvaluateeId(), evaluatorId, mapping.getRelationTypeCode());
        
        if ("EXECUTIVE".equals(mapping.getRelationTypeCode())) {
            validateExecutiveMapping(evaluatorId);
        }

        mapping.setEvaluatorId(evaluatorId);
        mapping.preUpdate();
        mapping.setUpdatedBy(com.ees.eval.util.SecurityUtil.getCurrentEmployeeId());
        mapping.setUpdatedAt(LocalDateTime.now());
        
        int updated = mappingMapper.update(mapping);
        if (updated == 0) {
            throw new IllegalStateException("업데이트 중 동시성 충돌이 발생했습니다.");
        }
        
        return enrichDto(mappingMapper.findById(mappingId).get());
    }

    @Override
    @Transactional
    public void initializeMappingsByDept(Long periodId, Long deptId) {
        mappingMapper.deleteByPeriodAndDept(periodId, deptId, com.ees.eval.util.SecurityUtil.getCurrentEmployeeId(), LocalDateTime.now());
    }

    private void validateSelfMapping(Long evaluateeId, Long evaluatorId, String relationTypeCode) {
        if (evaluateeId.equals(evaluatorId) && !"SELF".equals(relationTypeCode)) {
            throw new IllegalArgumentException("자기 자신을 평가자로 지정할 수 없습니다. (본인 평가 불가)");
        }
    }

    private void validateDuplicate(Long periodId, Long evaluateeId, Long evaluatorId, String relationTypeCode) {
        int count = mappingMapper.countDuplicate(periodId, evaluateeId, evaluatorId, relationTypeCode);
        if (count > 0) {
            throw new IllegalStateException("동일한 평가 관계가 이미 존재합니다.");
        }
    }

    private void validateExecutiveMapping(Long evaluatorId) {
        Employee evaluator = employeeMapper.findById(evaluatorId)
                .orElseThrow(() -> new IllegalArgumentException("평가자를 찾을 수 없습니다."));
        
        com.ees.eval.domain.Department dept = departmentMapper.findById(evaluator.getDeptId())
                .orElseThrow(() -> new IllegalArgumentException("평가자의 부서를 찾을 수 없습니다."));
                
        if (dept.getParentDeptId() != null) {
            throw new IllegalArgumentException("최상위 부서에 소속된 사원만 임원(EXECUTIVE)으로 지정할 수 있습니다.");
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
