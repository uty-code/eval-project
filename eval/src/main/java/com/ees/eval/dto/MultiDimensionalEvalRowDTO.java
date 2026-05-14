package com.ees.eval.dto;

import com.ees.eval.dto.enums.MultiDimensionalEvalCtaType;
import com.ees.eval.dto.enums.MultiDimensionalEvalStatus;
import lombok.Builder;

/**
 * 다면평가 목록의 한 행을 구성하는 데이터를 정의한 DTO입니다.
 */
@Builder
public record MultiDimensionalEvalRowDTO(
    Long mappingId,
    Long evaluateeId,
    String empId,
    String name,
    Long deptId,
    String deptName,
    String titleName,
    String relationName,
    MultiDimensionalEvalStatus statusType,
    String displayStatus,
    MultiDimensionalEvalCtaType ctaType,
    String displayCta,
    boolean canWrite,
    boolean canView,
    java.math.BigDecimal score
) {
}
