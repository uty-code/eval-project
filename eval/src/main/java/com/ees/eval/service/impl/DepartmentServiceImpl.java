package com.ees.eval.service.impl;

import com.ees.eval.domain.Department;
import com.ees.eval.domain.Employee;
import com.ees.eval.domain.Role;
import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.RoleMapper;
import com.ees.eval.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * DepartmentService 인터페이스의 실제 비즈니스 로직 구현체입니다.
 * 계층형 부서 트리 관리, 소속 사원 조회, 부서 삭제 시 안전 검증,
 * 리더(부서장) 지정/해제 및 권한 자동 동기화 등을 수행합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;
    private final RoleMapper roleMapper;
    private final CacheManager cacheManager;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public DepartmentDTO getDepartmentById(Long deptId) {
        // 1. 부서 엔티티 조회
        Department dept = departmentMapper.findById(deptId)
                .orElseThrow(() -> new IllegalArgumentException("부서를 찾을 수 없습니다. deptId: " + deptId));

        // 2. 상위 부서명 셀프 JOIN 조회
        String parentDeptName = departmentMapper.findParentDeptName(deptId);

        // 3. 리더(부서장) 사원명 JOIN 조회
        String leaderName = departmentMapper.findLeaderName(deptId);

        // 4. 소속 사원 인원수 카운트 조회
        int employeeCount = departmentMapper.countEmployeesByDeptId(deptId);

        return convertToDto(dept, parentDeptName, leaderName, employeeCount);
    }

    /**
     * {@inheritDoc}
     * N+1 쿼리 방지용 단순 부서 목록 조회 (Select Box 전용)
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "departments-simple", key = "'all'")
    public List<DepartmentDTO> getSimpleAllDepartments() {
        return departmentMapper.findAll().stream()
                .map(dept -> convertToDto(dept, null, null, 0, false))
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "departments", key = "'all'")
    public List<DepartmentDTO> getAllDepartments() {
        // 1. 전체 부서 조회
        List<Department> departments = departmentMapper.findAll();
        
        // 2. 부서 추가 정보 및 DTO 변환 (N+1 문제 해결)
        List<DepartmentDTO> allDepts = convertToDtoListWithDetails(departments);

        // 4. 부모-자식 트리 맵 구성 (parentDeptId 기준 그룹화)
        Map<Long, List<DepartmentDTO>> childrenMap = allDepts.stream()
                .filter(d -> d.parentDeptId() != null)
                .collect(Collectors.groupingBy(DepartmentDTO::parentDeptId));

        // 5. 최상위(Root) 부서 목록 추출 후 ID 순 정렬
        List<DepartmentDTO> roots = allDepts.stream()
                .filter(d -> d.parentDeptId() == null)
                .sorted(Comparator.comparing(DepartmentDTO::deptId))
                .toList();

        // 6. DFS로 트리 순회하여 한 줄로 펼침(Flatten)
        List<DepartmentDTO> sortedDepts = new ArrayList<>();
        for (DepartmentDTO root : roots) {
            buildTreeList(root, childrenMap, sortedDepts, 0);
        }

        return sortedDepts;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public long countActiveDepartments() {
        return departmentMapper.countActiveDepartments();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "departments", 
               key = "'all'", 
               condition = "(#searchKeyword == null || #searchKeyword.trim().isEmpty()) && (#searchStatus == null || #searchStatus.trim().isEmpty())")
    public List<DepartmentDTO> searchDepartments(String searchKeyword, String searchStatus) {
        boolean isSearch = (searchKeyword != null && !searchKeyword.trim().isEmpty())
                || (searchStatus != null && !searchStatus.trim().isEmpty());

        if (!isSearch) {
            return getAllDepartments();
        }

        List<Department> departments = departmentMapper.findAllWithConditions(searchKeyword, searchStatus);
        
        return convertToDtoListWithDetails(departments).stream()
                .map(dto -> dto.toBuilder().treeDepth(0).build())
                .collect(Collectors.toList());
    }

    /**
     * DFS 방식으로 부서를 재귀 순회하여 리스트에 순차적으로 담습니다.
     * depth 파라미터를 통해 트리의 깊이를 재계산합니다.
     */
    private void buildTreeList(DepartmentDTO node, Map<Long, List<DepartmentDTO>> childrenMap,
            List<DepartmentDTO> result, int depth) {
        
        // 트리의 깊이(depth)를 설정한 복제본 객체를 추가
        DepartmentDTO nodeWithDepth = node.toBuilder().treeDepth(depth).build();
        result.add(nodeWithDepth);

        List<DepartmentDTO> children = childrenMap.getOrDefault(node.deptId(), Collections.emptyList());
        // 하위 부서도 식별자 순으로 정렬 후 깊이를 1 증가시켜 재귀 탐색
        children.stream()
                .sorted(Comparator.comparing(DepartmentDTO::deptId))
                .forEach(child -> buildTreeList(child, childrenMap, result, depth + 1));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<DepartmentDTO> getRootDepartments() {
        List<Department> departments = departmentMapper.findRootDepartments();
        return convertToDtoListWithDetails(departments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<DepartmentDTO> getChildDepartments(Long parentDeptId) {
        List<Department> departments = departmentMapper.findByParentDeptId(parentDeptId);
        return convertToDtoListWithDetails(departments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDTO> getEmployeesByDeptId(Long deptId) {
        // 1. 해당 부서 존재 여부 확인
        departmentMapper.findById(deptId)
                .orElseThrow(() -> new IllegalArgumentException("부서를 찾을 수 없습니다. deptId: " + deptId));

        // 2. 부서 소속 사원 목록 조회
        List<Employee> employees = employeeMapper.findByDeptId(deptId);
        if (employees.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. N+1 방지: 사원들의 권한 목록 일괄 조회
        List<Long> empIds = employees.stream().map(Employee::getEmpId).collect(Collectors.toList());
        List<Map<String, Object>> roleMaps = employeeMapper.findRoleNamesByEmpIds(empIds);

        Map<Long, List<String>> empRolesMap = roleMaps.stream()
                .collect(Collectors.groupingBy(
                        row -> ((Number) row.get("EMP_ID")).longValue(),
                        Collectors.mapping(row -> (String) row.get("ROLE_NAME"), Collectors.toList())));

        // 4. DTO 변환
        return employees.stream()
                .map(emp -> {
                    List<String> roleNames = empRolesMap.getOrDefault(emp.getEmpId(), Collections.emptyList());
                    return convertEmployeeToDto(emp, roleNames);
                })
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @CacheEvict(value = {"departments", "departments-simple"}, allEntries = true)
    public DepartmentDTO createDepartment(DepartmentDTO departmentDto) {
        // 엔티티로 변환 후 감사 필드 및 초기값 설정
        Department dept = convertToEntity(departmentDto);
        dept.prePersist();

        // 데이터베이스 삽입 수행
        departmentMapper.insert(dept);

        return getDepartmentById(dept.getDeptId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @CacheEvict(value = {"departments", "departments-simple"}, allEntries = true)
    public DepartmentDTO updateDepartment(DepartmentDTO departmentDto) {
        // 1. 기존 부서 정보 조회 (리더 변경 확인 및 검증용)
        Department existingDept = departmentMapper.findById(departmentDto.deptId())
                .orElseThrow(() -> new IllegalArgumentException("수정 대상 부서를 찾을 수 없습니다. deptId: " + departmentDto.deptId()));

        Long oldLeaderId = existingDept.getLeaderId();
        Long newLeaderId = departmentDto.leaderId();

        // 2. 리더가 변경되었다면 권한 동기화 로직 수행
        if (newLeaderId == null ? oldLeaderId != null : !newLeaderId.equals(oldLeaderId)) {
            log.info("부서 {}의 리더 변경 감지: {} -> {}", existingDept.getDeptId(), oldLeaderId, newLeaderId);

            // 기존 리더 권한 회수 (다른 부서 리더가 아닌 경우에만)
            if (oldLeaderId != null) {
                revokeManagerRoleIfNotLeaderElsewhere(oldLeaderId, existingDept.getDeptId());
            }

            // 새 리더 지정 시 검증 및 권한 부여
            if (newLeaderId != null) {
                // 부서 소속 및 상태 검증 (assignLeader 로직 재사용)
                Employee emp = employeeMapper.findById(newLeaderId)
                        .orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다. empId: " + newLeaderId));
                if (!emp.getDeptId().equals(existingDept.getDeptId())) {
                    throw new IllegalArgumentException("해당 부서 소속이 아닌 사원은 리더로 지정할 수 없습니다.");
                }
                if (!"EMPLOYED".equals(emp.getStatusCode())) {
                    throw new IllegalArgumentException("재직 중인 사원만 리더로 지정할 수 있습니다.");
                }
                
                // 새 리더 권한 부여
                grantManagerRole(newLeaderId);
            }
        }

        // 3. 엔티티 변환 및 수정 일시 갱신
        Department dept = convertToEntity(departmentDto);
        dept.preUpdate();

        // 4. 낙관적 락 체크를 포함한 업데이트 수행
        int updatedRows = departmentMapper.update(dept);
        if (updatedRows == 0) {
            throw new EesOptimisticLockException("부서 정보가 다른 사용자에 의해 변경되었거나 수정 충돌이 발생했습니다.");
        }

        return getDepartmentById(dept.getDeptId());
    }

    /**
     * {@inheritDoc}
     * 소속 사원이나 하위 부서가 존재하면 삭제를 거부하여 데이터 무결성을 보장합니다.
     */
    @Override
    @Transactional
    @CacheEvict(value = {"departments", "departments-simple"}, allEntries = true)
    public void deleteDepartment(Long deptId) {
        // 1. 부서 존재 여부 확인
        departmentMapper.findById(deptId)
                .orElseThrow(() -> new IllegalArgumentException("삭제 대상 부서를 찾을 수 없습니다. deptId: " + deptId));

        // 2. 소속 사원 존재 여부 확인 (사원이 있으면 삭제 불가)
        int empCount = departmentMapper.countEmployeesByDeptId(deptId);
        if (empCount > 0) {
            throw new IllegalStateException(
                    "소속 사원이 " + empCount + "명 존재하는 부서는 삭제할 수 없습니다. " +
                            "사원을 다른 부서로 이동한 후 다시 시도해 주세요.");
        }

        // 3. 하위 부서 존재 여부 확인 (하위 부서가 있으면 삭제 불가)
        List<Department> children = departmentMapper.findByParentDeptId(deptId);
        if (!children.isEmpty()) {
            throw new IllegalStateException(
                    "하위 부서가 " + children.size() + "개 존재하는 부서는 삭제할 수 없습니다. " +
                            "하위 부서를 먼저 정리한 후 다시 시도해 주세요.");
        }

        // 4. 안전 검증 통과 → 논리적 삭제 수행
        Long currentUserId = com.ees.eval.util.SecurityUtil.getCurrentEmployeeId();
        departmentMapper.softDelete(deptId, currentUserId, LocalDateTime.now());
    }

    /**
     * {@inheritDoc}
     * 현재 is_active 상태를 조회한 후 반대 값으로 전환합니다.
     * y → n (사용중 → 미사용중)
     * n → y (미사용중 → 사용중)
     */
    @Override
    @Transactional
    @CacheEvict(value = {"departments", "departments-simple"}, allEntries = true)
    public void toggleDepartmentStatus(Long deptId) {
        // 1. 대상 부서 존재 여부 확인
        Department dept = departmentMapper.findById(deptId)
                .orElseThrow(() -> new IllegalArgumentException("부서를 찾을 수 없습니다. deptId: " + deptId));

        // 2. 현재 상태와 반대 값으로 전환 (y -> n, n -> y)
        String currentStatus = dept.getIsActive();
        String newStatus = "y".equals(currentStatus) ? "n" : "y";

        // 3. 상태 업데이트 수행
        Long currentUserId = com.ees.eval.util.SecurityUtil.getCurrentEmployeeId();
        departmentMapper.updateActiveStatus(deptId, newStatus, currentUserId, LocalDateTime.now());
    }

    /**
     * {@inheritDoc}
     * 리더 지정 시 기존 리더의 부서장 권한을 회수하고, 새 리더에게 권한을 부여합니다.
     * empId가 null인 경우 리더 해제(removeLeader)로 위임합니다.
     */
    @Override
    @Transactional
    @CacheEvict(value = {"departments", "departments-simple"}, allEntries = true)
    public void assignLeader(Long deptId, Long empId) {
        // NULL이 들어오면 리더 해제 처리
        if (empId == null) {
            removeLeader(deptId);
            return;
        }

        // 1. 부서 존재 여부 확인
        Department dept = departmentMapper.findById(deptId)
                .orElseThrow(() -> new IllegalArgumentException("부서를 찾을 수 없습니다. deptId: " + deptId));

        // 2. 대상 사원 존재 여부 및 해당 부서 소속 확인
        Employee emp = employeeMapper.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다. empId: " + empId));
        if (!emp.getDeptId().equals(deptId)) {
            throw new IllegalArgumentException("해당 부서 소속이 아닌 사원은 리더로 지정할 수 없습니다.");
        }

        // 3. 재직 중인 사원인지 확인
        if (!"EMPLOYED".equals(emp.getStatusCode())) {
            throw new IllegalArgumentException("재직 중인 사원만 리더로 지정할 수 있습니다.");
        }

        // 4. 이미 같은 리더라면 아무 작업도 하지 않음
        if (empId.equals(dept.getLeaderId())) {
            log.info("부서 {}의 리더가 이미 사원 {}입니다. 변경 사항 없음.", deptId, empId);
            return;
        }

        // 5. 기존 리더가 있으면 권한 회수 (다른 부서 리더가 아닌 경우에만)
        if (dept.getLeaderId() != null) {
            revokeManagerRoleIfNotLeaderElsewhere(dept.getLeaderId(), deptId);
        }

        // 6. 새 리더 지정 (departments 테이블 업데이트)
        Long currentUserId = com.ees.eval.util.SecurityUtil.getCurrentEmployeeId();
        departmentMapper.updateLeader(deptId, empId, currentUserId, LocalDateTime.now());

        // 7. 새 리더에게 ROLE_MANAGER 권한 부여 (이미 보유 중이면 SKIP)
        grantManagerRole(empId);

        log.info("부서 {} 리더를 사원 {}(으)로 지정 완료. ROLE_MANAGER 권한 부여됨.", deptId, empId);
    }

    /**
     * {@inheritDoc}
     * 현재 리더를 해제하고, 다른 부서의 리더가 아니라면 ROLE_MANAGER 권한을 회수합니다.
     */
    @Override
    @Transactional
    @CacheEvict(value = {"departments", "departments-simple"}, allEntries = true)
    public void removeLeader(Long deptId) {
        // 1. 부서 존재 여부 확인
        Department dept = departmentMapper.findById(deptId)
                .orElseThrow(() -> new IllegalArgumentException("부서를 찾을 수 없습니다. deptId: " + deptId));

        // 2. 현재 리더가 없으면 아무 작업도 하지 않음
        if (dept.getLeaderId() == null) {
            log.info("부서 {}에 리더가 미지정 상태입니다. 변경 사항 없음.", deptId);
            return;
        }

        Long previousLeaderId = dept.getLeaderId();

        // 3. 리더 해제 (leader_id를 NULL로)
        Long currentUserId = com.ees.eval.util.SecurityUtil.getCurrentEmployeeId();
        departmentMapper.updateLeader(deptId, null, currentUserId, LocalDateTime.now());

        // 4. 기존 리더의 권한 회수 (다른 부서 리더가 아닌 경우에만)
        revokeManagerRoleIfNotLeaderElsewhere(previousLeaderId, deptId);

        log.info("부서 {} 리더 해제 완료. 기존 리더: 사원 {}", deptId, previousLeaderId);
    }

    /**
     * 사원에게 ROLE_MANAGER 권한을 부여합니다.
     * 이미 보유 중이면 중복 부여하지 않습니다.
     *
     * @param empId 권한을 부여할 사원 식별자
     */
    private void grantManagerRole(Long empId) {
        // 현재 사원의 권한 목록을 조회
        List<String> currentRoles = employeeMapper.findRoleNamesByEmpId(empId);

        // 관리자(ADMIN)나 임원(EXECUTIVE) 권한이 있는 경우 권한 변경 생략 (보호 로직)
        if (currentRoles.contains("ROLE_ADMIN") || currentRoles.contains("ROLE_EXECUTIVE")) {
            log.info("사원 {}은(는) 상위 권한 보유자이므로 매니저 권한 부여를 생략합니다.", empId);
            return;
        }

        if (currentRoles.contains("ROLE_MANAGER")) {
            log.debug("사원 {}은(는) 이미 ROLE_MANAGER 권한을 보유 중입니다.", empId);
            return;
        }

        // ROLE_MANAGER의 role_id 조회
        Role managerRole = roleMapper.findByRoleName("ROLE_MANAGER")
                .orElseThrow(() -> new IllegalStateException("ROLE_MANAGER 권한 정보를 찾을 수 없습니다."));

        // 기존 권한 소프트 삭제 후 ROLE_MANAGER로 교체
        Long currentUserId = com.ees.eval.util.SecurityUtil.getCurrentEmployeeId();
        LocalDateTime now = LocalDateTime.now();
        employeeMapper.deleteEmployeeRolesByEmpId(empId, currentUserId, now);
        employeeMapper.insertEmployeeRole(empId, managerRole.getRoleId(), currentUserId, now);

        log.info("사원 {}에게 ROLE_MANAGER 권한을 부여했습니다.", empId);
    }

    /**
     * 사원의 ROLE_MANAGER 권한을 회수하고 ROLE_USER로 되돌립니다.
     * 단, 해당 사원이 다른 부서의 리더를 겸임하고 있다면 권한을 유지합니다.
     *
     * @param empId          권한 회수 대상 사원 식별자
     * @param excludeDeptId  현재 해제 중인 부서 ID (이 부서는 카운트에서 제외)
     */
    private void revokeManagerRoleIfNotLeaderElsewhere(Long empId, Long excludeDeptId) {
        // 해당 사원이 리더로 등록된 부서 수를 확인
        int leaderCount = departmentMapper.countDepartmentsByLeaderId(empId);

        // 현재 해제 중인 부서를 제외했을 때 다른 부서 리더가 아닌 경우에만 권한 회수 시도
        if (leaderCount <= 1) {
            // 현재 사원의 권한 목록 조회
            List<String> currentRoles = employeeMapper.findRoleNamesByEmpId(empId);

            // 관리자나 임원 권한 보유자는 부서장 해제 시에도 권한 유지 (보호 로직)
            if (currentRoles.contains("ROLE_ADMIN") || currentRoles.contains("ROLE_EXECUTIVE")) {
                log.info("사원 {}은(는) 상위 권한 보유자이므로 부서장 해제 후에도 권한을 유지합니다.", empId);
                return;
            }

            // ROLE_USER의 role_id 조회
            Role userRole = roleMapper.findByRoleName("ROLE_USER")
                    .orElseThrow(() -> new IllegalStateException("ROLE_USER 권한 정보를 찾을 수 없습니다."));

            Long currentUserId = com.ees.eval.util.SecurityUtil.getCurrentEmployeeId();
            LocalDateTime now = LocalDateTime.now();
            employeeMapper.deleteEmployeeRolesByEmpId(empId, currentUserId, now);
            employeeMapper.insertEmployeeRole(empId, userRole.getRoleId(), currentUserId, now);

            log.info("사원 {}의 ROLE_MANAGER 권한을 회수하고 ROLE_USER로 전환했습니다.", empId);
        } else {
            log.info("사원 {}은(는) 다른 부서의 리더이므로 ROLE_MANAGER 권한을 유지합니다.", empId);
        }
    }

    /**
     * 부서 도메인 엔티티를 DTO 레코드로 변환합니다.
     *
     * @param dept           부서 엔티티
     * @param parentDeptName 상위 부서명 (null 가능)
     * @param leaderName     리더 사원명 (null 가능)
     * @param employeeCount  소속 사원 인원수
     * @return 변환된 DepartmentDTO
     */
    /**
     * Entity를 DTO로 변환합니다. (단일 변환용)
     */
    private DepartmentDTO convertToDto(Department dept, String parentDeptName, String leaderName, int employeeCount) {
        boolean isLeaderExecutive = false;
        if (dept.getLeaderId() != null) {
            List<String> roles = employeeMapper.findRoleNamesByEmpId(dept.getLeaderId());
            isLeaderExecutive = roles.contains("ROLE_EXECUTIVE");
        }
        return convertToDto(dept, parentDeptName, leaderName, employeeCount, isLeaderExecutive);
    }

    /**
     * Entity를 DTO로 변환합니다. (사전에 계산된 임원 여부 포함)
     */
    private DepartmentDTO convertToDto(Department dept, String parentDeptName, String leaderName, int employeeCount, boolean isLeaderExecutive) {
        return DepartmentDTO.builder()
                .deptId(dept.getDeptId())
                .parentDeptId(dept.getParentDeptId())
                .leaderId(dept.getLeaderId())
                .deptName(dept.getDeptName())
                .parentDeptName(parentDeptName)
                .leaderName(leaderName)
                .isLeaderExecutive(isLeaderExecutive)
                .employeeCount(employeeCount)
                .isActive(dept.getIsActive())
                .isDeleted(dept.getIsDeleted())
                .version(dept.getVersion())
                .createdAt(dept.getCreatedAt())
                .createdBy(dept.getCreatedBy())
                .updatedAt(dept.getUpdatedAt())
                .updatedBy(dept.getUpdatedBy())
                .build();
    }

    /**
     * 다수 부서의 부서장들에 대한 임원 권한 여부를 일괄 조회하여 맵으로 반환합니다.
     */
    private Map<Long, Boolean> getLeaderExecutiveMap(List<Department> departments) {
        List<Long> leaderIds = departments.stream()
                .map(Department::getLeaderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (leaderIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Map<String, Object>> roleList = employeeMapper.findRoleNamesByEmpIds(leaderIds);
        
        return roleList.stream()
                .filter(m -> "ROLE_EXECUTIVE".equals(m.get("ROLE_NAME")))
                .map(m -> {
                    Object empId = m.get("EMP_ID");
                    if (empId instanceof Number) {
                        return ((Number) empId).longValue();
                    }
                    return Long.valueOf(empId.toString());
                })
                .distinct()
                .collect(Collectors.toMap(id -> id, id -> true, (v1, v2) -> v1));
    }

    /**
     * DTO 레코드를 부서 도메인 엔티티로 변환합니다.
     *
     * @param dto 부서 DTO
     * @return 변환된 Department 엔티티
     */
    private Department convertToEntity(DepartmentDTO dto) {
        Department dept = Department.builder()
                .deptId(dto.deptId())
                .parentDeptId(dto.parentDeptId())
                .leaderId(dto.leaderId())
                .deptName(dto.deptName())
                // 신규 생성 시 기본값 'y', 수정 시 기존 값 유지
                .isActive(dto.isActive() != null ? dto.isActive() : "y")
                .build();
        dept.setIsDeleted(dto.isDeleted());
        dept.setVersion(dto.version());
        dept.setCreatedAt(dto.createdAt());
        dept.setCreatedBy(dto.createdBy());
        dept.setUpdatedAt(dto.updatedAt());
        dept.setUpdatedBy(dto.updatedBy());
        return dept;
    }

    /**
     * 다수 부서의 추가 정보를 한 번에 배치 조회하여 N+1 문제를 방지합니다.
     */
    private List<DepartmentDTO> convertToDtoListWithDetails(List<Department> departments) {
        if (departments.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 리더 임원 여부 조회
        Map<Long, Boolean> execMap = getLeaderExecutiveMap(departments);

        // 2. 부서 ID 추출
        List<Long> deptIds = departments.stream()
                .map(Department::getDeptId)
                .collect(Collectors.toList());

        // 3. 부서 추가 정보 (상위부서명, 리더명, 직원수) 배치 조회
        List<Map<String, Object>> details = departmentMapper.findDepartmentDetailsByDeptIds(deptIds);
        
        // 4. Map으로 변환하여 매핑 준비
        Map<Long, Map<String, Object>> detailsMap = details.stream()
                .collect(Collectors.toMap(
                        m -> ((Number) m.get("DEPT_ID")).longValue(),
                        m -> m
                ));

        // 5. DTO 변환
        return departments.stream().map(dept -> {
            Map<String, Object> detail = detailsMap.get(dept.getDeptId());
            
            String parentName = null;
            String leaderName = null;
            int count = 0;
            
            if (detail != null) {
                parentName = (String) detail.get("PARENT_DEPT_NAME");
                leaderName = (String) detail.get("LEADER_NAME");
                if (detail.get("EMP_COUNT") != null) {
                    count = ((Number) detail.get("EMP_COUNT")).intValue();
                }
            }
            
            boolean isExec = execMap.getOrDefault(dept.getLeaderId(), false);

            return convertToDto(dept, parentName, leaderName, count, isExec);
        }).collect(Collectors.toList());
    }

    /**
     * Employee 엔티티를 EmployeeDTO 레코드로 변환합니다. (부서 내 사원 조회용)
     *
     * @param emp       사원 엔티티
     * @param roleNames 사원 보유 권한명 목록
     * @return 변환된 EmployeeDTO
     */
    private EmployeeDTO convertEmployeeToDto(Employee emp, List<String> roleNames) {
        return EmployeeDTO.builder()
                .empId(emp.getEmpId())
                .deptId(emp.getDeptId())
                .positionId(emp.getPositionId())
                .password(null) // 비밀번호 외부 노출 방지
                .name(emp.getName())
                .email(emp.getEmail())
                .hireDate(emp.getHireDate())
                .retireDate(emp.getRetireDate())
                .roleNames(roleNames != null ? roleNames : Collections.emptyList())
                .isDeleted(emp.getIsDeleted())
                .version(emp.getVersion())
                .createdAt(emp.getCreatedAt())
                .createdBy(emp.getCreatedBy())
                .updatedAt(emp.getUpdatedAt())
                .updatedBy(emp.getUpdatedBy())
                .build();
    }
}
