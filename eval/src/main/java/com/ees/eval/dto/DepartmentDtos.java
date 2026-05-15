package com.ees.eval.dto;

/**
 * 부서 시스템 성능 최적화 및 프로젝션을 위한 Record DTO 모음입니다.
 */
public class DepartmentDtos {

    /**
     * 부서 상세 정보 조회를 위한 DTO
     * 1회의 JOIN 쿼리로 부모 부서명, 리더명, 직원 수를 포함합니다.
     */
    public record DepartmentDetailDTO(
        Long deptId,
        Long parentDeptId,
        Long leaderId,
        String deptName,
        String parentDeptName,
        String leaderName,
        int employeeCount,
        String isActive,
        String isDeleted,
        Integer version,
        java.time.LocalDateTime createdAt,
        Long createdBy
    ) {}

    /**
     * Select Box 등에서 사용되는 경량 부서 DTO
     */
    public record SimpleDepartmentDTO(
        Long deptId,
        String deptName
    ) {}

    /**
     * 사원의 권한 정보를 담는 DTO (Map 대체용)
     */
    public record EmployeeRoleDTO(
        Long empId,
        String roleName
    ) {}
}
