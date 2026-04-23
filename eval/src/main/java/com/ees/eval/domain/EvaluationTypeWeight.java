package com.ees.eval.domain;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 평가 유형별 가중치(Type Weight)를 정의하는 도메인입니다.
 * 이미지 디자인에 따라 COMPETENCY, PERFORMANCE 등 유형별 비중을 100% 기준으로 설정합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationTypeWeight {
    private Long weightId;
    private Long periodId;
    private Long deptId;
    private String targetRoleCode;
    private String elementTypeCode;
    private BigDecimal weight;
    
    private String isDeleted;
    private Integer version;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;

    public void prePersist() {
        this.isDeleted = "n";
        this.version = 0;
        this.createdAt = LocalDateTime.now();
    }

    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
