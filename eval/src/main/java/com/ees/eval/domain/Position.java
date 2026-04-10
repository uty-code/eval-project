package com.ees.eval.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 사원의 직급 정보를 나타내는 Entity 클래스입니다.
 * BaseEntity를 상속하여 감사 이력을 남깁니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Position extends BaseEntity {
    private Long positionId;
    private String positionName;
    private Integer hierarchyLevel;
    private BigDecimal weightBase;

    /**
     * 직급 엔티티 생성을 위한 빌더 메서드입니다.
     *
     * @param positionId 직급 식별용 고유 ID
     * @param positionName 직급 명 (예: 사원, 대리 등)
     * @param hierarchyLevel 계층 순위 (낮은 숫자가 높은 직급을 나타냄)
     * @param weightBase 다면 평가 시 직급별 반영 가중치 보정값
     */
    @Builder
    public Position(Long positionId, String positionName, Integer hierarchyLevel, BigDecimal weightBase) {
        this.positionId = positionId;
        this.positionName = positionName;
        this.hierarchyLevel = hierarchyLevel;
        this.weightBase = weightBase;
    }
}
