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

    /** 평가자 매핑 수정이 허용되는 유일한 상태 */
    private static final String STATUS_PLANNED = "PLANNED";

    /** 자기 자신을 매핑할 수 없는 관계 유형 목록 */
    private static final String RELATION_MANAGER = "MANAGER";
    private static final String RELATION_SELF = "SELF";
    private static final String RELATION_SUBORDINATE = "SUBORDINATE";
    private static final String RELATION_EXECUTIVE = "EXECUTIVE";

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

        List<EvaluatorMappingDTO> results = new ArrayList<>();
        List<EvaluatorMapping> mappingsToInsert = new ArrayList<>();
        
        for (Long evaluatorId : evaluatorIds) {
            // 본인/타인 관계 검증
            validateSelfMapping(evaluateeId, evaluatorId, relationTypeCode);

            // 부서장 관계 검증 (최적화된 정보 사용)
            if (RELATION_MANAGER.equals(relationTypeCode)) {
                validateManagerRelationStrict(evaluateeId, evaluatorId, actualLeaderId, evaluateeDeptId);
            }

            // 임원 권한 검증
            if (RELATION_EXECUTIVE.equals(relationTypeCode)) {
                validateExecutiveMapping(evaluatorId);
            }

            // 다면 평가자(부서원) 검증
            if (RELATION_SUBORDINATE.equals(relationTypeCode)) {
                validateSubordinateMappingStrict(evaluateeId, evaluatorId, actualLeaderId, evaluateeDeptId);
            }

            // 중복 매핑 검증
            validateDuplicate(periodId, evaluateeId, evaluatorId, relationTypeCode);

            EvaluatorMapping mapping = EvaluatorMapping.builder()
                    .periodId(periodId)
                    .evaluateeId(evaluateeId)
                    .evaluatorId(evaluatorId)
                    .relationTypeCode(relationTypeCode)
                    .build();
            mapping.prePersist();
            mappingsToInsert.add(mapping);
            results.add(enrichDto(mapping));
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
                .filter(e -> "EMPLOYED".equals(e.getStatusCode()))
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
                ? allEmployees.stream().filter(e -> "EMPLOYED".equals(e.getStatusCode())).toList()
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
            if (roles.contains("ROLE_EXECUTIVE")) {
                continue;
            }

            boolean isExcludedFromSelf = roles.contains("ROLE_ADMIN");
            if (!isExcludedFromSelf) {
                addIfAbsent(newMappings, existingKeys, periodId, evaluateeId, evaluateeId, "SELF", currentUserId, now);
            }

            if (emp.getDeptId() != null) {
                Long leaderId = deptLeaderMap.get(emp.getDeptId());
                boolean isLeader = (leaderId != null && leaderId.equals(evaluateeId));

                if (isLeader) {
                    // 2) SUBORDINATE (부서장은 부서원들로부터 다면 평가를 받음)
                    // [Retired Check] 부서장이 퇴사 상태인 경우 다면 평가 대상에서 제외 (자동 스킵)
                    Employee leaderEmp = empMap.get(leaderId);
                    if (leaderEmp != null && "EMPLOYED".equals(leaderEmp.getStatusCode())) {
                        List<Employee> members = deptMembers.getOrDefault(emp.getDeptId(),
                                java.util.Collections.emptyList());
                        for (Employee mem : members) {
                            if (!mem.getEmpId().equals(evaluateeId)) {
                                addIfAbsent(newMappings, existingKeys, periodId, evaluateeId, mem.getEmpId(),
                                        "SUBORDINATE", currentUserId, now);
                            }
                        }
                    }
                } else if (leaderId != null) {
                    // 3) MANAGER (일반 사원은 부서장에게 평가를 받음)
                    // [Retired Check] 부서장이 퇴사 상태인 경우 1차 평가(MANAGER) 매핑 스킵 -> 임원 평가로 일원화
                    Employee leaderEmp = empMap.get(leaderId);
                    if (leaderEmp != null && "EMPLOYED".equals(leaderEmp.getStatusCode())) {
                        addIfAbsent(newMappings, existingKeys, periodId, evaluateeId, leaderId, "MANAGER",
                                currentUserId, now);
                    }
                }

                // 4) EXECUTIVE (모든 사원은 소속 본부의 임원에게 최종 평가를 받음)
                Long rootId = rootDeptCache.get(emp.getDeptId());
                if (rootId != null) {
                    List<Employee> executivesInRoot = deptMembers
                            .getOrDefault(rootId, java.util.Collections.emptyList()).stream()
                            .filter(e -> rolesMap.getOrDefault(e.getEmpId(), java.util.Collections.emptySet())
                                    .contains("ROLE_EXECUTIVE"))
                            .toList();
                    for (Employee exec : executivesInRoot) {
                        if (!exec.getEmpId().equals(evaluateeId)) {
                            addIfAbsent(newMappings, existingKeys, periodId, evaluateeId, exec.getEmpId(), "EXECUTIVE",
                                    currentUserId, now);
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
            if (evaluator != null && !"EMPLOYED".equalsIgnoreCase(evaluator.getStatusCode())) {

                Employee evaluatee = empMap.get(m.getEvaluateeId());
                // 재직 중(EMPLOYED)인 피평가자만 검사 대상 (퇴사자, 승인대기자 등 제외)
                if (evaluatee == null || !"EMPLOYED".equalsIgnoreCase(evaluatee.getStatusCode()))
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
                .filter(e -> "EMPLOYED".equalsIgnoreCase(e.getStatusCode()))

                .toList();

        for (Employee evaluatee : activeEvaluatees) {
            Long empId = evaluatee.getEmpId();
            Set<String> roles = rolesMap.getOrDefault(empId, Collections.emptySet());

            // 임원은 피평가자가 될 수 없으므로 검사 대상에서 제외
            if (roles.contains("ROLE_EXECUTIVE")) {
                continue;
            }

            boolean isExecutiveOrAdmin = roles.contains("ROLE_ADMIN");

            List<EvaluatorMapping> myMappings = mappingsByEvaluatee.getOrDefault(empId, Collections.emptyList());

            String deptName = evaluatee.getDeptId() != null && deptMap.containsKey(evaluatee.getDeptId())
                    ? deptMap.get(evaluatee.getDeptId()).getDeptName()
                    : "알 수 없음";

            if (!isExecutiveOrAdmin) {
                boolean hasSelf = myMappings.stream().anyMatch(m -> "SELF".equals(m.getRelationTypeCode()));
                if (!hasSelf) {
                    anomalies.add(MappingAnomalyDTO.builder()
                            .evaluateeId(empId).evaluateeName(evaluatee.getName()).deptName(deptName)
                            .anomalyType("MISSING_SELF").description("본인 평가 매핑이 누락되었습니다.").severity("ERROR").build());
                }
            }

            if (!isExecutiveOrAdmin) {
                // 부서 미배정 사원 별도 처리
                if (evaluatee.getDeptId() == null) {
                    boolean hasManager = myMappings.stream().anyMatch(m -> "MANAGER".equals(m.getRelationTypeCode()));
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
                        if (leaderEmp == null || !"EMPLOYED".equalsIgnoreCase(leaderEmp.getStatusCode())) {
                            isLeader = false;
                        }

                    }

                    if (!isLeader) {
                        boolean hasManager = myMappings.stream()
                                .anyMatch(m -> "MANAGER".equals(m.getRelationTypeCode()));
                        if (!hasManager) {
                            // 부서장이 퇴사했거나 지정되지 않은 경우(null) 메시지 차별화 및 등급 격하(INFO)
                            boolean isLeaderUnassigned = (leaderId == null);
                            boolean isLeaderRetired = leaderId != null &&
                                    (empMap.get(leaderId) == null
                                            || !"EMPLOYED".equalsIgnoreCase(empMap.get(leaderId).getStatusCode()));

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
                                .filter(m -> "SUBORDINATE".equals(m.getRelationTypeCode())).count();
                        if (subordinateCount == 0) {
                            anomalies.add(MappingAnomalyDTO.builder()
                                    .evaluateeId(empId).evaluateeName(evaluatee.getName()).deptName(deptName)
                                    .anomalyType("MISSING_SUBORDINATE").description("다면 평가자(부서원) 매핑이 0명입니다.")
                                    .severity("WARNING").build());
                        }
                    }
                }
            }

            boolean hasExecutive = myMappings.stream().anyMatch(m -> "EXECUTIVE".equals(m.getRelationTypeCode()));
            if (!hasExecutive && !roles.contains("ROLE_ADMIN")) {
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
        if (roles.stream().noneMatch(role -> "ROLE_EXECUTIVE".equals(role))) {
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
                .anyMatch(e -> "SUBMITTED".equals(e.getConfirmStatusCode()));

        if (isSubmittedByDownstream) {
            result.put("isLocked", true);
            
            // 누구 때문에 잠겼는지 정보 추가
            String lockedBy = evaluations.stream()
                    .filter(e -> "SUBMITTED".equals(e.getConfirmStatusCode()))
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
                    .anyMatch(e -> "SUBMITTED".equals(e.getConfirmStatusCode()));

            result.put(mappingId, isLocked);
        }

        return result;
    }
}
