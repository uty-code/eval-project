package com.ees.eval.service.impl;

import com.ees.eval.domain.Department;
import com.ees.eval.domain.Employee;
import com.ees.eval.domain.Position;
import com.ees.eval.dto.EmployeeDTO;
import com.ees.eval.dto.EmployeePageDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import com.ees.eval.mapper.DepartmentMapper;
import com.ees.eval.mapper.EmployeeMapper;
import com.ees.eval.mapper.LoginLogMapper;
import com.ees.eval.mapper.PositionMapper;
import com.ees.eval.mapper.RoleMapper;
import com.ees.eval.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final PasswordEncoder passwordEncoder;
    private final RoleMapper roleMapper;
    private final LoginLogMapper loginLogMapper;
    @Qualifier("virtualThreadExecutor")
    private final Executor virtualThreadExecutor;

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

        // 3. 부서명, 직급명 조회
        String deptName = departmentMapper.findById(employee.getDeptId())
                .map(d -> d.getDeptName()).orElse(null);
        String positionName = positionMapper.findById(employee.getPositionId())
                .map(p -> p.getPositionName()).orElse(null);

        // 4. 엔티티와 권한/부서/직급 정보를 결합하여 DTO로 변환
        return convertToDto(employee, roleNames, deptName, positionName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public EmployeeDTO getEmployeeByUsername(String username) {
        // 1. username 기반 사원 조회 (Spring Security 인증 연계)
        try {
            Long empId = Long.parseLong(username);
            Employee employee = employeeMapper.findByIdForAuth(empId)
                    .orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다. empId: " + empId));

            // 2. 권한 목록 조회 후 DTO 변환 (인증 용도이므로 부서명/직급명 불필요)
            List<String> roleNames = employeeMapper.findRoleNamesByEmpId(employee.getEmpId());
            return convertToDto(employee, roleNames, null, null);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("사원 번호 형식이 잘못되었습니다. 숫자만 입력 가능합니다.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDTO> getAllEmployees() {
        // 부서/직급 전체 목록을 미리 Map으로 캐싱 (N+1 방지)
        Map<Long, String> deptMap = departmentMapper.findAll().stream()
                .collect(Collectors.toMap(Department::getDeptId, Department::getDeptName));
        Map<Long, String> positionMap = positionMapper.findAll().stream()
                .collect(Collectors.toMap(Position::getPositionId, Position::getPositionName));

        return employeeMapper.findAll().stream()
                .map(emp -> {
                    List<String> roleNames = employeeMapper.findRoleNamesByEmpId(emp.getEmpId());
                    String deptName = deptMap.get(emp.getDeptId());
                    String positionName = positionMap.get(emp.getPositionId());
                    return convertToDto(emp, roleNames, deptName, positionName);
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

        // 퇴사 처리 자동화 (신규 등록 시)
        if ("RETIRED".equalsIgnoreCase(employee.getStatusCode())) {
            if (employee.getRetireDate() == null) {
                employee.setRetireDate(LocalDate.now());
            }
        }

        // 2. 비밀번호를 BCrypt로 암호화 처리
        String encodedPassword = passwordEncoder.encode(employeeDto.password());
        employee.setPassword(encodedPassword);

        // 3. 감사 필드 초기화 (is_deleted='n', version=0, created_at 등)
        employee.prePersist();
        if (employee.getStatusCode() == null || employee.getStatusCode().isBlank()) {
            employee.setStatusCode("EMPLOYED");
        }


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
        // 1. 기존 데이터와 비교를 위해 조회
        Employee existingEmployee = employeeMapper.findById(employeeDto.empId())
                .orElseThrow(() -> new IllegalArgumentException("사원 정보를 찾을 수 없습니다."));

        // 2. 엔티티 변환
        Employee employee = convertToEntity(employeeDto);

        // 3. 퇴사 처리 자동화 로직
        if ("RETIRED".equalsIgnoreCase(employee.getStatusCode())) {
            // 이미 퇴사 정보가 있다면 유지하고, 새로 퇴사 처리되는 사원이면 오늘을 퇴사일로 설정
            if (existingEmployee.getRetireDate() != null) {
                employee.setRetireDate(existingEmployee.getRetireDate());
            } else {
                employee.setRetireDate(LocalDate.now());
            }
        } else {
            // 퇴사 상태가 아니면 퇴사일 초기화
            employee.setRetireDate(null);
        }
        
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
     * 사원 기본 정보 수정 후 권한도 함께 교체합니다.
     * 기존 권한(employee_roles_51)은 소프트 삭제 처리 후 새 권한을 삽입합니다.
     * 단, 해당 사원이 부서장인 경우 ROLE_MANAGER 권한은 자동 유지됩니다.
     */
    @Override
    @Transactional
    public EmployeeDTO updateEmployee(EmployeeDTO employeeDto, List<Long> roleIds) {
        // 1. 기존 데이터와 비교를 위해 조회
        Employee existingEmployee = employeeMapper.findById(employeeDto.empId())
                .orElseThrow(() -> new IllegalArgumentException("사원 정보를 찾을 수 없습니다."));

        // 2. 엔티티 변환
        Employee employee = convertToEntity(employeeDto);

        // 3. 퇴사 처리 자동화 로직
        if ("RETIRED".equalsIgnoreCase(employee.getStatusCode())) {
            if (existingEmployee.getRetireDate() != null) {
                employee.setRetireDate(existingEmployee.getRetireDate());
            } else {
                employee.setRetireDate(LocalDate.now());
            }
        } else {
            employee.setRetireDate(null);
        }

        employee.preUpdate();

        int updatedRows = employeeMapper.update(employee);
        if (updatedRows == 0) {
            throw new EesOptimisticLockException("사원 정보가 다른 사용자에 의해 변경되었거나 수정 충돌이 발생했습니다.");
        }

        // 4. 권한 교체: 기존 권한 소프트 삭제 후 새 권한 삽입
        if (roleIds != null && !roleIds.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            Long adminId = 1L; // TODO: SecurityContext에서 현재 로그인 사용자 ID로 교체

            // 부서장 권한 보호: 해당 사원이 부서장이면 권한 변경 자체를 차단
            int leaderCount = departmentMapper.countDepartmentsByLeaderId(employee.getEmpId());
            if (leaderCount > 0) {
                Long managerRoleId = roleMapper.findByRoleName("ROLE_MANAGER")
                        .orElseThrow(() -> new IllegalStateException("ROLE_MANAGER 권한 정보를 찾을 수 없습니다."))
                        .getRoleId();
                // ROLE_MANAGER가 제외된 경우 예외 발생으로 차단
                if (!roleIds.contains(managerRoleId)) {
                    throw new IllegalStateException(
                            "이 사원은 현재 부서장으로 지정되어 있습니다. 권한을 변경하려면 부서 관리 페이지에서 부서장을 먼저 해제하세요.");
                }
            }

            employeeMapper.deleteEmployeeRolesByEmpId(employee.getEmpId(), adminId, now);
            for (Long roleId : roleIds) {
                employeeMapper.insertEmployeeRole(employee.getEmpId(), roleId, adminId, now);
            }
        }

        // 5. 최신 데이터 재조회
        return getEmployeeById(employee.getEmpId());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean authenticate(String loginId, String rawPassword) {
        Long empId;
        try {
            empId = Long.parseLong(loginId);
        } catch (NumberFormatException e) {
            return false;
        }
        Optional<Employee> optionalEmp = employeeMapper.findByIdForAuth(empId);
        if (optionalEmp.isPresent()) {
            Employee employee = optionalEmp.get();
            // 계정 잠금 또는 이미 퇴사된 경우엔 로그인 실패 처리 (필요에 따라 예외 던질 수 있음)
            if ("RETIRED".equalsIgnoreCase(employee.getStatusCode())) {
                return false;
            }
            if (loginLogMapper.countRecentFailures(empId) >= 5) {
                return false;
            }

            return passwordEncoder.matches(rawPassword, employee.getPassword());
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Long getNextEmpId() {
        return employeeMapper.getNextEmpId();
    }

    /**
     * {@inheritDoc}
     * Java 21 Pattern Matching for switch를 활용하여 권한명에 따른 접근 레벨을 판별합니다.
     */
    @Override
    public String checkAccessPrivilege(String roleName) {
        // Java 21: Pattern Matching for switch 적용
        return switch (roleName) {
            case String r when r.equals("ROLE_ADMIN") -> "전체 시스템 접근 허용 (관리자)";
            case String r when r.equals("ROLE_MANAGER") -> "부서 관리 및 평가 조회 허용 (매니저)";
            case String r when r.equals("ROLE_USER") -> "본인 평가 조회만 허용 (일반 사용자)";
            default -> "알 수 없는 권한입니다: " + roleName;
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDTO> getPendingEmployees() {
        // 부서/직급 전체 목록을 미리 Map으로 캐싱 (N+1 방지)
        Map<Long, String> deptMap = departmentMapper.findAll().stream()
                .collect(Collectors.toMap(Department::getDeptId, Department::getDeptName));
        Map<Long, String> positionMap = positionMapper.findAll().stream()
                .collect(Collectors.toMap(Position::getPositionId, Position::getPositionName));

        // 승인 대기 중인 사원 목록 조회
        List<Employee> pendingEmployees = employeeMapper.findPendingEmployees();

        if (pendingEmployees.isEmpty()) {
            return Collections.emptyList();
        }

        // N+1 최적화: 대기 중인 사원들의 권한 목록을 IN 쿼리로 한 번에 조회하여 그룹화
        List<Long> empIds = pendingEmployees.stream().map(Employee::getEmpId).collect(Collectors.toList());
        List<Map<String, Object>> roleMaps = employeeMapper.findRoleNamesByEmpIds(empIds);

        // EmpId를 키로, RoleName의 리스트를 값으로 가지는 Map 생성
        Map<Long, List<String>> empRolesMap = roleMaps.stream()
                .collect(Collectors.groupingBy(
                        row -> ((Number) row.get("EMP_ID")).longValue(),
                        Collectors.mapping(row -> (String) row.get("ROLE_NAME"), Collectors.toList())));

        return pendingEmployees.stream()
                .map(emp -> {
                    List<String> roleNames = empRolesMap.getOrDefault(emp.getEmpId(), Collections.emptyList());
                    String deptName = deptMap.get(emp.getDeptId());
                    String positionName = positionMap.get(emp.getPositionId());
                    return convertToDto(emp, roleNames, deptName, positionName);
                })
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * 대시보드 인사 현황용으로 최신 등록순 상위 5명의 사원을 조회합니다.
     * JOIN 쿼리를 통해 부서명, 직급명이 엔티티에 이미 포함되어 있으므로
     * 추가 N+1 쿼리 없이 바로 DTO로 변환합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDTO> getTop5RecentEmployees() {
        return employeeMapper.findTop5RecentWithDetail().stream()
                .map(emp -> convertToDto(emp, Collections.emptyList(), emp.getDeptName(), emp.getPositionName()))
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * Mapper의 COUNT 쿼리를 호출하여 DB에서 직접 승인 대기 사원 수를 집계합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public long countPendingEmployees() {
        return employeeMapper.countPendingEmployees();
    }

    /**
     * {@inheritDoc}
     * Mapper의 COUNT 쿼리를 호출하여 DB에서 직접 재직 사원 수를 집계합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public long countActiveEmployees() {
        return employeeMapper.countActiveEmployees();
    }

    /**
     * {@inheritDoc}
     * PENDING 상태의 사원을 EMPLOYED로 변경하고 ROLE_USER 권한을 자동 부여합니다.
     */
    @Override
    @Transactional
    public void approveEmployee(Long empId, Long adminId) {
        LocalDateTime now = LocalDateTime.now();
        // 1. 상태를 EMPLOYED로 변경
        int updated = employeeMapper.updateStatusCode(empId, "EMPLOYED", adminId, now);
        if (updated == 0) {
            throw new IllegalArgumentException("승인 대상 사원을 찾을 수 없습니다. empId: " + empId);
        }
        // 2. ROLE_USER 권한 자동 부여 (이미 있으면 중복 방지)
        List<String> existingRoles = employeeMapper.findRoleNamesByEmpId(empId);
        if (!existingRoles.contains("ROLE_USER")) {
            Long userRoleId = roleMapper.findByRoleName("ROLE_USER")
                    .orElseThrow(() -> new IllegalStateException("ROLE_USER 권한 정보를 찾을 수 없습니다."))
                    .getRoleId();
            employeeMapper.insertEmployeeRole(empId, userRoleId, adminId, now);
        }
    }

    /**
     * {@inheritDoc}
     * PENDING 상태 사원을 소프트 삭제 처리하여 거절합니다.
     */
    @Override
    @Transactional
    public void rejectEmployee(Long empId, Long adminId) {
        int updated = employeeMapper.softDelete(empId, adminId, LocalDateTime.now());
        if (updated == 0) {
            throw new IllegalArgumentException("거절 대상 사원을 찾을 수 없습니다. empId: " + empId);
        }
    }

    /**
     * 도메인 엔티티(Employee)를 DTO(EmployeeDTO) 레코드로 변환합니다.
     * 비밀번호는 보안을 위해 DTO에 포함하지 않습니다.
     *
     * @param employee  원본 사원 엔티티
     * @param roleNames 사원이 보유한 권한명 목록
     * @return 변환된 EmployeeDTO 레코드
     */
    private EmployeeDTO convertToDto(Employee employee, List<String> roleNames, String deptName, String positionName) {
        // login_logs 기반 연속 실패 횟수로 잠금 여부 판단
        boolean locked = false;
        if (employee.getEmpId() != null) {
            locked = loginLogMapper.countRecentFailures(employee.getEmpId()) >= 5;
        }
        return EmployeeDTO.builder()
                .empId(employee.getEmpId())
                .deptId(employee.getDeptId())
                .positionId(employee.getPositionId())
                .password(null) // 비밀번호는 외부 노출하지 않음
                .name(employee.getName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .statusCode(employee.getStatusCode())
                .hireDate(employee.getHireDate())
                .retireDate(employee.getRetireDate())
                .deptName(deptName)
                .positionName(positionName)
                .isLocked(locked)
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
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public long countThisYearHired() {
        int currentYear = LocalDate.now().getYear();
        return employeeMapper.countThisYearHired(currentYear);
    }

    /**
     * {@inheritDoc}
     * JOIN 쿼리로 사원+부서+직급을 한 번에 가져오고,
     * 페이지 데이터 조회와 전체 건수 조회를 병렬로 실행하여 성능을 최적화합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public EmployeePageDTO searchEmployeesPage(
            String searchName, Long searchDeptId, String searchStatus,
            int pageNum, int pageSize) {

        // OFFSET 계산 (0부터 시작)
        int offset = (pageNum - 1) * pageSize;

        // 페이지 데이터 조회(JOIN 쿼리)와 전체 건수 조회를 병렬로 실행
        CompletableFuture<List<Employee>> employeesFuture = CompletableFuture.supplyAsync(
                () -> employeeMapper.searchEmployeesWithDetail(searchName, searchDeptId, searchStatus, offset,
                        pageSize),
                virtualThreadExecutor);

        CompletableFuture<Long> totalCountFuture = CompletableFuture.supplyAsync(
                () -> employeeMapper.countSearchEmployees(searchName, searchDeptId, searchStatus),
                virtualThreadExecutor);

        // 두 조회가 모두 완료될 때까지 대기
        CompletableFuture.allOf(employeesFuture, totalCountFuture).join();

        List<Employee> employees = employeesFuture.join();
        long totalCount = totalCountFuture.join();

        if (employees.isEmpty()) {
            return EmployeePageDTO.of(Collections.emptyList(), pageNum, pageSize, totalCount);
        }

        // N+1 최적화: 페이지에 속한 사원들의 권한 목록을 IN 쿼리로 한 번에 조회
        List<Long> empIds = employees.stream().map(Employee::getEmpId).collect(Collectors.toList());
        List<Map<String, Object>> roleMaps = employeeMapper.findRoleNamesByEmpIds(empIds);

        // EmpId를 키로, 권한명 리스트를 값으로 가지는 Map 생성
        Map<Long, List<String>> empRolesMap = roleMaps.stream()
                .collect(Collectors.groupingBy(
                        row -> ((Number) row.get("EMP_ID")).longValue(),
                        Collectors.mapping(row -> (String) row.get("ROLE_NAME"), Collectors.toList())));

        // JOIN으로 가져온 deptName/positionName + 배치 조회한 roleNames 조합하여 DTO 변환
        List<EmployeeDTO> employeeDTOs = employees.stream()
                .map(emp -> {
                    List<String> roleNames = empRolesMap.getOrDefault(emp.getEmpId(), Collections.emptyList());
                    // deptName, positionName 은 JOIN 쿼리가 이미 채워준 값 사용
                    return convertToDto(emp, roleNames, emp.getDeptName(), emp.getPositionName());
                })
                .collect(Collectors.toList());

        return EmployeePageDTO.of(employeeDTOs, pageNum, pageSize, totalCount);
    }

    /**
     * {@inheritDoc}
     * 현재 비밀번호를 검증 후 BCrypt로 암호화하여 새 비밀번호로 교체합니다.
     */
    @Override
    @Transactional
    public void changePassword(Long empId, String currentPassword, String newPassword) {
        // 1. 현재 비밀번호 조회 (인증용 쿼리)
        Employee employee = employeeMapper.findByIdForAuth(empId)
                .orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다."));

        // 2. 현재 비밀번호 일치 여부 검증
        if (!passwordEncoder.matches(currentPassword, employee.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 3. 새 비밀번호가 현재 비밀번호와 동일한 경우 변경 불가
        if (passwordEncoder.matches(newPassword, employee.getPassword())) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        // 4. 새 비밀번호 암호화 후 저장
        String encodedNewPassword = passwordEncoder.encode(newPassword);
        int updated = employeeMapper.updatePassword(empId, encodedNewPassword);
        if (updated == 0) {
            throw new IllegalStateException("비밀번호 변경에 실패했습니다.");
        }
    }

    /**
     * {@inheritDoc}
     * 이메일과 전화번호만 업데이트합니다.
     */
    @Override
    @Transactional
    public void updateContactInfo(Long empId, String email, String phone) {
        // 전화번호 포맷 정리 (숫자만 남기고 재포맷)
        String formattedPhone = phone;
        if (phone != null && !phone.isBlank()) {
            String digits = phone.replaceAll("[^0-9]", "");
            if (digits.length() == 11) {
                formattedPhone = digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
            } else if (digits.length() == 10) {
                formattedPhone = digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
            }
        }
        int updated = employeeMapper.updateContactInfo(empId, email, formattedPhone);
        if (updated == 0) {
            throw new IllegalStateException("연락처 수정에 실패했습니다.");
        }
    }

    /**
     * {@inheritDoc}
     * login_logs_51의 실패 기록을 초기화하고,
     * 비밀번호를 사번(empId)으로 리셋하여 잠긴 계정을 해제합니다.
     */
    @Override
    @Transactional
    public void unlockAccount(Long empId) {
        // 1. 실패 로그 초기화 (is_failure를 'n'으로 변경)
        loginLogMapper.resetFailureLogsByEmpId(empId);

        // 2. 비밀번호를 사번으로 초기화 (BCrypt 암호화)
        String encodedPassword = passwordEncoder.encode(String.valueOf(empId));
        int updated = employeeMapper.resetPassword(empId, encodedPassword);
        if (updated == 0) {
            throw new IllegalArgumentException("잠금 해제 대상 사원을 찾을 수 없습니다. empId: " + empId);
        }
    }

    /**
     * {@inheritDoc}
     * login_logs 기반으로 연속 실패 5회 이상인 사원 목록을 조회하여 DTO로 변환합니다.
     */
    @Override
    public List<EmployeeDTO> getLockedEmployees() {
        return employeeMapper.findLockedEmployees().stream()
                .map(emp -> convertToDto(emp, Collections.emptyList(), emp.getDeptName(), emp.getPositionName()))
                .toList();
    }

    /**
     * {@inheritDoc}
     * login_logs 기반으로 연속 실패 5회 이상인 사원 수를 반환합니다.
     */
    @Override
    public long countLockedEmployees() {
        return employeeMapper.countLockedEmployees();
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
                .password(dto.password())
                .name(dto.name())
                .email(dto.email())
                .phone(dto.phone())
                .statusCode(dto.statusCode())
                .hireDate(dto.hireDate())
                .retireDate(dto.retireDate())
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
