package com.ees.eval.service;

import com.ees.eval.dto.EmployeeDTO;

import java.util.List;

/**
 * 사원(Employee) 관리 업무를 담당하는 서비스 인터페이스입니다.
 * 사원 등록(비밀번호 암호화), 인증 검증, 조회, 수정, 삭제 기능을 제공합니다.
 */
public interface EmployeeService {

    /**
     * 사원 ID로 사원 상세 정보를 조회합니다.
     * JOIN을 통해 직급명과 권한명 목록이 함께 반환됩니다.
     *
     * @param empId 조회할 사원 식별자
     * @return 직급명, 권한명이 포함된 사원 DTO
     * @throws IllegalArgumentException 해당 ID의 사원이 존재하지 않을 경우 발생
     */
    EmployeeDTO getEmployeeById(Long empId);

    /**
     * 로그인 아이디(username)로 사원 상세 정보를 조회합니다.
     *
     * @param username 조회할 사원의 로그인 아이디
     * @return 사원 DTO
     * @throws IllegalArgumentException 해당 username의 사원이 존재하지 않을 경우 발생
     */
    EmployeeDTO getEmployeeByUsername(String username);

    /**
     * 전체 사원 목록을 조회합니다.
     *
     * @return 사원 DTO 리스트
     */
    List<EmployeeDTO> getAllEmployees();

    /**
     * 검색 조건(이름, 부서, 재직 상태)에 따라 사원 목록을 조회합니다.
     *
     * @param searchName   검색할 사원 성명 (부분 일치, null 허용)
     * @param searchDeptId 검색할 부서 ID (null 허용)
     * @param searchStatus 검색할 재직 상태 코드 (null 허용)
     * @return 조건에 해당하는 사원 DTO 리스트
     */
    List<EmployeeDTO> searchEmployees(String searchName, Long searchDeptId, String searchStatus);

    /**
     * 신규 사원을 등록합니다. 비밀번호는 BCrypt로 암호화된 후 저장됩니다.
     *
     * @param dto 등록할 사원 정보 DTO (평문 비밀번호 포함)
     * @param roleIds 부여할 권한 목록
     * @return 등록된 사원의 정보를 담은 EmployeeDTO
     */
    EmployeeDTO registerEmployee(EmployeeDTO dto, List<Long> roleIds);

    /**
     * 발급 대기 중인 다음 사원 번호를 조회합니다.
     * @return 다음 사번
     */
    Long getNextEmpId();

    /**
     * 사원 정보를 수정합니다. 낙관적 락을 통한 동시성 제어가 수행됩니다.
     *
     * @param employeeDto 수정할 데이터가 포함된 DTO
     * @return 수정 완료된 사원 정보 DTO
     * @throws com.ees.eval.exception.EesOptimisticLockException 데이터 충돌 시 발생
     */
    EmployeeDTO updateEmployee(EmployeeDTO employeeDto);

    /**
     * 사원 정보와 권한을 함께 수정합니다.
     * 기존 권한은 소프트 삭제 후 새 권한으로 교체됩니다.
     *
     * @param employeeDto 수정할 데이터가 포함된 DTO
     * @param roleIds     새로 부여할 권한 ID 목록
     * @return 수정 완료된 사원 정보 DTO
     * @throws com.ees.eval.exception.EesOptimisticLockException 데이터 충돌 시 발생
     */
    EmployeeDTO updateEmployee(EmployeeDTO employeeDto, List<Long> roleIds);

    /**
     * 사원을 논리적으로 삭제(Soft Delete) 처리합니다.
     *
     * @param empId 삭제할 사원의 고유 식별자
     * @throws IllegalArgumentException 대상 사원이 존재하지 않을 경우 발생
     */
    void deleteEmployee(Long empId);

    /**
     * 로그인 시도 시 비밀번호 일치 여부를 확인합니다.
     * BCryptPasswordEncoder의 matches() 메서드를 통해 검증합니다.
     *
     * @param username 로그인 아이디
     * @param rawPassword 사용자가 입력한 평문 비밀번호
     * @return 인증 성공 시 true, 실패 시 false
     */
    boolean authenticate(String username, String rawPassword);

    /**
     * Java 21 Pattern Matching for switch를 활용한 권한별 접근 판별 메서드입니다.
     * 사원에게 부여된 권한명에 따라 접근 가능 범위를 문자열로 반환합니다.
     *
     * @param roleName 판별 대상 권한명
     * @return 해당 권한의 접근 레벨 설명 문자열
     */
    String checkAccessPrivilege(String roleName);

    /**
     * 승인 대기(PENDING) 상태의 사원 등록 신청 목록을 조회합니다.
     *
     * @return PENDING 사원 DTO 리스트
     */
    List<EmployeeDTO> getPendingEmployees();

    /**
     * 승인 대기 중인 사원 수를 조회합니다.
     * @return 승인 대기 사원 수
     */
    long countPendingEmployees();

    /**
     * 사원 등록 신청을 승인하여 EMPLOYED 상태로 변경하고 ROLE_USER 권한을 부여합니다.
     *
     * @param empId     승인할 사원의 식별자
     * @param adminId   승인을 처리하는 관리자의 empId
     */
    void approveEmployee(Long empId, Long adminId);

    /**
     * 사원 등록 신청을 거절하여 해당 레코드를 소프트 삭제 처리합니다.
     *
     * @param empId   거절할 사원의 식별자
     * @param adminId 거절을 처리하는 관리자의 empId
     */
    void rejectEmployee(Long empId, Long adminId);

    /**
     * 대시보드 인사 현황용으로 최신 등록순 상위 5명의 사원 정보를 조회합니다.
     * 부서명, 직급명이 포함된 DTO 리스트를 반환합니다.
     *
     * @return 최신 등록순 사원 DTO 목록 (최대 5건)
     */
    List<EmployeeDTO> getTop5RecentEmployees();

    /**
     * 재직 상태(EMPLOYED)인 사원의 수를 DB에서 직접 조회합니다.
     *
     * @return 재직 중인 사원 수
     */
    long countActiveEmployees();

    /**
     * 올해 입사한 사원의 수를 DB에서 직접 조회합니다.
     *
     * @return 올해 입사자 수
     */
    long countThisYearHired();

    /**
     * 검색 조건과 페이지 정보를 기반으로 사원 목록을 조회합니다.
     * 내부적으로 JOIN 쿼리와 페이지네이션(OFFSET/FETCH)을 활용하여 성능을 최적화합니다.
     *
     * @param searchName   검색할 사원 성명 (null 허용)
     * @param searchDeptId 검색할 부서 ID (null 허용)
     * @param searchStatus 검색할 재직 상태 코드 (null 허용)
     * @param pageNum      현재 페이지 번호 (1부터 시작)
     * @param pageSize     페이지당 사원 수
     * @return 페이지 메타데이터와 사원 목록이 담긴 EmployeePageDTO
     */
    com.ees.eval.dto.EmployeePageDTO searchEmployeesPage(
            String searchName, Long searchDeptId, String searchStatus,
            int pageNum, int pageSize);

    /**
     * 현재 비밀번호를 검증한 후 새 비밀번호로 변경합니다.
     *
     * @param empId           비밀번호를 변경할 사원 식별자
     * @param currentPassword 현재 비밀번호 (평문, 검증용)
     * @param newPassword     변경할 새 비밀번호 (평문, BCrypt 암호화 후 저장)
     * @throws IllegalArgumentException 현재 비밀번호가 일치하지 않을 경우 발생
     */
    void changePassword(Long empId, String currentPassword, String newPassword);

    /**
     * 사원 본인의 이메일과 전화번호를 수정합니다.
     *
     * @param empId 수정할 사원 식별자
     * @param email 새 이메일 주소
     * @param phone 새 전화번호 (평문, 포맷팅은 서비스 내에서 처리)
     */
    void updateContactInfo(Long empId, String email, String phone);

    /**
     * 계정 잠금(login_fail_cnt >= 5)을 해제합니다.
     * login_fail_cnt를 0으로 초기화하여 다음 로그인 시 잠금이 적용되지 않도록 합니다.
     *
     * @param empId 잠금 해제할 사원 식별자
     * @throws IllegalArgumentException 대상 사원이 존재하지 않을 경우 발생
     */
    void unlockAccount(Long empId);

    /**
     * 계정이 잠긴(login_fail_cnt >= 5) 사원 목록을 조회합니다.
     *
     * @return 잠긴 계정 사원 DTO 리스트
     */
    List<EmployeeDTO> getLockedEmployees();

    /**
     * 계정이 잠긴(login_fail_cnt >= 5) 사원 수를 조회합니다.
     *
     * @return 잠긴 계정 사원 수
     */
    long countLockedEmployees();
}
