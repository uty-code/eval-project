package com.ees.eval.dto;

import com.ees.eval.dto.enums.MyEvaluationCtaType;
import com.ees.eval.dto.enums.MyEvaluationStatus;
import lombok.Builder;

/**
 * 나의 자가평가 목록의 한 행을 구성하는 데이터를 정의한 DTO입니다.
 */
@Builder
public record MyEvaluationRowDTO(
    Long mappingId,
    Long periodId,
    String periodName,
    String periodYear,
    Long empId,
    String name,
    String deptName,
    String titleName,
    MyEvaluationStatus statusType,
    String displayStatus,
    MyEvaluationCtaType ctaType,
    String displayCta,
    boolean isLocked,
    boolean isSubmitted,
    java.math.BigDecimal score
) {
}
