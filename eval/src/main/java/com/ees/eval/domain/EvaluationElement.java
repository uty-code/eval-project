package com.ees.eval.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 평가 항목(evaluation_elements) 테이블에 대응하는 도메인 엔티티 클래스입니다.
 * 각 평가 차수에 귀속되며 '성과(PERFORMANCE)' 또는 '역량(COMPETENCY)' 구분을 갖습니다.
 * 동일 차수 내 모든 항목의 가중치(weight) 합은 100이어야 합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class EvaluationElement extends BaseEntity {

    /** 평가 항목 고유 식별자 (PK) */
    private Long elementId;

    /** 소속 평가 차수 식별자 (FK -> evaluation_periods) */
    private Long periodId;

    /** 항목 유형 코드 (PERFORMANCE: 성과, COMPETENCY: 역량) */
    private String elementTypeCode;

    /** 평가 항목 명칭 */
    private String elementName;

    /** 해당 항목의 최대 점수 */
    private BigDecimal maxScore;

    /** 해당 항목의 가중치 (전체 합 = 100) */
    private BigDecimal weight;

    /**
     * 평가 항목 엔티티를 생성하는 빌더 메서드입니다.
     *
     * @param elementId 항목 ID
     * @param periodId 차수 ID
     * @param elementTypeCode 항목 유형 코드
     * @param elementName 항목 명칭
     * @param maxScore 최대 점수
     * @param weight 가중치
     */
    @Builder
    public EvaluationElement(Long elementId, Long periodId, String elementTypeCode,
                             String elementName, BigDecimal maxScore, BigDecimal weight) {
        this.elementId = elementId;
        this.periodId = periodId;
        this.elementTypeCode = elementTypeCode;
        this.elementName = elementName;
        this.maxScore = maxScore;
        this.weight = weight;
    }
}
