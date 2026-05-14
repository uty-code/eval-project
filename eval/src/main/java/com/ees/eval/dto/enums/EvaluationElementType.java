package com.ees.eval.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 평가 요소 유형을 정의하는 Enum입니다.
 * PERFORMANCE: 성과평가, COMPETENCY: 역량평가, MULTI_DIMENSIONAL: 다면평가
 */
@Getter
@RequiredArgsConstructor
public enum EvaluationElementType {
    /** 성과평가 */
    PERFORMANCE("PERFORMANCE"),
    
    /** 역량평가 */
    COMPETENCY("COMPETENCY"),
    
    /** 다면평가 */
    MULTI_DIMENSIONAL("MULTI_DIMENSIONAL");

    private final String code;

    /**
     * 코드 값으로 Enum 상수를 찾습니다.
     * @param code 코드 값
     * @return 매칭되는 Enum 상수, 없으면 null
     */
    public static EvaluationElementType fromCode(String code) {
        for (EvaluationElementType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
