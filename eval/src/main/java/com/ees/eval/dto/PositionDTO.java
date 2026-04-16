package com.ees.eval.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 직급 정보를 관리하기 위한 데이터 전송 객체(Record)입니다.
 * Java 21의 record 타입을 활용하여 불변성을 유지합니다.
 *
 * @param positionId     직급 고유 식별자
 * @param positionName   직급 명칭 (예: 사원, 대리, 과장)
 * @param hierarchyLevel 직급 계층 수준 (계층 구조 정렬 시 활용)
 * @param weightBase     평가 시 가중치 기준값
 * @param isDeleted      삭제 여부
 * @param version        데이터 수정 버전 (낙관적 락 활용)
 * @param createdAt      생성 일시
 * @param createdBy      생성자 ID
 * @param updatedAt      수정 일시
 * @param updatedBy      수정자 ID
 */
@Builder
public record PositionDTO(
        Long positionId,
        String positionName,
        Integer hierarchyLevel,
        BigDecimal weightBase,
        String isDeleted,
        Integer version,
        LocalDateTime createdAt,
        Long createdBy,
        LocalDateTime updatedAt,
        Long updatedBy) {
}
