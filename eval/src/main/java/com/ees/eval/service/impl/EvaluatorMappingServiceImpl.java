package com.ees.eval.service.impl;

import com.ees.eval.domain.Employee;
import com.ees.eval.domain.EvaluatorMapping;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.EvaluationPeriodMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.domain.Evaluation;
import com.ees.eval.service.EvaluatorMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.ees.eval.dto.MappingAnomalyDTO;
import com.ees.eval.dto.MultiDimensionalEvalRowDTO;
import com.ees.eval.dto.MyEvaluationPageDTO;
import com.ees.eval.dto.MyEvaluationRowDTO;
import com.ees.eval.dto.enums.MyEvaluationCtaType;
import com.ees.eval.dto.enums.MyEvaluationStatus;
import com.ees.eval.dto.MultiDimensionalEvalPageDTO;
import com.ees.eval.dto.enums.MultiDimensionalEvalStatus;
import com.ees.eval.dto.enums.MultiDimensionalEvalCtaType;
import com.ees.eval.dto.enums.RelationType;
import com.ees.eval.dto.enums.ConfirmStatus;
import com.ees.eval.dto.enums.EvaluationPeriodStatus;
import com.ees.eval.dto.enums.EmployeeStatus;
import com.ees.eval.dto.enums.SystemRole;

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
    private final EvaluationPeriodMapper periodMapper;
    private final EvaluationMapper evaluationMapper;
    private final com.ees.eval.mapper.EvaluationElementMapper elementMapper;

    /** 평가자 매핑 수정이 허용되는 유일한 상태 */
    private static final String STATUS_PLANNED = EvaluationPeriodStatus.PLANNED.getCode();

    /** 자기 자신을 매핑할 수 없는 관계 유형 목록 */
    private static final String RELATION_MANAGER = RelationType.MANAGER.getCode();
    private static final String RELATION_SELF = RelationType.SELF.getCode();
    private static final String RELATION_SUBORDINATE = RelationType.SUBORDINATE.getCode();
    private static final String RELATION_EXECUTIVE = RelationType.EXECUTIVE.getCode();

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
        // 전체 평가 태스크 조회 (필터 없이 null 전달)
        return mappingMapper.findByEvaluatorId(periodId, evaluatorId, null).stream()
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
        // 평가 진행 상태 검증 (PLANNED 상태에서만 수정 가능)
        validatePeriodModifiable(mappingDto.periodId());
        validateSelfMapping(mappingDto.evaluateeId(), mappingDto.evaluatorId(), mappingDto.relationTypeCode());

        if (RELATION_MANAGER.equals(mappingDto.relationTypeCode())) {
            validateManagerRelation(mappingDto.evaluateeId(), mappingDto.evaluatorId());
        }

        if (RELATION_EXECUTIVE.equals(mappingDto.relationTypeCode())) {
            validateExecutiveMapping(mappingDto.evaluatorId());
        }

        if (RELATION_SUBORDINATE.equals(mappingDto.relationTypeCode())) {
            validateSubordinateMapping(mappingDto.evaluateeId(), mappingDto.evaluatorId());
        }

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
        // 평가 진행 상태 검증 (PLANNED 상태에서만 수정 가능)
        validatePeriodModifiable(periodId);

        // [Optimization] 피평가자 부서 및 부서장 정보를 루프 밖에서 1회 조회하여 성능 최적화
        Employee evaluatee = employeeMapper.findById(evaluateeId)
                .orElseThrow(() -> new IllegalArgumentException("피평가자 정보를 찾을 수 없습니다."));
        Long evaluateeDeptId = evaluatee.getDeptId();

        Long actualLeaderId = null;
        if (RELATION_MANAGER.equals(relationTypeCode) || RELATION_SUBORDINATE.equals(relationTypeCode)) {
            actualLeaderId = departmentMapper.findById(evaluateeDeptId)
                    .map(com.ees.eval.domain.Department::getLeaderId)
                    .orElse(null);
        }

        // [New Optimization] N+1 방지를 위해 검증용 데이터를 사전에 일괄 조회
        // 1. 기존 매핑 조회 (중복 검증용)
        List<EvaluatorMapping> existingMappings = new ArrayList<>(mappingMapper.findByEvaluateeId(periodId, evaluateeId));
        
        // 2. 평가자 목록 일괄 조회
        Map<Long, Employee> evaluatorMap = employeeMapper.findByIds(evaluatorIds).stream()
                .collect(Collectors.toMap(Employee::getEmpId, e -> e));
                
        // 3. 임원 권한 검증이 필요한 경우 권한 목록 일괄 조회
        Map<Long, Set<String>> evaluatorRolesMap = Collections.emptyMap();
        if (RELATION_EXECUTIVE.equals(relationTypeCode)) {
            List<Map<String, Object>> rawRoles = employeeMapper.findRoleNamesByEmpIds(evaluatorIds);
            evaluatorRolesMap = rawRoles.stream()
                    .collect(Collectors.groupingBy(
                            m -> {
                                Object empIdObj = m.get("EMP_ID");
                                if (empIdObj == null) empIdObj = m.get("emp_id");
                                return empIdObj instanceof Number ? ((Number) empIdObj).longValue() : Long.valueOf(empIdObj.toString());
                            },
                            Collectors.mapping(
                                    m -> {
                                        Object roleNameObj = m.get("ROLE_NAME");
                                        if (roleNameObj == null) roleNameObj = m.get("role_name");
                                        return String.valueOf(roleNameObj);
                                    },
                                    Collectors.toSet()
                            )
                    ));
        }

        List<EvaluatorMappingDTO> results = new ArrayList<>();
        List<EvaluatorMapping> mappingsToInsert = new ArrayList<>();
        
        for (Long evaluatorId : evaluatorIds) {
            // 본인/타인 관계 검증
            validateSelfMapping(evaluateeId, evaluatorId, relationTypeCode);

            // 부서장 관계 검증 (최적화된 정보 사용)
            if (RELATION_MANAGER.equals(relationTypeCode)) {
                validateManagerRelationStrict(evaluateeId, evaluatorId, actualLeaderId, evaluateeDeptId);
            }

            // 임원 권한 검증 (메모리 맵 사용)
            if (RELATION_EXECUTIVE.equals(relationTypeCode)) {
                Set<String> roles = evaluatorRolesMap.getOrDefault(evaluatorId, Collections.emptySet());
                if (!roles.contains(SystemRole.ROLE_EXECUTIVE.getCode())) {
                    throw new IllegalArgumentException("임원 권한이 없는 사원은 임원 평가자로 지정할 수 없습니다.");
                }
            }

            // 다면 평가자(부서원) 검증 (메모리 맵 사용)
            if (RELATION_SUBORDINATE.equals(relationTypeCode)) {
                if (!evaluateeId.equals(actualLeaderId)) {
                    throw new IllegalArgumentException("피평가자가 부서장이 아닙니다.");
                }
                Employee evaluator = evaluatorMap.get(evaluatorId);
                if (evaluator == null) {
                    throw new IllegalArgumentException("평가자를 찾을 수 없습니다.");
                }
                if (!evaluator.getDeptId().equals(evaluateeDeptId)) {
                    throw new IllegalArgumentException("다면 평가자(부서원)는 동일 부서 소속이어야 합니다.");
                }
            }

            // 중복 매핑 검증 (메모리 리스트 사용)
            boolean isDuplicate = existingMappings.stream()
                    .anyMatch(m -> m.getEvaluatorId().equals(evaluatorId) && m.getRelationTypeCode().equals(relationTypeCode));
            if (isDuplicate) {
                throw new IllegalArgumentException("이미 동일한 평가자가 해당 관계로 지정되어 있습니다.");
            }

            EvaluatorMapping mapping = EvaluatorMapping.builder()
                    .periodId(periodId)
                    .evaluateeId(evaluateeId)
                    .evaluatorId(evaluatorId)
                    .relationTypeCode(relationTypeCode)
                    .build();
            mapping.prePersist();
            mappingsToInsert.add(mapping);
            results.add(enrichDto(mapping));
            
            // 동일 요청 내에서 중복 방지를 위해 리스트 업데이트
            existingMappings.add(mapping);
        }
        
        if (!mappingsToInsert.isEmpty()) {
            mappingMapper.insertBatch(mappingsToInsert);
        }
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public int autoGenerateMappings(Long periodId, Long deptId, Long excludeEmpId) {
        // 평가 진행 상태 검증 (PLANNED 상태에서만 수정 가능)
        validatePeriodModifiable(periodId);
        log.info("평가자 자동 매핑 시작 - periodId: {}, deptId: {}", periodId, deptId);

        // 1. 기초 데이터 로드 (All-in-one Select)
        List<Employee> allEmployees = employeeMapper.findAll();
        List<com.ees.eval.domain.Department> allDepts = departmentMapper.findAll();

        // 사원 맵 및 부서별 사원 그룹화
        Map<Long, List<Employee>> deptMembers = allEmployees.stream()
                .filter(e -> EmployeeStatus.EMPLOYED.getCode().equals(e.getStatusCode()))
                .collect(Collectors.groupingBy(Employee::getDeptId));

        Map<Long, com.ees.eval.domain.Department> deptMap = allDepts.stream()
                .collect(Collectors.toMap(com.ees.eval.domain.Department::getDeptId, d -> d));

        Map<Long, Employee> empMap = allEmployees.stream()
                .collect(Collectors.toMap(Employee::getEmpId, e -> e));

        Map<Long, Long> deptLeaderMap = allDepts.stream()

                .filter(d -> d.getLeaderId() != null)
                .collect(Collectors.toMap(com.ees.eval.domain.Department::getDeptId,
                        com.ees.eval.domain.Department::getLeaderId));

        // 부서별 최상위 부서 캐싱 (EXECUTIVE 매핑용)
        Map<Long, Long> rootDeptCache = new HashMap<>();
        for (com.ees.eval.domain.Department d : allDepts) {
            com.ees.eval.domain.Department curr = d;
            while (curr.getParentDeptId() != null && deptMap.containsKey(curr.getParentDeptId())) {
                curr = deptMap.get(curr.getParentDeptId());
            }
            rootDeptCache.put(d.getDeptId(), curr.getDeptId());
        }

        // 권한 일괄 조회 (N+1 방지)
        List<Long> empIds = allEmployees.stream().map(Employee::getEmpId).toList();
        List<Map<String, Object>> rawRoles = employeeMapper.findRoleNamesByEmpIds(empIds);
        Map<Long, Set<String>> rolesMap = rawRoles.stream()
                .collect(Collectors.groupingBy(
                        m -> ((Number) m.get("EMP_ID")).longValue(),
                        Collectors.mapping(m -> (String) m.get("ROLE_NAME"), Collectors.toSet())));

        // 기존 매핑 조회 (중복 방지용 캐시)
        Set<String> existingKeys = mappingMapper.findAllByPeriodId(periodId).stream()
                .map(m -> m.getEvaluateeId() + "_" + m.getEvaluatorId() + "_" + m.getRelationTypeCode())
                .collect(Collectors.toCollection(HashSet::new));

        // 2. 매핑 대상 결정
        List<Employee> targets = (deptId == null)
                ? allEmployees.stream().filter(e -> EmployeeStatus.EMPLOYED.getCode().equals(e.getStatusCode())).toList()
                : deptMembers.getOrDefault(deptId, java.util.Collections.emptyList());

        List<EvaluatorMapping> newMappings = new ArrayList<>();
        Long currentUserId = com.ees.eval.util.SecurityUtil.getCurrentEmployeeId();
        LocalDateTime now = LocalDateTime.now();

        for (Employee emp : targets) {
            if (excludeEmpId != null && emp.getEmpId().equals(excludeEmpId))
                continue;
            Long evaluateeId = emp.getEmpId();

            // 1) SELF (자기평가)
            Set<String> roles = rolesMap.getOrDefault(evaluateeId, java.util.Collections.emptySet());

            // 임원은 피평가자가 될 수 없으므로 매핑 대상에서 제외
            if (roles.contains(SystemRole.ROLE_EXECUTIVE.getCode())) {
                continue;
            }

            boolean isExcludedFromSelf = roles.contains(SystemRole.ROLE_ADMIN.getCode());
            if (!isExcludedFromSelf) {
                addIfAbsent(newMappings, existingKeys, periodId, evaluateeId, evaluateeId, RELATION_SELF, currentUserId, now);
            }

            if (emp.getDeptId() != null) {
                Long leaderId = deptLeaderMap.get(emp.getDeptId());
                boolean isLeader = (leaderId != null && leaderId.equals(evaluateeId));

                if (isLeader) {
                    // 2) SUBORDINATE (부서장은 부서원들로부터 다면 평가를 받음)
                    // [Retired Check] 부서장이 퇴사 상태인 경우 다면 평가 대상에서 제외 (자동 스킵)
                    Employee leaderEmp = empMap.get(leaderId);
                    if (leaderEmp != null && EmployeeStatus.EMPLOYED.getCode().equals(leaderEmp.getStatusCode())) {
                        List<Employee> members = deptMembers.getOrDefault(emp.getDeptId(),
                                java.util.Collections.emptyList());
                        for (Employee mem : members) {
                            if (!mem.getEmpId().equals(evaluateeId)) {
                                addIfAbsent(newMappings, existingKeys, periodId, evaluateeId, mem.getEmpId(),
                                        RELATION_SUBORDINATE, currentUserId, now);
                            }
                        }
                    }
                } else if (leaderId != null) {
                    // 3) MANAGER (일반 사원은 부서장에게 평가를 받음)
                    // [Retired Check] 부서장이 퇴사 상태인 경우 1차 평가(MANAGER) 매핑 스킵 -> 임원 평가로 일원화
                    Employee leaderEmp = empMap.get(leaderId);
                    if (leaderEmp != null && EmployeeStatus.EMPLOYED.getCode().equals(leaderEmp.getStatusCode())) {
                        addIfAbsent(newMappings, existingKeys, periodId, evaluateeId, leaderId, RELATION_MANAGER,
                                currentUserId, now);
                    }
                }

                // 4) EXECUTIVE (모든 사원은 소속 본부의 임원에게 최종 평가를 받음)
                // [본부장-하위 팀장 매핑 자동화]
                // 피평가자가 부서장(isLeader)이고, 상위 부서(본부)가 존재하며, 그 상위 부서의 리더(본부장)가 지정되어 있고 재직 중인 경우:
                // 그 본부장을 2차 평가자(EXECUTIVE)로 매핑합니다.
                // 그렇지 않은 경우(일반 사원이거나 상위 리더가 없는 경우)에는 기존 로직대로 소속 본부의 임원(ROLE_EXECUTIVE)을 최종 평가자로 매핑합니다.
                boolean isExecutiveMapped = false;
                if (isLeader) {
                    com.ees.eval.domain.Department myDept = deptMap.get(emp.getDeptId());
                    if (myDept != null && myDept.getParentDeptId() != null) {
                        com.ees.eval.domain.Department parentDept = deptMap.get(myDept.getParentDeptId());
                        if (parentDept != null && parentDept.getLeaderId() != null) {
                            Employee parentLeader = empMap.get(parentDept.getLeaderId());
                            if (parentLeader != null && EmployeeStatus.EMPLOYED.getCode().equals(parentLeader.getStatusCode())) {
                                if (!parentLeader.getEmpId().equals(evaluateeId)) {
                                    addIfAbsent(newMappings, existingKeys, periodId, evaluateeId, parentLeader.getEmpId(), RELATION_EXECUTIVE,
                                            currentUserId, now);
                                    isExecutiveMapped = true;
                                    log.info("부서장 계층 평가 매핑 추가 - 피평가자: {}({}), 2차 평가자(본부장): {}({})",
                                            emp.getName(), evaluateeId, parentLeader.getName(), parentLeader.getEmpId());
                                }
                            }
                        }
                    }
                }

                if (!isExecutiveMapped) {
                    Long rootId = rootDeptCache.get(emp.getDeptId());
                    if (rootId != null) {
                        List<Employee> executivesInRoot = deptMembers
                                .getOrDefault(rootId, java.util.Collections.emptyList()).stream()
                                .filter(e -> rolesMap.getOrDefault(e.getEmpId(), java.util.Collections.emptySet())
                                        .contains(SystemRole.ROLE_EXECUTIVE.getCode()))
                                .toList();
                        for (Employee exec : executivesInRoot) {
                            if (!exec.getEmpId().equals(evaluateeId)) {
                                addIfAbsent(newMappings, existingKeys, periodId, evaluateeId, exec.getEmpId(), RELATION_EXECUTIVE,
                                        currentUserId, now);
                            }
                        }
                    }
                }
            }
        }

        // 3. 일괄 삽입 (Chunking 처리로 DB 파라미터 제한 방지)
        if (!newMappings.isEmpty()) {
            int batchSize = 500;
            for (int i = 0; i < newMappings.size(); i += batchSize) {
                int end = Math.min(i + batchSize, newMappings.size());
                mappingMapper.insertBatch(newMappings.subList(i, end));
            }
        }

        log.info("평가자 자동 매핑 완료 - 생성된 매핑 수: {}", newMappings.size());
        return newMappings.size();
    }

    /**
     * 중복을 체크하여 매핑 리스트에 추가합니다.
     */
    private void addIfAbsent(List<EvaluatorMapping> list, Set<String> keys, Long periodId,
            Long evaluateeId, Long evaluatorId, String type,
            Long userId, LocalDateTime now) {
        String key = evaluateeId + "_" + evaluatorId + "_" + type;
        if (!keys.contains(key)) {
            EvaluatorMapping m = EvaluatorMapping.builder()
                    .periodId(periodId)
                    .evaluateeId(evaluateeId)
                    .evaluatorId(evaluatorId)
                    .relationTypeCode(type)
                    .build();
            m.setIsDeleted("n");
            m.setVersion(0);
            m.setCreatedAt(now);
            m.setCreatedBy(userId);
            m.setUpdatedAt(now);
            m.setUpdatedBy(userId);
            list.add(m);
            keys.add(key); // 현재 Batch 내 중복 방지
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteMapping(Long mappingId) {
        // 매핑 조회 후 해당 차수의 진행 상태 검증
        EvaluatorMapping target = mappingMapper.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("삭제 대상 매핑을 찾을 수 없습니다. mappingId: " + mappingId));
        validatePeriodModifiable(target.getPeriodId());
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
        // 평가 진행 상태 검증 (PLANNED 상태에서만 수정 가능)
        validatePeriodModifiable(mapping.getPeriodId());

        validateSelfMapping(mapping.getEvaluateeId(), evaluatorId, mapping.getRelationTypeCode());

        if (RELATION_MANAGER.equals(mapping.getRelationTypeCode())) {
            validateManagerRelation(mapping.getEvaluateeId(), evaluatorId);
        }

        if (RELATION_EXECUTIVE.equals(mapping.getRelationTypeCode())) {
            validateExecutiveMapping(evaluatorId);
        }

        if (RELATION_SUBORDINATE.equals(mapping.getRelationTypeCode())) {
            validateSubordinateMapping(mapping.getEvaluateeId(), evaluatorId);
        }

        validateDuplicate(mapping.getPeriodId(), mapping.getEvaluateeId(), evaluatorId, mapping.getRelationTypeCode());

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
        // 평가 진행 상태 검증 (PLANNED 상태에서만 수정 가능)
        validatePeriodModifiable(periodId);
        mappingMapper.deleteByPeriodAndDept(periodId, deptId, com.ees.eval.util.SecurityUtil.getCurrentEmployeeId(),
                LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MappingAnomalyDTO> checkMappingIntegrity(Long periodId) {
        log.info("평가 매핑 정합성 검사 시작 - periodId: {}", periodId);

        List<Employee> allEmployees = employeeMapper.findAll();
        List<com.ees.eval.domain.Department> allDepts = departmentMapper.findAll();
        List<EvaluatorMapping> allMappings = mappingMapper.findAllByPeriodId(periodId);

        // 권한 정보 일괄 조회 (N+1 방지)
        Map<Long, Set<String>> rolesMap = new java.util.HashMap<>();
        if (!allEmployees.isEmpty()) {
            List<Long> empIds = allEmployees.stream().map(Employee::getEmpId).toList();
            List<Map<String, Object>> rawRoles = employeeMapper.findRoleNamesByEmpIds(empIds);

            for (Map<String, Object> row : rawRoles) {
                Object empIdObj = row.get("EMP_ID");
                if (empIdObj == null)
                    empIdObj = row.get("emp_id");

                Object roleNameObj = row.get("ROLE_NAME");
                if (roleNameObj == null)
                    roleNameObj = row.get("role_name");

                if (empIdObj != null && roleNameObj != null) {
                    try {
                        Long empId = Long.valueOf(empIdObj.toString());
                        String roleName = roleNameObj.toString();
                        rolesMap.computeIfAbsent(empId, k -> new java.util.HashSet<>()).add(roleName);
                    } catch (Exception e) {
                        log.warn("권한 정보 파싱 오류: row={}, error={}", row, e.getMessage());
                    }
                }
            }
        }

        Map<Long, Employee> empMap = allEmployees.stream()
                .collect(Collectors.toMap(Employee::getEmpId, e -> e));

        Map<Long, com.ees.eval.domain.Department> deptMap = allDepts.stream()
                .collect(Collectors.toMap(com.ees.eval.domain.Department::getDeptId, d -> d));

        Map<Long, List<EvaluatorMapping>> mappingsByEvaluatee = allMappings.stream()
                .collect(Collectors.groupingBy(EvaluatorMapping::getEvaluateeId));

        List<MappingAnomalyDTO> anomalies = new ArrayList<>();

        // 퇴사자 평가자 중복 보고 방지용 키 세트
        Set<String> retiredAnomalyKeys = new HashSet<>();

        for (EvaluatorMapping m : allMappings) {
            Employee evaluator = empMap.get(m.getEvaluatorId());
            if (evaluator != null && !EmployeeStatus.EMPLOYED.getCode().equalsIgnoreCase(evaluator.getStatusCode())) {

                Employee evaluatee = empMap.get(m.getEvaluateeId());
                // 재직 중(EMPLOYED)인 피평가자만 검사 대상 (퇴사자, 승인대기자 등 제외)
                if (evaluatee == null || !EmployeeStatus.EMPLOYED.getCode().equalsIgnoreCase(evaluatee.getStatusCode()))
                    continue;

                // 동일 피평가자-평가자-관계유형 조합의 중복 anomaly 방지
                String dedupKey = m.getEvaluateeId() + "_" + m.getEvaluatorId() + "_" + m.getRelationTypeCode();
                if (retiredAnomalyKeys.contains(dedupKey))
                    continue;
                retiredAnomalyKeys.add(dedupKey);

                String deptName = evaluatee.getDeptId() != null && deptMap.containsKey(evaluatee.getDeptId())
                        ? deptMap.get(evaluatee.getDeptId()).getDeptName()
                        : "알 수 없음";

                anomalies.add(MappingAnomalyDTO.builder()
                        .evaluateeId(evaluatee.getEmpId())
                        .evaluateeName(evaluatee.getName())
                        .deptName(deptName)
                        .anomalyType("RETIRED_EVALUATOR")
                        .description(String.format("매핑된 %s 평가자(%s)가 퇴사 상태입니다.", m.getRelationTypeCode(),
                                evaluator.getName()))
                        .severity("INFO") // [Requirement] 퇴사자로 인한 이슈는 블록 방지를 위해 INFO로 격하
                        .build());
            }
        }

        List<Employee> activeEvaluatees = allEmployees.stream()
                .filter(e -> EmployeeStatus.EMPLOYED.getCode().equalsIgnoreCase(e.getStatusCode()))

                .toList();

        for (Employee evaluatee : activeEvaluatees) {
            Long empId = evaluatee.getEmpId();
            Set<String> roles = rolesMap.getOrDefault(empId, Collections.emptySet());

            // 임원은 피평가자가 될 수 없으므로 검사 대상에서 제외
            if (roles.contains(SystemRole.ROLE_EXECUTIVE.getCode())) {
                continue;
            }

            boolean isExecutiveOrAdmin = roles.contains(SystemRole.ROLE_ADMIN.getCode());

            List<EvaluatorMapping> myMappings = mappingsByEvaluatee.getOrDefault(empId, Collections.emptyList());

            String deptName = evaluatee.getDeptId() != null && deptMap.containsKey(evaluatee.getDeptId())
                    ? deptMap.get(evaluatee.getDeptId()).getDeptName()
                    : "알 수 없음";

            if (!isExecutiveOrAdmin) {
                boolean hasSelf = myMappings.stream().anyMatch(m -> RELATION_SELF.equals(m.getRelationTypeCode()));
                if (!hasSelf) {
                    anomalies.add(MappingAnomalyDTO.builder()
                            .evaluateeId(empId).evaluateeName(evaluatee.getName()).deptName(deptName)
                            .anomalyType("MISSING_SELF").description("본인 평가 매핑이 누락되었습니다.").severity("ERROR").build());
                }
            }

            if (!isExecutiveOrAdmin) {
                // 부서 미배정 사원 별도 처리
                if (evaluatee.getDeptId() == null) {
                    boolean hasManager = myMappings.stream().anyMatch(m -> RELATION_MANAGER.equals(m.getRelationTypeCode()));
                    if (!hasManager) {
                        anomalies.add(MappingAnomalyDTO.builder()
                                .evaluateeId(empId).evaluateeName(evaluatee.getName()).deptName(deptName)
                                .anomalyType("MISSING_MANAGER").description("부서가 미배정 상태이며, 1차 평가자(부서장) 매핑이 누락되었습니다.")
                                .severity("INFO").build());
                    }

                } else {
                    // 부서장 판별: leader_id가 재직 중인 사원인지도 함께 검증
                    Long leaderId = deptMap.containsKey(evaluatee.getDeptId())
                            ? deptMap.get(evaluatee.getDeptId()).getLeaderId()
                            : null;
                    boolean isLeader = leaderId != null && empId.equals(leaderId);

                    // 부서장이 퇴사 상태이면 부서장으로 인정하지 않음
                    if (isLeader) {
                        Employee leaderEmp = empMap.get(leaderId);
                        if (leaderEmp == null || !EmployeeStatus.EMPLOYED.getCode().equalsIgnoreCase(leaderEmp.getStatusCode())) {
                            isLeader = false;
                        }

                    }

                    if (!isLeader) {
                        boolean hasManager = myMappings.stream()
                                .anyMatch(m -> RELATION_MANAGER.equals(m.getRelationTypeCode()));
                        if (!hasManager) {
                            // 부서장이 퇴사했거나 지정되지 않은 경우(null) 메시지 차별화 및 등급 격하(INFO)
                            boolean isLeaderUnassigned = (leaderId == null);
                            boolean isLeaderRetired = leaderId != null &&
                                    (empMap.get(leaderId) == null
                                            || !EmployeeStatus.EMPLOYED.getCode().equalsIgnoreCase(empMap.get(leaderId).getStatusCode()));

                            String desc;
                            String severity;

                            if (isLeaderRetired) {
                                desc = "부서장 퇴사 (임원 평가 대체)";
                                severity = "INFO";
                            } else if (isLeaderUnassigned) {
                                desc = "부서장 미지정 (임원 평가 대체)";
                                severity = "INFO";

                            } else {
                                desc = "1차 평가자(부서장) 매핑이 누락되었습니다.";
                                severity = "WARNING";
                            }

                            anomalies.add(MappingAnomalyDTO.builder()
                                    .evaluateeId(empId).evaluateeName(evaluatee.getName()).deptName(deptName)
                                    .anomalyType("MISSING_MANAGER").description(desc).severity(severity).build());
                        }

                    } else {
                        long subordinateCount = myMappings.stream()
                                .filter(m -> RELATION_SUBORDINATE.equals(m.getRelationTypeCode())).count();
                        if (subordinateCount == 0) {
                            anomalies.add(MappingAnomalyDTO.builder()
                                    .evaluateeId(empId).evaluateeName(evaluatee.getName()).deptName(deptName)
                                    .anomalyType("MISSING_SUBORDINATE").description("다면 평가자(부서원) 매핑이 0명입니다.")
                                    .severity("WARNING").build());
                        }
                    }
                }
            }

            boolean hasExecutive = myMappings.stream().anyMatch(m -> RELATION_EXECUTIVE.equals(m.getRelationTypeCode()));
            if (!hasExecutive && !roles.contains(SystemRole.ROLE_ADMIN.getCode())) {
                anomalies.add(MappingAnomalyDTO.builder()
                        .evaluateeId(empId).evaluateeName(evaluatee.getName()).deptName(deptName)
                        .anomalyType("MISSING_EXECUTIVE").description("최종 평가자(임원) 매핑이 누락되었습니다.").severity("ERROR")
                        .build());
            }
        }

        log.info("평가 매핑 정합성 검사 완료 - 발견된 이상 건수: {}", anomalies.size());
        return anomalies;
    }

    private void validateSelfMapping(Long evaluateeId, Long evaluatorId, String relationTypeCode) {
        // 1. 자기 자신인데 SELF가 아닌 경우 차단
        if (evaluateeId.equals(evaluatorId) && !RELATION_SELF.equals(relationTypeCode)) {
            throw new IllegalArgumentException("자기 자신은 '본인' 관계 유형으로만 매핑이 가능합니다. 선택된 관계를 확인해 주세요.");
        }
        // 2. SELF 유형인데 본인이 아닌 경우 차단
        if (RELATION_SELF.equals(relationTypeCode) && !evaluateeId.equals(evaluatorId)) {
            throw new IllegalArgumentException("'본인' 관계 유형은 피평가자와 평가자가 동일해야 합니다.");
        }
    }

    /**
     * 특정 사원을 '부서장'으로 매핑할 때 해당 부서의 공식 리더(Leader)인지 검증합니다.
     */
    private com.ees.eval.domain.Department getEvaluateeDepartment(Long evaluateeId) {
        Employee evaluatee = employeeMapper.findById(evaluateeId)
                .orElseThrow(() -> new IllegalArgumentException("피평가자 정보를 조회할 수 없습니다."));
        return departmentMapper.findById(evaluatee.getDeptId())
                .orElseThrow(() -> new IllegalArgumentException("피평가자의 부서 정보를 찾을 수 없습니다."));
    }

    private void validateManagerRelation(Long evaluateeId, Long evaluatorId) {
        com.ees.eval.domain.Department dept = getEvaluateeDepartment(evaluateeId);
        Long leaderId = dept.getLeaderId();

        validateManagerRelationStrict(evaluateeId, evaluatorId, leaderId, dept.getDeptId());
    }

    private void validateManagerRelationStrict(Long evaluateeId, Long evaluatorId, Long leaderId,
            Long evaluateeDeptId) {
        // 1. 부서 일치 검증 (부서장과 부서원의 소속 부서가 같아야 함)
        Employee evaluator = employeeMapper.findById(evaluatorId)
                .orElseThrow(() -> new IllegalArgumentException("평가자를 찾을 수 없습니다."));
        if (!evaluator.getDeptId().equals(evaluateeDeptId)) {
            throw new IllegalArgumentException("해당 부서 소속이 아닌 사원을 '부서장'으로 매핑할 수 없습니다. (소속 부서가 일치하지 않습니다)");
        }

        // 2. 공식 리더 여부 검증
        if (leaderId == null || !leaderId.equals(evaluatorId)) {
            throw new IllegalArgumentException("해당 부서의 공식 부서장(Leader)만 '부서장' 관계유형으로 매핑할 수 있습니다.");
        }
    }

    private void validateSubordinateMapping(Long evaluateeId, Long evaluatorId) {
        com.ees.eval.domain.Department dept = getEvaluateeDepartment(evaluateeId);
        Long leaderId = dept.getLeaderId();

        validateSubordinateMappingStrict(evaluateeId, evaluatorId, leaderId, dept.getDeptId());
    }

    private void validateSubordinateMappingStrict(Long evaluateeId, Long evaluatorId, Long leaderId,
            Long evaluateeDeptId) {
        // 1. 부서 일치 검증 (동일 부서원끼리만 다면 평가 가능)
        Employee evaluator = employeeMapper.findById(evaluatorId)
                .orElseThrow(() -> new IllegalArgumentException("평가자를 찾을 수 없습니다."));
        if (!evaluator.getDeptId().equals(evaluateeDeptId)) {
            throw new IllegalArgumentException("다면 평가자(부서원)는 피평가자와 동일한 부서 소속이어야 합니다. (소속 부서가 일치하지 않습니다)");
        }

        // 2. 피평가자 자격 검증 (부서장만 다면 평가의 피평가자가 될 수 있음 - 상향 평가 원칙)
        if (leaderId == null || !leaderId.equals(evaluateeId)) {
            throw new IllegalArgumentException("부서장만 다면 평가(상향 평가)의 대상이 될 수 있습니다. 일반 사원은 다면 평가 대상이 아닙니다.");
        }
    }

    private void validateDuplicate(Long periodId, Long evaluateeId, Long evaluatorId, String relationTypeCode) {
        int count = mappingMapper.countDuplicate(periodId, evaluateeId, evaluatorId, relationTypeCode);
        if (count > 0) {
            throw new IllegalStateException("동일한 평가 관계가 이미 존재합니다.");
        }
    }

    /**
     * 평가 차수의 상태가 수정 가능한 상태(PLANNED)인지 검증합니다.
     * 평가가 시작(IN_PROGRESS)되면 평가자 매핑의 생성/수정/삭제를 차단합니다.
     *
     * @param periodId 검증할 평가 차수 ID
     * @throws IllegalStateException 수정 불가능한 상태일 경우
     */
    private void validatePeriodModifiable(Long periodId) {
        com.ees.eval.domain.EvaluationPeriod period = periodMapper.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("평가 차수를 찾을 수 없습니다. periodId: " + periodId));

        if (!STATUS_PLANNED.equalsIgnoreCase(period.getStatusCode())) {
            String statusCode = period.getStatusCode() != null ? period.getStatusCode().trim().toUpperCase() : "";
            String statusDesc = statusCode.contains("PROGRESS") || statusCode.contains("진행") 
                    ? "진행 중(IN_PROGRESS)" : statusCode;
            throw new IllegalStateException(
                    String.format("수정 불가: 현재 평가 차수가 %s 상태이므로 평가자 매핑을 변경할 수 없습니다. 준비(PLANNED) 상태에서만 추가/수정/삭제가 가능합니다.",
                            statusDesc));
        }


    }

    private void validateExecutiveMapping(Long evaluatorId) {
        Employee evaluator = employeeMapper.findById(evaluatorId)
                .orElseThrow(() -> new IllegalArgumentException("평가자를 찾을 수 없습니다."));

        com.ees.eval.domain.Department dept = departmentMapper.findById(evaluator.getDeptId())
                .orElseThrow(() -> new IllegalArgumentException("평가자의 부서를 찾을 수 없습니다."));

        // 1. 부서 검증: 최상위 부서 소속 여부
        if (dept.getParentDeptId() != null) {
            throw new IllegalArgumentException("최상위 부서에 소속된 사원만 임원(EXECUTIVE)으로 지정할 수 있습니다.");
        }

        // 2. 권한 검증: ROLE_EXECUTIVE 보유 여부
        List<String> roles = employeeMapper.findRoleNamesByEmpId(evaluatorId);
        if (roles.stream().noneMatch(role -> SystemRole.ROLE_EXECUTIVE.getCode().equals(role))) {
            throw new IllegalArgumentException("해당 사원은 임원(ROLE_EXECUTIVE) 권한이 없습니다.");
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
                .deptName(mapping.getDeptName())
                .titleName(mapping.getTitleName())
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

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> checkEvaluationLock(Long mappingId) {
        Map<String, Object> result = new HashMap<>();
        result.put("isLocked", false);
        result.put("lockedBy", "");

        EvaluatorMapping current = mappingMapper.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("매핑을 찾을 수 없습니다."));

        String type = current.getRelationTypeCode();
        Long periodId = current.getPeriodId();
        Long evaluateeId = current.getEvaluateeId();

        // 잠금 대상 관계 설정 (내 뒤 단계들)
        List<String> downstreamTypes = new ArrayList<>();
        if (RELATION_SELF.equals(type) || RELATION_SUBORDINATE.equals(type)) {
            downstreamTypes.add(RELATION_MANAGER);
            downstreamTypes.add(RELATION_EXECUTIVE);
        } else if (RELATION_MANAGER.equals(type)) {
            downstreamTypes.add(RELATION_EXECUTIVE);
        }

        if (downstreamTypes.isEmpty()) {
            return result;
        }

        // 해당 피평가자의 모든 매핑 조회
        List<EvaluatorMapping> allMappings = mappingMapper.findByEvaluateeId(periodId, evaluateeId);
        List<Long> mappingIds = allMappings.stream()
                .filter(m -> downstreamTypes.contains(m.getRelationTypeCode()))
                .map(EvaluatorMapping::getMappingId)
                .toList();

        if (mappingIds.isEmpty()) {
            return result;
        }

        // 해당 매핑들의 제출 여부 확인
        List<Evaluation> evaluations = evaluationMapper.findByMappingIds(mappingIds);
        boolean isSubmittedByDownstream = evaluations.stream()
                .anyMatch(e -> ConfirmStatus.SUBMITTED.getCode().equals(e.getConfirmStatusCode()));

        if (isSubmittedByDownstream) {
            result.put("isLocked", true);
            
            // 누구 때문에 잠겼는지 정보 추가
            String lockedBy = evaluations.stream()
                    .filter(e -> ConfirmStatus.SUBMITTED.getCode().equals(e.getConfirmStatusCode()))
                    .findFirst()
                    .map(e -> {
                        EvaluatorMapping m = allMappings.stream()
                                .filter(am -> am.getMappingId().equals(e.getMappingId()))
                                .findFirst().orElse(null);
                        if (m == null) return "상위 평가자";
                        if (RELATION_MANAGER.equals(m.getRelationTypeCode())) return "1차 평가자(부서장)";
                        if (RELATION_EXECUTIVE.equals(m.getRelationTypeCode())) return "최종 평가자(임원)";
                        return "상위 평가자";
                    }).orElse("상위 평가자");
            
            result.put("lockedBy", lockedBy);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     * 사전 조회된 매핑/평가 데이터를 활용하여 DB 호출 없이 잠금 여부를 일괄 확인합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, Boolean> checkEvaluationLockBulk(
            List<Long> mappingIds,
            Map<Long, List<EvaluatorMapping>> allMappingsByEvaluatee,
            Map<Long, List<Evaluation>> evalGroupMap) {

        Map<Long, Boolean> result = new HashMap<>();

        // mappingId → EvaluatorMapping 변환을 위한 맵 구성 (사전 조회 데이터에서 추출)
        Map<Long, EvaluatorMapping> mappingById = allMappingsByEvaluatee.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(EvaluatorMapping::getMappingId, m -> m, (a, b) -> a));

        for (Long mappingId : mappingIds) {
            EvaluatorMapping current = mappingById.get(mappingId);
            if (current == null) {
                result.put(mappingId, false);
                continue;
            }

            String type = current.getRelationTypeCode();

            // 잠금 대상 관계 설정 (내 뒤 단계들)
            List<String> downstreamTypes = new ArrayList<>();
            if (RELATION_SELF.equals(type) || RELATION_SUBORDINATE.equals(type)) {
                downstreamTypes.add(RELATION_MANAGER);
                downstreamTypes.add(RELATION_EXECUTIVE);
            } else if (RELATION_MANAGER.equals(type)) {
                downstreamTypes.add(RELATION_EXECUTIVE);
            }

            if (downstreamTypes.isEmpty()) {
                result.put(mappingId, false);
                continue;
            }

            // 해당 피평가자의 모든 매핑 중 다운스트림 매핑 ID 추출
            List<EvaluatorMapping> evaluateeMappings = allMappingsByEvaluatee.getOrDefault(
                    current.getEvaluateeId(), Collections.emptyList());
            List<Long> downstreamMappingIds = evaluateeMappings.stream()
                    .filter(m -> downstreamTypes.contains(m.getRelationTypeCode()))
                    .map(EvaluatorMapping::getMappingId)
                    .toList();

            if (downstreamMappingIds.isEmpty()) {
                result.put(mappingId, false);
                continue;
            }

            // 다운스트림 매핑의 평가 중 SUBMITTED 여부 확인
            boolean isLocked = downstreamMappingIds.stream()
                    .flatMap(mid -> evalGroupMap.getOrDefault(mid, Collections.emptyList()).stream())
                    .anyMatch(e -> ConfirmStatus.SUBMITTED.getCode().equals(e.getConfirmStatusCode()));

            result.put(mappingId, isLocked);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public com.ees.eval.dto.MultiDimensionalEvalPageDTO getMultiDimensionalTasks(
            Long periodId, Long evaluatorId, Long filterDeptId, String filterStatus, String keyword, int page, int pageSize, boolean isPeriodActive) {
        
        // 1. 내가 평가해야 할 전체 다면평가 태스크 조회 (DB 레벨 필터링 적용)
        List<EvaluatorMapping> myAllTasks = mappingMapper.findByEvaluatorId(periodId, evaluatorId, RELATION_SUBORDINATE);
        
        if (myAllTasks.isEmpty()) {
            return new com.ees.eval.dto.MultiDimensionalEvalPageDTO(Collections.emptyList(), 1, 1, 0, pageSize);
        }

        // 2. 관련 데이터 벌크 조회 (최적화: N+1 방지)
        List<Long> evaluateeIds = myAllTasks.stream().map(EvaluatorMapping::getEvaluateeId).distinct().toList();
        Map<Long, Employee> evaluateeMap = employeeMapper.findByIds(evaluateeIds).stream()
                .collect(Collectors.toMap(Employee::getEmpId, e -> e));
        
        // 해당 피평가자들의 모든 매핑 정보 (잠금 체크 및 자가평가 확인용)
        Map<Long, List<EvaluatorMapping>> allMappingsByEvaluatee = mappingMapper.findByEvaluateeIds(periodId, evaluateeIds).stream()
                .collect(Collectors.groupingBy(EvaluatorMapping::getEvaluateeId));
        
        // 전체 평가 데이터 (제출 여부 확인용)
        List<Long> allMappingIds = allMappingsByEvaluatee.values().stream()
                .flatMap(List::stream)
                .map(EvaluatorMapping::getMappingId)
                .toList();
        
        Map<Long, List<Evaluation>> evalGroupMapTmp = new HashMap<>();
        if (!allMappingIds.isEmpty()) {
            List<Evaluation> allEvals = evaluationMapper.findByMappingIds(allMappingIds);
            evalGroupMapTmp = allEvals.stream().collect(Collectors.groupingBy(Evaluation::getMappingId));
        }
        final Map<Long, List<Evaluation>> evalGroupMap = evalGroupMapTmp;

        // [N+1 수정] 평가요소 캐시 (periodId_deptId 기준) — 루프 밖 선언
        Map<String, List<com.ees.eval.domain.EvaluationElement>> multiElementCache = new HashMap<>();

        // 3. 필터링 및 상태 계산
        List<com.ees.eval.dto.MultiDimensionalEvalRowDTO> rowList = new ArrayList<>();
        for (EvaluatorMapping task : myAllTasks) {
            Employee evaluatee = evaluateeMap.get(task.getEvaluateeId());
            if (evaluatee == null) continue;

            // (A) 기본 필터링 (부서, 키워드)
            if (filterDeptId != null && !filterDeptId.equals(evaluatee.getDeptId())) continue;
            if (keyword != null && !keyword.isEmpty() && 
                !(evaluatee.getName().contains(keyword) || evaluatee.getEmpId().toString().contains(keyword))) {
                continue;
            }

            // (B) 상태 계산 로직
            List<Evaluation> myEvals = evalGroupMap.getOrDefault(task.getMappingId(), Collections.emptyList());
            boolean isSubmitted = myEvals.stream().anyMatch(e -> ConfirmStatus.SUBMITTED.getCode().equals(e.getConfirmStatusCode()));
            boolean isInProgress = !isSubmitted && !myEvals.isEmpty();

            MultiDimensionalEvalStatus statusType = isSubmitted ? MultiDimensionalEvalStatus.SUBMITTED :
                                                   isInProgress ? MultiDimensionalEvalStatus.IN_PROGRESS :
                                                   MultiDimensionalEvalStatus.WAITING;

            // (C) 자가평가 제출 여부 확인
            List<EvaluatorMapping> peerMappings = allMappingsByEvaluatee.getOrDefault(task.getEvaluateeId(), Collections.emptyList());
            Long selfMappingId = peerMappings.stream()
                    .filter(m -> RELATION_SELF.equals(m.getRelationTypeCode()))
                    .map(EvaluatorMapping::getMappingId)
                    .findFirst().orElse(null);
            
            boolean selfSubmitted = selfMappingId != null && evalGroupMap.getOrDefault(selfMappingId, Collections.emptyList()).stream()
                    .anyMatch(e -> ConfirmStatus.SUBMITTED.getCode().equals(e.getConfirmStatusCode()));

            // (D) 잠금 상태 (상위 평가자가 제출했는지)
            boolean isLocked = peerMappings.stream()
                    .filter(m -> RELATION_MANAGER.equals(m.getRelationTypeCode()) || RELATION_EXECUTIVE.equals(m.getRelationTypeCode()))
                    .anyMatch(m -> evalGroupMap.getOrDefault(m.getMappingId(), Collections.emptyList()).stream()
                            .anyMatch(e -> ConfirmStatus.SUBMITTED.getCode().equals(e.getConfirmStatusCode())));

            MultiDimensionalEvalCtaType ctaType;
            if (!isPeriodActive) {
                // 평가 기간 종료 시: 이전 데이터를 볼 수 있도록 '조회' 모드로 통일
                ctaType = MultiDimensionalEvalCtaType.VIEW;
            } else if (isLocked) {
                ctaType = MultiDimensionalEvalCtaType.LOCKED;
            } else if (!selfSubmitted) {
                ctaType = MultiDimensionalEvalCtaType.WAITING_SELF;
            } else {
                // 기간 중이고 잠기지 않았으며 자가평가가 완료되었다면 제출 여부와 관계없이 '수정' 가능(EDIT)
                // (이미 제출된 경우 템플릿에서 '수정' 텍스트로 자동 전환됨)
                ctaType = MultiDimensionalEvalCtaType.EDIT;
            }

            // (E) 상태 필터링
            if (filterStatus != null && !filterStatus.isEmpty() && !statusType.name().equals(filterStatus)) continue;

            // (F) 점수 계산 (환산 점수)
            java.math.BigDecimal totalScore = java.math.BigDecimal.ZERO;
            if (isSubmitted) {
                // [N+1 수정] 평가요소 캐시 활용 (periodId_deptId 당 1회 조회)
                String elemCacheKey = task.getPeriodId() + "_" + (evaluatee.getDeptId() != null ? evaluatee.getDeptId() : -1L);
                List<com.ees.eval.domain.EvaluationElement> elements = multiElementCache.computeIfAbsent(elemCacheKey, k -> {
                    List<com.ees.eval.domain.EvaluationElement> el = elementMapper.findByPeriodId(task.getPeriodId(), evaluatee.getDeptId());
                    return el.isEmpty() ? elementMapper.findByPeriodId(task.getPeriodId(), null) : el;
                });
                
                Map<Long, com.ees.eval.domain.EvaluationElement> elementMap = elements.stream()
                        .collect(Collectors.toMap(com.ees.eval.domain.EvaluationElement::getElementId, e -> e, (a, b) -> a));
                
                java.math.BigDecimal weightedSum = java.math.BigDecimal.ZERO;
                java.math.BigDecimal totalWeight = java.math.BigDecimal.ZERO;
                
                for (Evaluation e : myEvals) {
                    com.ees.eval.domain.EvaluationElement el = elementMap.get(e.getElementId());
                    if (el != null && e.getScore() != null) {
                        java.math.BigDecimal maxScore = el.getMaxScore();
                        if (maxScore.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            java.math.BigDecimal normalized = java.math.BigDecimal.valueOf(e.getScore())
                                    .divide(maxScore, 10, java.math.RoundingMode.HALF_UP)
                                    .multiply(el.getWeight());
                            weightedSum = weightedSum.add(normalized);
                            totalWeight = totalWeight.add(el.getWeight());
                        }
                    }
                }
                if (totalWeight.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    totalScore = weightedSum.divide(totalWeight, 10, java.math.RoundingMode.HALF_UP)
                            .multiply(java.math.BigDecimal.valueOf(100))
                            .setScale(1, java.math.RoundingMode.HALF_UP);
                }
            }

            rowList.add(com.ees.eval.dto.MultiDimensionalEvalRowDTO.builder()
                    .mappingId(task.getMappingId())
                    .evaluateeId(task.getEvaluateeId())
                    .empId(evaluatee.getEmpId().toString())
                    .name(evaluatee.getName())
                    .deptId(evaluatee.getDeptId())
                    .deptName(evaluatee.getDeptName())
                    .titleName(evaluatee.getPositionName() != null ? evaluatee.getPositionName() : "부서장")
                    .relationName("부서원→부서장") 
                    .periodName(task.getPeriodName())
                    .statusType(statusType)
                    .displayStatus(statusType.getDescription())
                    .ctaType(ctaType)
                    .displayCta(ctaType.getDescription())
                    .canWrite(ctaType == MultiDimensionalEvalCtaType.EDIT)
                    .canView(ctaType == MultiDimensionalEvalCtaType.VIEW || ctaType == MultiDimensionalEvalCtaType.LOCKED)
                    .score(isSubmitted ? totalScore : null)
                    .build());
        }

        // 4. 정렬 (단일 차수면 부서/이름순, 전체 차수면 SQL의 최신순 유지 또는 기간/부서/이름순)
        if (periodId != null) {
            rowList.sort(Comparator.comparing(com.ees.eval.dto.MultiDimensionalEvalRowDTO::deptName, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(com.ees.eval.dto.MultiDimensionalEvalRowDTO::name));
        } else {
            // 전체 차수 통합일 경우: SQL에서 이미 기간 역순으로 정렬되어 오지만, 
            // 자바에서 한 번 더 기간(내림차순) -> 부서(오름차순) -> 이름(오름차순)으로 안정적인 정렬 수행
            rowList.sort(Comparator.comparing(com.ees.eval.dto.MultiDimensionalEvalRowDTO::periodName, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(com.ees.eval.dto.MultiDimensionalEvalRowDTO::deptName, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(com.ees.eval.dto.MultiDimensionalEvalRowDTO::name));
        }

        // 5. 페이징 처리
        int totalCount = rowList.size();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        if (totalPages == 0) totalPages = 1;
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int startIndex = (currentPage - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalCount);

        List<com.ees.eval.dto.MultiDimensionalEvalRowDTO> pagedList = rowList.subList(startIndex, endIndex);

        return new com.ees.eval.dto.MultiDimensionalEvalPageDTO(pagedList, currentPage, totalPages, totalCount, pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public MyEvaluationPageDTO getMyEvaluationDashboardTasks(
            Long evaluatorId, Long periodId, String filterStatus, String keyword, int page, int pageSize) {
        
        // 1. 나의 자가평가(SELF) 태스크 전체 조회
        // periodId가 null이면 전체 차수에서 나의 SELF 매핑을 가져옴
        List<EvaluatorMapping> myTasks = mappingMapper.findByEvaluatorId(periodId, evaluatorId, RELATION_SELF);
        
        if (myTasks.isEmpty()) {
            return new MyEvaluationPageDTO(Collections.emptyList(), 1, 1, 0, pageSize);
        }

        // 2. 관련 데이터 벌크 조회 (N+1 방지)
        List<Long> mappingIds = myTasks.stream().map(EvaluatorMapping::getMappingId).toList();
        List<Evaluation> allEvals = evaluationMapper.findByMappingIds(mappingIds);
        Map<Long, List<Evaluation>> evalGroupMap = allEvals.stream()
                .collect(Collectors.groupingBy(Evaluation::getMappingId));

        // [N+1 수정] 잠금 체크용 사전 벌크 조회
        // SELF 태스크의 피평가자(=본인)에 대한 다운스트림 매핑(MANAGER/EXECUTIVE) 조회
        List<Long> selfEvaluateeIds = myTasks.stream()
                .map(EvaluatorMapping::getEvaluateeId).distinct().collect(Collectors.toList());
        Map<String, List<EvaluatorMapping>> peerMappingsByPeriodAndEvaluatee = new HashMap<>();
        Map<Long, List<Evaluation>> fullEvalGroupMap = new HashMap<>(evalGroupMap);
        if (!selfEvaluateeIds.isEmpty()) {
            List<EvaluatorMapping> allRelated = mappingMapper.findByEvaluateeIds(null, selfEvaluateeIds);
            for (EvaluatorMapping m : allRelated) {
                peerMappingsByPeriodAndEvaluatee
                        .computeIfAbsent(m.getPeriodId() + "_" + m.getEvaluateeId(), k -> new ArrayList<>())
                        .add(m);
            }
            List<Long> additionalIds = allRelated.stream()
                    .map(EvaluatorMapping::getMappingId)
                    .filter(id -> !fullEvalGroupMap.containsKey(id))
                    .distinct().collect(Collectors.toList());
            if (!additionalIds.isEmpty()) {
                evaluationMapper.findByMappingIds(additionalIds).forEach(
                        e -> fullEvalGroupMap.computeIfAbsent(e.getMappingId(), k -> new ArrayList<>()).add(e));
            }
        }

        // [N+1 수정] 사원 정보 및 평가요소 루프 밖에서 1회 조회
        Employee selfEmp = employeeMapper.findById(evaluatorId).orElse(null);
        Long selfDeptId = (selfEmp != null) ? selfEmp.getDeptId() : null;
        Map<String, List<com.ees.eval.domain.EvaluationElement>> elementCache = new HashMap<>();

        // 3. 필터링 및 상태 계산
        List<MyEvaluationRowDTO> rowList = new ArrayList<>();
        for (EvaluatorMapping task : myTasks) {
            
            // (A) 기본 필터링 (키워드: 차수명 검색 가능하도록)
            if (keyword != null && !keyword.isEmpty() && 
                !(task.getPeriodName() != null && task.getPeriodName().contains(keyword))) {
                continue;
            }

            // (B) 상태 계산 로직
            List<Evaluation> myEvals = evalGroupMap.getOrDefault(task.getMappingId(), Collections.emptyList());
            boolean isSubmitted = myEvals.stream().anyMatch(e -> ConfirmStatus.SUBMITTED.getCode().equals(e.getConfirmStatusCode()));
            boolean isInProgress = !isSubmitted && !myEvals.isEmpty();

            MyEvaluationStatus statusType = isSubmitted ? MyEvaluationStatus.SUBMITTED :
                                           isInProgress ? MyEvaluationStatus.IN_PROGRESS :
                                           MyEvaluationStatus.WAITING;

            // (C) 잠금 상태 — 사전 조회 데이터로 in-memory 판단 (N+1 제거)
            List<EvaluatorMapping> peerMappings = peerMappingsByPeriodAndEvaluatee
                    .getOrDefault(task.getPeriodId() + "_" + task.getEvaluateeId(), Collections.emptyList());
            boolean isLocked = peerMappings.stream()
                    .filter(m -> RELATION_MANAGER.equals(m.getRelationTypeCode())
                            || RELATION_EXECUTIVE.equals(m.getRelationTypeCode()))
                    .anyMatch(m -> fullEvalGroupMap.getOrDefault(m.getMappingId(), Collections.emptyList())
                            .stream().anyMatch(e -> ConfirmStatus.SUBMITTED.getCode().equals(e.getConfirmStatusCode())));

            MyEvaluationCtaType ctaType;
            if (isLocked) {
                ctaType = MyEvaluationCtaType.LOCKED;
            } else {
                // 제출 여부와 관계없이 잠기지 않았다면 '수정' 가능 상태(EDIT)로 표시
                // (이미 제출된 경우 템플릿에서 '수정' 텍스트로 자동 전환됨)
                ctaType = MyEvaluationCtaType.EDIT;
            }

            // (D) 상태 필터링
            if (filterStatus != null && !filterStatus.isEmpty() && !statusType.name().equals(filterStatus)) continue;

            // (E) 점수 계산 (자가평가 환산 점수)
            java.math.BigDecimal totalScore = null;
            if (isSubmitted) {
                // [N+1 수정] 루프 밖 사전 조회 사원 정보 + 평가요소 캐시 활용
                String elemKey = task.getPeriodId() + "_" + (selfDeptId != null ? selfDeptId : -1L);
                List<com.ees.eval.domain.EvaluationElement> elements = elementCache.computeIfAbsent(elemKey, k -> {
                    List<com.ees.eval.domain.EvaluationElement> el =
                            elementMapper.findByPeriodId(task.getPeriodId(), selfDeptId);
                    return el.isEmpty() ? elementMapper.findByPeriodId(task.getPeriodId(), null) : el;
                });

                Map<Long, com.ees.eval.domain.EvaluationElement> elementMap = elements.stream()
                        .collect(Collectors.toMap(com.ees.eval.domain.EvaluationElement::getElementId, e -> e, (a, b) -> a));

                java.math.BigDecimal weightedSum = java.math.BigDecimal.ZERO;
                java.math.BigDecimal totalWeight = java.math.BigDecimal.ZERO;

                for (Evaluation e : myEvals) {
                    com.ees.eval.domain.EvaluationElement el = elementMap.get(e.getElementId());
                    if (el != null && e.getScore() != null) {
                        java.math.BigDecimal maxScore = el.getMaxScore();
                        if (maxScore.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            java.math.BigDecimal normalized = java.math.BigDecimal.valueOf(e.getScore())
                                    .divide(maxScore, 10, java.math.RoundingMode.HALF_UP)
                                    .multiply(el.getWeight());
                            weightedSum = weightedSum.add(normalized);
                            totalWeight = totalWeight.add(el.getWeight());
                        }
                    }
                }
                if (totalWeight.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    totalScore = weightedSum.divide(totalWeight, 10, java.math.RoundingMode.HALF_UP)
                            .multiply(java.math.BigDecimal.valueOf(100))
                            .setScale(1, java.math.RoundingMode.HALF_UP);
                }
            }

            rowList.add(MyEvaluationRowDTO.builder()
                    .mappingId(task.getMappingId())
                    .periodId(task.getPeriodId())
                    .periodName(task.getPeriodName())
                    .periodYear(task.getPeriodYear() != null ? task.getPeriodYear().toString() : "")
                    .empId(task.getEvaluateeId()) // 본인 사번
                    .name(task.getEvaluateeName()) // 본인 이름
                    .deptName(task.getDeptName())
                    .titleName(task.getTitleName())
                    .statusType(statusType)
                    .displayStatus(statusType.getDescription())
                    .ctaType(ctaType)
                    .displayCta(ctaType.getDescription())
                    .isLocked(isLocked)
                    .isSubmitted(isSubmitted)
                    .score(totalScore)
                    .build());
        }

        // 4. 정렬 (최신 차수순)
        rowList.sort(Comparator.comparing(MyEvaluationRowDTO::periodYear, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MyEvaluationRowDTO::periodId, Comparator.reverseOrder()));

        // 5. 페이징 처리
        int totalCount = rowList.size();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        if (totalPages == 0) totalPages = 1;
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int startIndex = (currentPage - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalCount);

        List<MyEvaluationRowDTO> pagedList = rowList.subList(startIndex, endIndex);

        return new MyEvaluationPageDTO(pagedList, currentPage, totalPages, totalCount, pageSize);
    }

    /**
     * 어드민용: 전체 자가평가 대시보드 태스크 조회 (evaluator_id 필터 없음)
     */
    @Override
    @Transactional(readOnly = true)
    public MyEvaluationPageDTO getAdminMyEvaluationDashboardTasks(
            Long periodId, String filterStatus, String keyword, int page, int pageSize) {

        // 1. 전체 SELF 매핑 조회 (evaluator_id 필터 없음)
        List<EvaluatorMapping> allSelfTasks = mappingMapper.findAllByPeriodIdAndRelationType(periodId, RELATION_SELF);

        if (allSelfTasks.isEmpty()) {
            return new MyEvaluationPageDTO(Collections.emptyList(), 1, 1, 0, pageSize);
        }

        // 2. 관련 데이터 벌크 조회 (N+1 방지)
        List<Long> mappingIds = allSelfTasks.stream().map(EvaluatorMapping::getMappingId).toList();
        List<Evaluation> allEvals = evaluationMapper.findByMappingIds(mappingIds);
        Map<Long, List<Evaluation>> evalGroupMap = allEvals.stream()
                .collect(Collectors.groupingBy(Evaluation::getMappingId));

        // 피평가자들의 부서 정보를 알아내기 위한 사원 벌크 조회
        List<Long> evaluateeIds = allSelfTasks.stream().map(EvaluatorMapping::getEvaluateeId).distinct().toList();
        Map<Long, Employee> employeeMap = employeeMapper.findByIds(evaluateeIds).stream()
                .collect(Collectors.toMap(Employee::getEmpId, e -> e, (a, b) -> a));

        // 평가요소 캐싱 맵
        Map<String, List<com.ees.eval.domain.EvaluationElement>> elementCache = new HashMap<>();

        // 3. 필터링 및 상태 계산
        List<MyEvaluationRowDTO> rowList = new ArrayList<>();
        for (EvaluatorMapping task : allSelfTasks) {

            // (A) 키워드 필터 (사원명, 사번, 차수명 검색)
            if (keyword != null && !keyword.isEmpty()) {
                boolean match = (task.getEvaluateeName() != null && task.getEvaluateeName().contains(keyword))
                        || (task.getEvaluateeId() != null && task.getEvaluateeId().toString().contains(keyword))
                        || (task.getPeriodName() != null && task.getPeriodName().contains(keyword));
                if (!match) continue;
            }

            // (B) 상태 계산
            List<Evaluation> myEvals = evalGroupMap.getOrDefault(task.getMappingId(), Collections.emptyList());
            boolean isSubmitted = myEvals.stream().anyMatch(e -> ConfirmStatus.SUBMITTED.getCode().equals(e.getConfirmStatusCode()));
            boolean isInProgress = !isSubmitted && !myEvals.isEmpty();

            MyEvaluationStatus statusType = isSubmitted ? MyEvaluationStatus.SUBMITTED :
                                           isInProgress ? MyEvaluationStatus.IN_PROGRESS :
                                           MyEvaluationStatus.WAITING;

            // (C) 상태 필터링
            if (filterStatus != null && !filterStatus.isEmpty() && !statusType.name().equals(filterStatus)) continue;

            // (D) 어드민은 항상 읽기 전용 → VIEW 타입
            MyEvaluationCtaType ctaType = MyEvaluationCtaType.VIEW;

            // (E) 점수 계산 (자가평가 환산 점수)
            java.math.BigDecimal totalScore = null;
            if (isSubmitted) {
                Employee emp = employeeMap.get(task.getEvaluateeId());
                Long deptId = (emp != null) ? emp.getDeptId() : null;

                String elemKey = task.getPeriodId() + "_" + (deptId != null ? deptId : -1L);
                List<com.ees.eval.domain.EvaluationElement> elements = elementCache.computeIfAbsent(elemKey, k -> {
                    List<com.ees.eval.domain.EvaluationElement> el =
                            elementMapper.findByPeriodId(task.getPeriodId(), deptId);
                    return el.isEmpty() ? elementMapper.findByPeriodId(task.getPeriodId(), null) : el;
                });

                Map<Long, com.ees.eval.domain.EvaluationElement> elementMap = elements.stream()
                        .collect(Collectors.toMap(com.ees.eval.domain.EvaluationElement::getElementId, e -> e, (a, b) -> a));

                java.math.BigDecimal weightedSum = java.math.BigDecimal.ZERO;
                java.math.BigDecimal totalWeight = java.math.BigDecimal.ZERO;

                for (Evaluation e : myEvals) {
                    com.ees.eval.domain.EvaluationElement el = elementMap.get(e.getElementId());
                    if (el != null && e.getScore() != null) {
                        java.math.BigDecimal maxScore = el.getMaxScore();
                        if (maxScore.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            java.math.BigDecimal normalized = java.math.BigDecimal.valueOf(e.getScore())
                                    .divide(maxScore, 10, java.math.RoundingMode.HALF_UP)
                                    .multiply(el.getWeight());
                            weightedSum = weightedSum.add(normalized);
                            totalWeight = totalWeight.add(el.getWeight());
                        }
                    }
                }
                if (totalWeight.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    totalScore = weightedSum.divide(totalWeight, 10, java.math.RoundingMode.HALF_UP)
                            .multiply(java.math.BigDecimal.valueOf(100))
                            .setScale(1, java.math.RoundingMode.HALF_UP);
                }
            }

            rowList.add(MyEvaluationRowDTO.builder()
                    .mappingId(task.getMappingId())
                    .periodId(task.getPeriodId())
                    .periodName(task.getPeriodName())
                    .periodYear(task.getPeriodYear() != null ? task.getPeriodYear().toString() : "")
                    .empId(task.getEvaluateeId())
                    .name(task.getEvaluateeName())
                    .deptName(task.getDeptName())
                    .titleName(task.getTitleName())
                    .statusType(statusType)
                    .displayStatus(statusType.getDescription())
                    .ctaType(ctaType)
                    .displayCta(ctaType.getDescription())
                    .isLocked(false)
                    .isSubmitted(isSubmitted)
                    .score(totalScore)
                    .build());
        }

        // 4. 정렬 (부서→이름순)
        rowList.sort(Comparator.comparing(MyEvaluationRowDTO::deptName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MyEvaluationRowDTO::name, Comparator.nullsLast(Comparator.naturalOrder())));

        // 5. 페이징
        int totalCount = rowList.size();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        if (totalPages == 0) totalPages = 1;
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int startIndex = (currentPage - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalCount);

        return new MyEvaluationPageDTO(rowList.subList(startIndex, endIndex), currentPage, totalPages, totalCount, pageSize);
    }

    /**
     * 어드민용: 전체 다면평가 태스크 조회 (evaluator_id 필터 없음)
     */
    @Override
    @Transactional(readOnly = true)
    public com.ees.eval.dto.MultiDimensionalEvalPageDTO getAdminMultiDimensionalTasks(
            Long periodId, Long filterDeptId, String filterStatus, String keyword, int page, int pageSize, boolean isPeriodActive) {

        // 1. 전체 SUBORDINATE 매핑 조회 (evaluator_id 필터 없음)
        List<EvaluatorMapping> allSubTasks = mappingMapper.findAllByPeriodIdAndRelationType(periodId, RELATION_SUBORDINATE);

        if (allSubTasks.isEmpty()) {
            return new com.ees.eval.dto.MultiDimensionalEvalPageDTO(Collections.emptyList(), 1, 1, 0, pageSize);
        }

        // 2. 벌크 조회
        List<Long> evaluateeIds = allSubTasks.stream().map(EvaluatorMapping::getEvaluateeId).distinct().toList();
        Map<Long, Employee> evaluateeMap = employeeMapper.findByIds(evaluateeIds).stream()
                .collect(Collectors.toMap(Employee::getEmpId, e -> e, (a, b) -> a));

        List<Long> allMappingIds = allSubTasks.stream().map(EvaluatorMapping::getMappingId).toList();
        Map<Long, List<Evaluation>> evalGroupMap = new HashMap<>();
        if (!allMappingIds.isEmpty()) {
            evalGroupMap = evaluationMapper.findByMappingIds(allMappingIds).stream()
                    .collect(Collectors.groupingBy(Evaluation::getMappingId));
        }

        // 3. 필터링 및 상태 계산
        List<com.ees.eval.dto.MultiDimensionalEvalRowDTO> rowList = new ArrayList<>();
        for (EvaluatorMapping task : allSubTasks) {
            Employee evaluatee = evaluateeMap.get(task.getEvaluateeId());
            if (evaluatee == null) continue;

            if (filterDeptId != null && !filterDeptId.equals(evaluatee.getDeptId())) continue;
            if (keyword != null && !keyword.isEmpty() &&
                !(evaluatee.getName().contains(keyword) || evaluatee.getEmpId().toString().contains(keyword))) {
                continue;
            }

            List<Evaluation> myEvals = evalGroupMap.getOrDefault(task.getMappingId(), Collections.emptyList());
            boolean isSubmitted = myEvals.stream().anyMatch(e -> ConfirmStatus.SUBMITTED.getCode().equals(e.getConfirmStatusCode()));
            boolean isInProgress = !isSubmitted && !myEvals.isEmpty();

            MultiDimensionalEvalStatus statusType = isSubmitted ? MultiDimensionalEvalStatus.SUBMITTED :
                                                   isInProgress ? MultiDimensionalEvalStatus.IN_PROGRESS :
                                                   MultiDimensionalEvalStatus.WAITING;

            if (filterStatus != null && !filterStatus.isEmpty() && !statusType.name().equals(filterStatus)) continue;

            // 어드민은 항상 읽기 전용
            MultiDimensionalEvalCtaType ctaType = MultiDimensionalEvalCtaType.VIEW;

            // 평가자명 포함
            String evaluatorName = task.getEvaluatorName() != null ? task.getEvaluatorName() : "알 수 없음";

            rowList.add(com.ees.eval.dto.MultiDimensionalEvalRowDTO.builder()
                    .mappingId(task.getMappingId())
                    .evaluateeId(task.getEvaluateeId())
                    .empId(evaluatee.getEmpId().toString())
                    .name(evaluatee.getName())
                    .deptId(evaluatee.getDeptId())
                    .deptName(evaluatee.getDeptName())
                    .titleName(evaluatee.getPositionName() != null ? evaluatee.getPositionName() : "부서장")
                    .relationName(evaluatorName + "→" + evaluatee.getName())
                    .periodName(task.getPeriodName())
                    .statusType(statusType)
                    .displayStatus(statusType.getDescription())
                    .ctaType(ctaType)
                    .displayCta(ctaType.getDescription())
                    .canWrite(false)
                    .canView(true)
                    .score(null)
                    .build());
        }

        // 4. 정렬 (부서→피평가자명→평가자)
        rowList.sort(Comparator.comparing(com.ees.eval.dto.MultiDimensionalEvalRowDTO::deptName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(com.ees.eval.dto.MultiDimensionalEvalRowDTO::name));

        // 5. 페이징
        int totalCount = rowList.size();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        if (totalPages == 0) totalPages = 1;
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int startIndex = (currentPage - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalCount);

        return new com.ees.eval.dto.MultiDimensionalEvalPageDTO(rowList.subList(startIndex, endIndex), currentPage, totalPages, totalCount, pageSize);
    }

    /**
     * 어드민용: 전체 성과/역량 평가 태스크 조회 (evaluator_id 필터 없음)
     * MANAGER + EXECUTIVE 관계의 모든 매핑을 반환합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvaluatorMappingDTO> getAllPerformanceTasks(Long periodId) {
        // MANAGER 매핑 조회
        List<EvaluatorMapping> managerTasks = mappingMapper.findAllByPeriodIdAndRelationType(periodId, RELATION_MANAGER);
        // EXECUTIVE 매핑 조회
        List<EvaluatorMapping> executiveTasks = mappingMapper.findAllByPeriodIdAndRelationType(periodId, RELATION_EXECUTIVE);

        List<EvaluatorMapping> allTasks = new ArrayList<>(managerTasks);
        allTasks.addAll(executiveTasks);

        return allTasks.stream()
                .map(this::enrichDto)
                .collect(Collectors.toList());
    }
}
