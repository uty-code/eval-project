package com.ees.eval.mapper;

import com.ees.eval.domain.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * employees_51 테이블 및 관련 JOIN 데이터에 대한 데이터 접근 기능을 정의한 매퍼 인터페이스입니다.
 * 사원 CRUD와 더불어 직급/권한 조회를 위한 JOIN 쿼리를 포함합니다.
 */
@Mapper
public interface EmployeeMapper {

    /**
     * 다음에 발급될 사원 번호를 미리 조회합니다.
     * @return 예상되는 다음 사원 번호
     */
    Long getNextEmpId();

    /**
     * 사원 ID를 기반으로 사원 정보를 조회합니다. (positions JOIN 포함)
     *
     * @param empId 조회할 사원 식별자
     * @return 사원 엔티티를 담은 Optional 객체
     */
    Optional<Employee> findById(Long empId);

    /**
     * 로그인 아이디(empId)로 사원 정보를 조회합니다.
     * Spring Security 인증 시 사용됩니다.
     *
     * @param empId 조회할 사번
     * @return 사원 엔티티를 담은 Optional 객체
     */
    Optional<Employee> findByIdForAuth(Long empId);

    /**
     * 삭제(퇴사) 여부와 관계없이 사번으로 사원을 조회합니다.
     * 퇴사자 로그인 시도 시 구체적인 안내를 제공하기 위해 사용됩니다.
     *
     * @param empId 조회할 사번
     * @return 사원 엔티티를 담은 Optional 객체
     */
    Optional<Employee> findByIdIncludeDeleted(Long empId);

    /**
     * 삭제되지 않은 모든 사원 목록을 조회합니다.
     *
     * @return 전체 사원 리스트
     */
    List<Employee> findAll();

    /**
     * 검색 조건(이름, 부서, 재직 상태)에 따라 사원 목록을 동적으로 조회합니다.
     *
     * @param searchName   검색할 사원 성명 (부분 일치, null 허용)
     * @param searchDeptId 검색할 부서 ID (null 허용)
     * @param searchStatus 검색할 재직 상태 코드 (null 허용)
     * @return 조건에 해당하는 사원 리스트
     */
    List<Employee> searchEmployees(@Param("searchName") String searchName,
                                   @Param("searchDeptId") Long searchDeptId,
                                   @Param("searchStatus") String searchStatus);

    /**
     * 특정 부서에 소속된 활성 사원 목록을 조회합니다.
     *
     * @param deptId 대상 부서 식별자
     * @return 해당 부서 소속 사원 리스트
     */
    List<Employee> findByDeptId(Long deptId);

    /**
     * 특정 사원이 보유한 권한명(role_name) 목록을 employee_roles + roles JOIN으로 조회합니다.
     *
     * @param empId 대상 사원의 식별자
     * @return 해당 사원의 권한명 문자열 리스트
     */
    List<String> findRoleNamesByEmpId(Long empId);

    /**
     * 새로운 사원 정보를 employees_51 테이블에 저장합니다.
     *
     * @param employee 저장할 사원 엔티티 (비밀번호는 이미 암호화된 상태여야 함)
     * @return 삽입된 행의 수
     */
    int insert(Employee employee);

    /**
     * 사원-권한 매핑 정보를 employee_roles_51 테이블에 저장합니다.
     *
     * @param empId 사원 식별자
     * @param roleId 권한 식별자
     * @param createdBy 생성자 ID
     * @param createdAt 생성 시각
     * @return 삽입된 행의 수
     */
    int insertEmployeeRole(@Param("empId") Long empId, @Param("roleId") Long roleId,
                           @Param("createdBy") Long createdBy, @Param("createdAt") LocalDateTime createdAt);

    /**
     * 기존 사원 정보를 수정합니다. 낙관적 락(version) 체크가 동반됩니다.
     *
     * @param employee 수정할 사원 정보를 담은 엔티티
     * @return 업데이트된 행의 수 (낙관적 락 실패 시 0)
     */
    int update(Employee employee);

    /**
     * 특정 사원을 논리적으로 삭제(Soft Delete) 처리합니다.
     *
     * @param empId 삭제 처리할 사원 식별자
     * @param updatedBy 수정한 사용자 편집자 ID
     * @param updatedAt 수정 시각
     * @return 업데이트된 행의 수
     */
    int softDelete(@Param("empId") Long empId, @Param("updatedBy") Long updatedBy,
                   @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 로그인 실패 횟수 증가
     */
    int incrementLoginFailCnt(Long empId);

    /**
     * 로그인 실패 횟수 초기화
     */
    int resetLoginFailCnt(Long empId);

    /**
     * 승인 대기(PENDING) 상태의 사원 목록을 조회합니다.
     *
     * @return PENDING 상태 사원 리스트
     */
    List<Employee> findPendingEmployees();

    /**
     * 사원의 상태 코드를 변경합니다. (PENDING → EMPLOYED 승인용)
     *
     * @param empId      대상 사원 식별자
     * @param statusCode 변경할 상태 코드
     * @param updatedBy  처리자 ID
     * @param updatedAt  처리 시각
     * @return 업데이트된 행 수
     */
    int updateStatusCode(@Param("empId") Long empId, @Param("statusCode") String statusCode,
                         @Param("updatedBy") Long updatedBy, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 대시보드 인사 현황용으로 가장 최근에 등록된 사원 5명을 조회합니다.
     * 부서명(deptName)과 직급명(positionName)을 LEFT JOIN으로 함께 조회합니다.
     *
     * @return 최신 등록순 상위 5명의 사원 목록
     */
    List<Employee> findTop5RecentWithDetail();

    /**
     * 승인 대기(PENDING) 상태이면서 논리 삭제되지 않은 사원의 수를 조회합니다.
     *
     * @return 승인 대기 중인 사원 수
     */
    long countPendingEmployees();

    /**
     * 재직 상태(EMPLOYED)이면서 논리 삭제되지 않은 사원의 수를 조회합니다.
     *
     * @return 재직 중인 사원 수
     */
    long countActiveEmployees();
}
