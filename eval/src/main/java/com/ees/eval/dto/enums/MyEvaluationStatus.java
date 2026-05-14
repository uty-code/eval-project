package com.ees.eval.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 자가평가 상태를 정의하는 이넘입니다.
 */
@Getter
@RequiredArgsConstructor
public enum MyEvaluationStatus {
    WAITING("작성 대기", "badge bg-secondary"),
    IN_PROGRESS("작성 중", "badge bg-info"),
    SUBMITTED("제출 완료", "badge bg-success");

    private final String description;
    private final String badgeClass;
}
