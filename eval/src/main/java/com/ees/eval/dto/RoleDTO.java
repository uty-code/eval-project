package com.ees.eval.dto;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 권한 정보를 관리하기 위한 데이터 전송 객체(Record)입니다.
 * Java 21의 record 타입을 사용하여 데이터의 불변성을 보장합니다.
 *
 * @param roleId 권한 고유 식별자
 * @param roleName 권한 명칭 (예: ROLE_ADMIN, ROLE_USER)
 * @param description 권한에 대한 상세 설명
 * @param isDeleted 삭제 여부 (y: 삭제됨, n: 삭제되지 않음)
 * @param version 데이터 수정을 위한 버전 정보 (낙관적 락 활용)
 * @param createdAt 생성 일시
 * @param createdBy 생성자 ID
 * @param updatedAt 수정 일시
 * @param updatedBy 수정자 ID
 */
@Builder
public record RoleDTO(
    Long roleId,
    String roleName,
    String description,
    String isDeleted,
    Integer version,
    LocalDateTime createdAt,
    Long createdBy,
    LocalDateTime updatedAt,
    Long updatedBy
) {}
