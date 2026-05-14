package com.ees.eval.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 평가 차수의 상태를 정의하는 Enum입니다.
 * PLANNED: 준비, IN_PROGRESS: 진행 중, COMPLETED: 완료, CLOSED: 마감
 */
@Getter
@RequiredArgsConstructor
public enum EvaluationPeriodStatus {
    /** 준비 상태 */
    PLANNED("PLANNED"),
    
    /** 진행 중 */
    IN_PROGRESS("IN_PROGRESS"),
    
    /** 평가 완료 (집계 중) */
    COMPLETED("COMPLETED"),
    
    /** 최종 마감 */
    CLOSED("CLOSED");

    private final String code;

    public static EvaluationPeriodStatus fromCode(String code) {
        for (EvaluationPeriodStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}
