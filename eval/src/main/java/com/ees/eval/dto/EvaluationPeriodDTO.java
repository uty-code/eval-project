package com.ees.eval.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 평가 차수 정보를 전달하기 위한 불변 데이터 전송 객체(Record)입니다.
 *
 * @param periodId   차수 고유 식별자
 * @param periodYear 평가 대상 연도
 * @param periodName 차수 명칭
 * @param statusCode 상태 코드 (PLANNED, IN_PROGRESS, COMPLETED, CLOSED)
 * @param startDate  평가 시작일
 * @param endDate    평가 종료일
 * @param isDeleted  삭제 여부
 * @param version    낙관적 락 버전
 * @param createdAt  생성 일시
 * @param createdBy  생성자 ID
 * @param updatedAt  수정 일시
 * @param updatedBy  수정자 ID
 */
@Builder
public record EvaluationPeriodDTO(
        Long periodId,

        @NotNull(message = "연도는 필수 입력 항목입니다.") @Min(value = 2000, message = "연도는 2000년 이상이어야 합니다.") @Max(value = 2100, message = "연도는 2100년 이하여야 합니다.") Integer periodYear,

        @NotBlank(message = "차수명은 필수 입력 항목입니다.") String periodName,

        String statusCode,

        @NotNull(message = "시작일은 필수 입력 항목입니다.") LocalDate startDate,

        @NotNull(message = "종료일은 필수 입력 항목입니다.") LocalDate endDate,
        String isDeleted,
        Integer version,
        LocalDateTime createdAt,
        Long createdBy,
        LocalDateTime updatedAt,
        Long updatedBy) {
}
