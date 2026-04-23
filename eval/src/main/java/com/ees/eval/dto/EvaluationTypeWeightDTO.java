package com.ees.eval.dto;

import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 평가 유형별 가중치 정보를 전달하는 DTO입니다.
 */
@Builder
public record EvaluationTypeWeightDTO(
    Long weightId,
    Long periodId,
    Long deptId,
    String targetRoleCode,
    String elementTypeCode,
    BigDecimal weight,
    String isDeleted,
    Integer version,
    LocalDateTime createdAt,
    Long createdBy,
    LocalDateTime updatedAt,
    Long updatedBy
) {}
