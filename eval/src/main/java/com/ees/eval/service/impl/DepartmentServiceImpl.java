package com.ees.eval.service.impl;

import com.ees.eval.domain.Department;
import com.ees.eval.domain.Employee;
import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * DepartmentService 인터페이스의 실제 비즈니스 로직 구현체입니다.
 * 계층형 부서 트리 관리, 소속 사원 조회, 부서 삭제 시 안전 검증 등을 수행합니다.
 */
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;

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

        // 3. 소속 사원 인원수 카운트 조회
        int employeeCount = departmentMapper.countEmployeesByDeptId(deptId);

        return convertToDto(dept, parentDeptName, employeeCount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<DepartmentDTO> getAllDepartments() {
        // 1. 전체 부서 조회 (단일 쿼리)
        List<Department> allDepts = departmentMapper.findAll();

        // N+1 최적화를 위한 인메모리 매핑 (부모 부서명, 사원 수 캐싱)
        Map<Long, String> deptIdToNameMap = allDepts.stream()
                .collect(Collectors.toMap(Department::getDeptId, Department::getDeptName));
        
        Map<Long, Integer> countMap = departmentMapper.findAllEmployeeCounts().stream()
                .collect(Collectors.toMap(
                        m -> ((Number) m.get("DEPT_ID")).longValue(),
                        m -> ((Number) m.get("CNT")).intValue()
                ));

        // 2. 전체 부서 DTO 변환 (메모리에서 매핑하여 반복 쿼리 제거)
        List<DepartmentDTO> allDeptDtos = allDepts.stream()
                .map(dept -> {
                    String parentName = dept.getParentDeptId() != null 
                                        ? deptIdToNameMap.get(dept.getParentDeptId()) 
                                        : null;
                    int count = countMap.getOrDefault(dept.getDeptId(), 0);
                    return convertToDto(dept, parentName, count);
                })
                .collect(Collectors.toList());

        // 3. 부모-자식 트리 맵 구성 (parentDeptId 기준 그룹화)
        Map<Long, List<DepartmentDTO>> childrenMap = allDeptDtos.stream()
                .filter(d -> d.parentDeptId() != null)
                .collect(Collectors.groupingBy(DepartmentDTO::parentDeptId));

        // 4. 최상위(Root) 부서 목록 추출 후 ID 순 정렬
        List<DepartmentDTO> roots = allDeptDtos.stream()
                .filter(d -> d.parentDeptId() == null)
                .sorted(Comparator.comparing(DepartmentDTO::deptId))
                .toList();

        // 5. DFS로 트리 순회하여 한 줄로 펼침(Flatten)
        List<DepartmentDTO> sortedDepts = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (DepartmentDTO root : roots) {
            buildTreeList(root, childrenMap, sortedDepts, 0, visited);
        }

        return sortedDepts;
    }

    /**
     * DFS 방식으로 부서를 재귀 순회하여 리스트에 순차적으로 담습니다.
     * depth 파라미터를 통해 트리의 깊이를 재계산합니다.
     * 순환 참조(루프) 파괴 방지를 위해 visited 셋을 활용합니다.
     */
    private void buildTreeList(DepartmentDTO node, Map<Long, List<DepartmentDTO>> childrenMap,
            List<DepartmentDTO> result, int depth, Set<Long> visited) {
        
        // 순환 참조 감지 (이미 방문한 노드라면 즉시 반환하여 루프 중단)
        if (!visited.add(node.deptId())) {
            return;
        }

        // 트리의 깊이(depth)를 설정한 복제본 객체를 추가
        DepartmentDTO nodeWithDepth = node.toBuilder().treeDepth(depth).build();
        result.add(nodeWithDepth);

        List<DepartmentDTO> children = childrenMap.getOrDefault(node.deptId(), Collections.emptyList());
        // 하위 부서도 식별자 순으로 정렬 후 깊이를 1 증가시켜 재귀 탐색
        children.stream()
                .sorted(Comparator.comparing(DepartmentDTO::deptId))
                .forEach(child -> buildTreeList(child, childrenMap, result, depth + 1, visited));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<DepartmentDTO> getRootDepartments() {
        // 최상위 부서 목록 조회 (parent_dept_id IS NULL)
        return departmentMapper.findRootDepartments().stream()
                .map(dept -> {
                    int count = departmentMapper.countEmployeesByDeptId(dept.getDeptId());
                    return convertToDto(dept, null, count);
                })
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<DepartmentDTO> getChildDepartments(Long parentDeptId) {
        // 상위 부서명을 한 번만 조회하여 하위 부서 목록에 일괄 적용
        Department parentDept = departmentMapper.findById(parentDeptId).orElse(null);
        String parentDeptName = (parentDept != null) ? parentDept.getDeptName() : null;

        return departmentMapper.findByParentDeptId(parentDeptId).stream()
                .map(dept -> {
                    int count = departmentMapper.countEmployeesByDeptId(dept.getDeptId());
                    return convertToDto(dept, parentDeptName, count);
                })
                .collect(Collectors.toList());
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

        // 2. 부서 소속 사원 목록 조회 후 DTO 변환 (권한 정보 포함)
        return employeeMapper.findByDeptId(deptId).stream()
                .map(emp -> {
                    List<String> roleNames = employeeMapper.findRoleNamesByEmpId(emp.getEmpId());
                    return convertEmployeeToDto(emp, roleNames);
                })
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
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
    public DepartmentDTO updateDepartment(DepartmentDTO departmentDto) {
        // [안정성 검증 1] 자기 자신을 상위 부서로 지정하는 것 원천 차단
        if (departmentDto.deptId().equals(departmentDto.parentDeptId())) {
            throw new IllegalArgumentException("부서의 상위 부서로 자기 자신을 지정할 수 없습니다.");
        }

        // [안정성 검증 2] 자신의 하위 부서를 상위 부서로 지정하는지 탐색 (순환 참조 사이클 발생 원천 차단)
        Long currentParentId = departmentDto.parentDeptId();
        while (currentParentId != null) {
            if (currentParentId.equals(departmentDto.deptId())) {
                throw new IllegalArgumentException("자신의 하위 부서를 상위 부서로 지정할 수 없습니다. (순환 참조 구조 오류)");
            }
            Department parentEntity = departmentMapper.findById(currentParentId).orElse(null);
            currentParentId = (parentEntity != null) ? parentEntity.getParentDeptId() : null;
        }

        // 엔티티 변환 및 수정 일시 갱신
        Department dept = convertToEntity(departmentDto);
        dept.preUpdate();

        // 낙관적 락 체크를 포함한 업데이트 수행
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
        Long currentUserId = 1L; // 추후 SecurityContext에서 교체 예정
        departmentMapper.softDelete(deptId, currentUserId, LocalDateTime.now());
    }

    /**
     * 부서 도메인 엔티티를 DTO 레코드로 변환합니다.
     *
     * @param dept           부서 엔티티
     * @param parentDeptName 상위 부서명 (null 가능)
     * @param employeeCount  소속 사원 인원수
     * @return 변환된 DepartmentDTO
     */
    private DepartmentDTO convertToDto(Department dept, String parentDeptName, int employeeCount) {
        return DepartmentDTO.builder()
                .deptId(dept.getDeptId())
                .parentDeptId(dept.getParentDeptId())
                .deptName(dept.getDeptName())
                .parentDeptName(parentDeptName)
                .employeeCount(employeeCount)
                .isDeleted(dept.getIsDeleted())
                .version(dept.getVersion())
                .createdAt(dept.getCreatedAt())
                .createdBy(dept.getCreatedBy())
                .updatedAt(dept.getUpdatedAt())
                .updatedBy(dept.getUpdatedBy())
                .build();
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
                .deptName(dto.deptName())
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
