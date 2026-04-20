package com.ees.eval.dto;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 부서 정보를 외부 계층에 전달하기 위한 불변 데이터 전송 객체(Record)입니다.
 * 셀프 JOIN을 통해 가져온 상위 부서명과 부서별 사원 인원수를 포함합니다.
 *
 * @param deptId 부서 고유 식별자
 * @param parentDeptId 상위 부서 ID (최상위 부서일 경우 null)
 * @param leaderId 부서 리더(부서장) 사원 ID (NULL이면 리더 미지정)
 * @param deptName 부서 명칭
 * @param parentDeptName 셀프 JOIN으로 조회한 상위 부서 명칭
 * @param leaderName JOIN으로 조회한 부서 리더 사원 이름
 * @param employeeCount 해당 부서 소속 활성 사원 수
 * @param treeDepth 트리 깊이 (UI 들여쓰기 표현용)
 * @param isActive 사용 여부 (y: 사용중, n: 미사용중)
 * @param isDeleted 삭제 여부 (y/n)
 * @param version 낙관적 락을 위한 버전 번호
 * @param createdAt 생성 일시
 * @param createdBy 생성자 ID
 * @param updatedAt 수정 일시
 * @param updatedBy 수정자 ID
 */
@Builder(toBuilder = true)
public record DepartmentDTO(
    Long deptId,
    Long parentDeptId,
    Long leaderId,
    String deptName,
    String parentDeptName,
    String leaderName,
    Integer employeeCount,
    Integer treeDepth,
    String isActive,
    String isDeleted,
    Integer version,
    LocalDateTime createdAt,
    Long createdBy,
    LocalDateTime updatedAt,
    Long updatedBy
) {}
