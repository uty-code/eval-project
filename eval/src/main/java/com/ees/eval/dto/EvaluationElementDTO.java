package com.ees.eval.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 평가 항목 정보를 전달하기 위한 불변 데이터 전송 객체(Record)입니다.
 *
 * @param elementId       항목 고유 식별자
 * @param periodId        소속 차수 ID
 * @param elementTypeCode 항목 유형 (PERFORMANCE: 성과, COMPETENCY: 역량)
 * @param elementName     항목 명칭
 * @param maxScore        최대 점수
 * @param weight          가중치 (전체 합 100)
 * @param isDeleted       삭제 여부
 * @param version         낙관적 락 버전
 * @param createdAt       생성 일시
 * @param createdBy       생성자 ID
 * @param updatedAt       수정 일시
 * @param updatedBy       수정자 ID
 */
@Builder
public record EvaluationElementDTO(
        Long elementId,
        Long periodId,
        String elementTypeCode,
        String elementName,
        BigDecimal maxScore,
        BigDecimal weight,
        String isDeleted,
        Integer version,
        LocalDateTime createdAt,
        Long createdBy,
        LocalDateTime updatedAt,
        Long updatedBy) {
}
