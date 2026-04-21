package com.ees.eval.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 평가자 매핑(evaluator_mappings) 테이블에 대응하는 도메인 엔티티 클래스입니다.
 * 특정 평가 차수에서 피평가자(evaluatee)와 평가자(evaluator) 간의 관계를 정의합니다.
 * 관계 유형: SELF(자기), SUPERIOR(상급자), PEER(동료), SUBORDINATE(하급자)
 */
@Getter
@Setter
@NoArgsConstructor
public class EvaluatorMapping extends BaseEntity {

    /** 매핑 고유 식별자 (PK) */
    private Long mappingId;

    /** 평가 차수 식별자 (FK -> evaluation_periods) */
    private Long periodId;

    /** 피평가자(평가를 받는 사원) 식별자 (FK -> employees) */
    private Long evaluateeId;

    /** 평가자(평가를 수행하는 사원) 식별자 (FK -> employees) */
    private Long evaluatorId;

    /** 평가 관계 유형 코드 (SELF, SUPERIOR, PEER, SUBORDINATE) */
    private String relationTypeCode;

    /** 피평가자 성명 (MyBatis JOIN용) */
    private String evaluateeName;

    /** 평가자 성명 (MyBatis JOIN용) */
    private String evaluatorName;

    /**
     * 평가자 매핑 엔티티를 생성하는 빌더 메서드입니다.
     *
     * @param mappingId        매핑 ID
     * @param periodId         차수 ID
     * @param evaluateeId      피평가자 ID
     * @param evaluatorId      평가자 ID
     * @param relationTypeCode 관계 유형 코드
     */
    @Builder
    public EvaluatorMapping(Long mappingId, Long periodId, Long evaluateeId,
            Long evaluatorId, String relationTypeCode, String evaluateeName, String evaluatorName) {
        this.mappingId = mappingId;
        this.periodId = periodId;
        this.evaluateeId = evaluateeId;
        this.evaluatorId = evaluatorId;
        this.relationTypeCode = relationTypeCode;
        this.evaluateeName = evaluateeName;
        this.evaluatorName = evaluatorName;
    }
}
