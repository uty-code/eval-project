package com.ees.eval.service.impl;

import com.ees.eval.domain.Employee;
import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EmployeeService 인터페이스의 실제 비즈니스 로직 구현체입니다.
 * BCrypt 비밀번호 암호화, 낙관적 락, 소프트 델리트,
 * 그리고 Java 21 Pattern Matching을 활용한 권한 판별 로직을 포함합니다.
 */
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public EmployeeDTO getEmployeeById(Long empId) {
        // 1. 매퍼를 통해 사원 엔티티 조회
        Employee employee = employeeMapper.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다. empId: " + empId));

        // 2. 사원이 보유한 권한명 목록을 별도 쿼리로 조회
        List<String> roleNames = employeeMapper.findRoleNamesByEmpId(empId);

        // 3. 엔티티와 권한 목록을 결합하여 DTO로 변환
        return convertToDto(employee, roleNames);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public EmployeeDTO getEmployeeByUsername(String username) {
        // 1. username 기반 사원 조회 (Spring Security 인증 연계)
        Employee employee = employeeMapper.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다. username: " + username));

        // 2. 권한 목록 조회 후 DTO 변환
        List<String> roleNames = employeeMapper.findRoleNamesByEmpId(employee.getEmpId());
        return convertToDto(employee, roleNames);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDTO> getAllEmployees() {
        // 전체 사원 목록을 조회하고 각각에 대해 권한명 목록을 추가로 조합
        return employeeMapper.findAll().stream()
                .map(emp -> {
                    List<String> roleNames = employeeMapper.findRoleNamesByEmpId(emp.getEmpId());
                    return convertToDto(emp, roleNames);
                })
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EmployeeDTO registerEmployee(EmployeeDTO employeeDto, List<Long> roleIds) {
        // 1. DTO를 엔티티로 변환
        Employee employee = convertToEntity(employeeDto);

        // 2. 비밀번호를 BCrypt로 암호화 처리
        String encodedPassword = passwordEncoder.encode(employeeDto.password());
        employee.setPassword(encodedPassword);

        // 3. 감사 필드 초기화 (is_deleted='n', version=0, created_at 등)
        employee.prePersist();

        // 4. employees 테이블에 삽입
        employeeMapper.insert(employee);

        // 5. employee_roles 매핑 테이블에 권한 정보 삽입
        if (roleIds != null && !roleIds.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (Long roleId : roleIds) {
                employeeMapper.insertEmployeeRole(employee.getEmpId(), roleId, 1L, now);
            }
        }

        // 6. 저장 완료된 최신 정보를 다시 조회하여 반환
        return getEmployeeById(employee.getEmpId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EmployeeDTO updateEmployee(EmployeeDTO employeeDto) {
        // 1. 엔티티 변환 및 수정 일시 갱신
        Employee employee = convertToEntity(employeeDto);
        employee.preUpdate();

        // 2. MyBatis를 통한 조건부 업데이트 (version 체크로 낙관적 락 적용)
        int updatedRows = employeeMapper.update(employee);
        if (updatedRows == 0) {
            throw new EesOptimisticLockException("사원 정보가 다른 사용자에 의해 변경되었거나 수정 충돌이 발생했습니다.");
        }

        // 3. 최신 데이터 재조회
        return getEmployeeById(employee.getEmpId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteEmployee(Long empId) {
        Long currentUserId = 1L; // 추후 SecurityContext에서 가져올 예정

        // is_deleted = 'y'로 상태 변경하여 논리적 삭제 처리
        int updatedRows = employeeMapper.softDelete(empId, currentUserId, LocalDateTime.now());
        if (updatedRows == 0) {
            throw new IllegalArgumentException("삭제 대상 사원을 찾을 수 없습니다. empId: " + empId);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean authenticate(String username, String rawPassword) {
        // 1. username으로 사원 조회 (없으면 인증 실패)
        Employee employee = employeeMapper.findByUsername(username).orElse(null);
        if (employee == null) {
            return false;
        }

        // 2. 재직 중이 아닌 경우(퇴사, 휴직 등) 인증 거부 (노션 요구사항 반영)
        if (!"EMPLOYED".equalsIgnoreCase(employee.getStatusCode())) {
            return false;
        }

        // 3. BCryptPasswordEncoder.matches()로 평문 비밀번호와 해시값 비교
        return passwordEncoder.matches(rawPassword, employee.getPassword());
    }

    /**
     * {@inheritDoc}
     * Java 21 Pattern Matching for switch를 활용하여 권한명에 따른 접근 레벨을 판별합니다.
     */
    @Override
    public String checkAccessPrivilege(String roleName) {
        // Java 21: Pattern Matching for switch 적용
        return switch (roleName) {
            case String r when r.equals("ROLE_ADMIN")   -> "전체 시스템 접근 허용 (관리자)";
            case String r when r.equals("ROLE_MANAGER") -> "부서 관리 및 평가 조회 허용 (매니저)";
            case String r when r.equals("ROLE_USER")    -> "본인 평가 조회만 허용 (일반 사용자)";
            default -> "알 수 없는 권한입니다: " + roleName;
        };
    }

    /**
     * 도메인 엔티티(Employee)를 DTO(EmployeeDTO) 레코드로 변환합니다.
     * 비밀번호는 보안을 위해 DTO에 포함하지 않습니다.
     *
     * @param employee  원본 사원 엔티티
     * @param roleNames 사원이 보유한 권한명 목록
     * @return 변환된 EmployeeDTO 레코드
     */
    private EmployeeDTO convertToDto(Employee employee, List<String> roleNames) {
        return EmployeeDTO.builder()
                .empId(employee.getEmpId())
                .deptId(employee.getDeptId())
                .positionId(employee.getPositionId())
                .username(employee.getUsername())
                .password(null) // 비밀번호는 외부 노출하지 않음
                .name(employee.getName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .statusCode(employee.getStatusCode())
                .hireDate(employee.getHireDate())
                .positionName(null) // 추후 별도 조회 시 채움
                .roleNames(roleNames != null ? roleNames : Collections.emptyList())
                .isDeleted(employee.getIsDeleted())
                .version(employee.getVersion())
                .createdAt(employee.getCreatedAt())
                .createdBy(employee.getCreatedBy())
                .updatedAt(employee.getUpdatedAt())
                .updatedBy(employee.getUpdatedBy())
                .build();
    }

    /**
     * DTO(EmployeeDTO) 레코드를 도메인 엔티티(Employee)로 변환합니다.
     *
     * @param dto 변환할 사원 DTO
     * @return 변환된 Employee 엔티티
     */
    private Employee convertToEntity(EmployeeDTO dto) {
        Employee employee = Employee.builder()
                .empId(dto.empId())
                .deptId(dto.deptId())
                .positionId(dto.positionId())
                .username(dto.username())
                .password(dto.password())
                .name(dto.name())
                .email(dto.email())
                .phone(dto.phone())
                .statusCode(dto.statusCode())
                .hireDate(dto.hireDate())
                .build();
        employee.setIsDeleted(dto.isDeleted());
        employee.setVersion(dto.version());
        employee.setCreatedAt(dto.createdAt());
        employee.setCreatedBy(dto.createdBy());
        employee.setUpdatedAt(dto.updatedAt());
        employee.setUpdatedBy(dto.updatedBy());
        return employee;
    }
}
