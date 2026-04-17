package com.ees.eval.service;

import com.ees.eval.dto.DepartmentDTO;
import com.ees.eval.dto.EmployeeDTO;

import java.util.List;

/**
 * 부서(Department) 관리 업무를 담당하는 서비스 인터페이스입니다.
 * 계층형 트리 구조 조회, 부서별 사원 조회, 인원수 통계 기능을 포함합니다.
 */
public interface DepartmentService {

    /**
     * 부서 ID로 부서 상세 정보를 조회합니다.
     * 상위 부서명과 소속 사원 인원수가 DTO에 함께 포함됩니다.
     *
     * @param deptId 조회할 부서 식별자
     * @return 부서 DTO (상위 부서명, 인원수 포함)
     * @throws IllegalArgumentException 해당 ID의 부서가 존재하지 않을 경우 발생
     */
    DepartmentDTO getDepartmentById(Long deptId);

    /**
     * 전체 부서 목록을 조회합니다.
     *
     * @return 부서 DTO 리스트
     */
    List<DepartmentDTO> getAllDepartments();

    /**
     * 최상위 부서(루트 노드) 목록만 조회합니다.
     *
     * @return 최상위 부서 DTO 리스트
     */
    List<DepartmentDTO> getRootDepartments();

    /**
     * 특정 상위 부서의 직속 하위 부서 목록을 조회합니다.
     *
     * @param parentDeptId 상위 부서 식별자
     * @return 하위 부서 DTO 리스트
     */
    List<DepartmentDTO> getChildDepartments(Long parentDeptId);

    /**
     * 특정 부서에 소속된 사원 목록을 조회합니다.
     * EmployeeService와 연동하여 사원의 권한 정보도 함께 반환됩니다.
     *
     * @param deptId 대상 부서 식별자
     * @return 부서 소속 사원 DTO 리스트
     */
    List<EmployeeDTO> getEmployeesByDeptId(Long deptId);

    /**
     * 신규 부서를 등록합니다.
     *
     * @param departmentDto 생성할 부서 정보 DTO
     * @return 저장 완료된 부서 DTO
     */
    DepartmentDTO createDepartment(DepartmentDTO departmentDto);

    /**
     * 부서 정보를 수정합니다. 낙관적 락으로 동시성을 제어합니다.
     *
     * @param departmentDto 수정할 데이터가 포함된 DTO
     * @return 수정 완료된 부서 DTO
     * @throws com.ees.eval.exception.EesOptimisticLockException 데이터 충돌 시 발생
     */
    DepartmentDTO updateDepartment(DepartmentDTO departmentDto);

    /**
     * 부서를 논리적으로 삭제(Soft Delete)합니다.
     * 소속 사원이 존재하는 경우 삭제를 거부합니다.
     *
     * @param deptId 삭제할 부서 식별자
     * @throws IllegalStateException    소속 사원이 존재하는 부서를 삭제하려 할 경우 발생
     * @throws IllegalArgumentException 대상 부서가 존재하지 않을 경우 발생
     */
    void deleteDepartment(Long deptId);

    /**
     * 부서의 사용 여부(is_active)를 전환합니다.
     * 사용중(y)이면 미사용중(n)으로, 미사용중(n)이면 사용중(y)으로 변경합니다.
     *
     * @param deptId 대상 부서 식별자
     * @throws IllegalArgumentException 대상 부서가 존재하지 않을 경우 발생
     */
    void toggleDepartmentStatus(Long deptId);
}
