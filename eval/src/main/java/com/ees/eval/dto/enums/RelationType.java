package com.ees.eval.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 평가 관계 유형을 정의하는 Enum입니다.
 * DB의 relation_type_code 컬럼과 매핑됩니다.
 */
@Getter
@RequiredArgsConstructor
public enum RelationType {
    /** 자기평가 */
    SELF("SELF"),
    
    /** 1차/2차 상급자 평가 */
    MANAGER("MANAGER"),
    
    /** 다면평가 (하급자/동료 평가) */
    SUBORDINATE("SUBORDINATE"),
    
    /** 임원 평가 (최종 등급 확정용) */
    EXECUTIVE("EXECUTIVE");

    private final String code;
}
