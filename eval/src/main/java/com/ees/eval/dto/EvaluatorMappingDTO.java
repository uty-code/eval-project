package com.ees.eval.dto;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 평가자 매핑 정보를 전달하기 위한 불변 데이터 전송 객체(Record)입니다.
 * JOIN을 통해 가져온 피평가자/평가자 이름을 포함합니다.
 *
 * @param mappingId        매핑 고유 식별자
 * @param periodId         소속 차수 ID
 * @param evaluateeId      피평가자 사원 ID
 * @param evaluatorId      평가자 사원 ID
 * @param relationTypeCode 관계 유형 (SELF, SUPERIOR, PEER, SUBORDINATE)
 * @param evaluateeName    JOIN으로 조회한 피평가자 성명
 * @param evaluatorName    JOIN으로 조회한 평가자 성명
 * @param isDeleted        삭제 여부
 * @param version          낙관적 락 버전
 * @param createdAt        생성 일시
 * @param createdBy        생성자 ID
 * @param updatedAt        수정 일시
 * @param updatedBy        수정자 ID
 */
@Builder
public record EvaluatorMappingDTO(
        Long mappingId,
        Long periodId,
        Long evaluateeId,
        Long evaluatorId,
        String relationTypeCode,
        String evaluateeName,
        String evaluatorName,
        String isDeleted,
        Integer version,
        LocalDateTime createdAt,
        Long createdBy,
        LocalDateTime updatedAt,
        Long updatedBy) {
}
