package com.ees.eval.domain;

import lombok.*;
import java.math.BigDecimal;

/**
 * 평가 유형별 가중치(Type Weight)를 정의하는 도메인입니다.
 * BaseEntity를 상속하여 감사(Audit) 필드를 제공받습니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class EvaluationTypeWeight extends BaseEntity {
    private Long weightId;
    private Long periodId;
    private Long deptId;
    private String targetRoleCode;
    private String elementTypeCode;
    private BigDecimal weight;

    @Builder
    public EvaluationTypeWeight(Long weightId, Long periodId, Long deptId, 
                              String targetRoleCode, String elementTypeCode, BigDecimal weight) {
        this.weightId = weightId;
        this.periodId = periodId;
        this.deptId = deptId;
        this.targetRoleCode = targetRoleCode;
        this.elementTypeCode = elementTypeCode;
        this.weight = weight;
    }
}
