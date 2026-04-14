package com.ees.eval.mapper;

import com.ees.eval.domain.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * departments_51 테이블 및 계층형 구조 데이터에 대한 접근 기능을 정의한 매퍼 인터페이스입니다.
 * 셀프 JOIN을 통한 상위 부서 조회, 하위 부서 조회, 부서별 인원 통계 등을 포함합니다.
 */
@Mapper
public interface DepartmentMapper {

    /**
     * 부서 ID로 부서 정보를 조회합니다.
     *
     * @param deptId 조회할 부서 식별자
     * @return 부서 엔티티를 담은 Optional 객체
     */
    Optional<Department> findById(Long deptId);

    /**
     * 삭제되지 않은 모든 부서 목록을 조회합니다.
     *
     * @return 전체 부서 리스트
     */
    List<Department> findAll();

    /**
     * 최상위 부서(parent_dept_id IS NULL) 목록을 조회합니다.
     *
     * @return 루트 부서 리스트
     */
    List<Department> findRootDepartments();

    /**
     * 특정 상위 부서에 속하는 하위 부서 목록을 조회합니다.
     *
     * @param parentDeptId 상위 부서 식별자
     * @return 직속 하위 부서 리스트
     */
    List<Department> findByParentDeptId(Long parentDeptId);

    /**
     * 상위 부서의 이름을 셀프 JOIN으로 조회합니다.
     *
     * @param deptId 대상 부서 식별자
     * @return 상위 부서 명칭 (최상위 부서인 경우 null)
     */
    String findParentDeptName(Long deptId);

    /**
     * 해당 부서에 소속된 활성 사원 수를 카운트합니다.
     * 평가 시스템에서 부서별 통계 산출에 활용됩니다.
     *
     * @param deptId 대상 부서 식별자
     * @return 활성 사원 인원 수
     */
    int countEmployeesByDeptId(Long deptId);

    /**
     * 새로운 부서 정보를 저장합니다.
     *
     * @param department 저장할 부서 엔티티 객체
     * @return 삽입된 행의 수
     */
    int insert(Department department);

    /**
     * 부서 정보를 수정합니다. 낙관적 락(version) 체크가 동반됩니다.
     *
     * @param department 수정할 부서 정보를 담은 엔티티
     * @return 업데이트된 행의 수 (낙관적 락 실패 시 0)
     */
    int update(Department department);

    /**
     * 특정 부서를 논리적으로 삭제(Soft Delete) 처리합니다.
     *
     * @param deptId 삭제 처리할 부서 식별자
     * @param updatedBy 수정한 사용자 ID
     * @param updatedAt 수정 시각
     * @return 업데이트된 행의 수
     */
    int softDelete(@Param("deptId") Long deptId, @Param("updatedBy") Long updatedBy,
                   @Param("updatedAt") LocalDateTime updatedAt);
}
