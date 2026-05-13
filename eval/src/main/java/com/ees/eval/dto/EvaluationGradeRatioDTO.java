package com.ees.eval.dto;

import lombok.Builder;

/**
 * 상대평가 등급 배분 비율을 담는 DTO
 */
@Builder
public record EvaluationGradeRatioDTO(
    Long ratioId,
    Long periodId,
    Long deptId,
    Integer gradeSRatio,
    Integer gradeARatio,
    Integer gradeBRatio,
    Integer gradeCRatio,
    Integer gradeDRatio
) {
    public static EvaluationGradeRatioDTO defaultRatio(Long periodId, Long deptId) {
        return EvaluationGradeRatioDTO.builder()
                .periodId(periodId)
                .deptId(deptId)
                .gradeSRatio(10)
                .gradeARatio(20)
                .gradeBRatio(40)
                .gradeCRatio(20)
                .gradeDRatio(10)
                .build();
    }
}
