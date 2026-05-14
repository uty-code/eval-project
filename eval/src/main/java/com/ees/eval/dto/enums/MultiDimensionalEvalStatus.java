package com.ees.eval.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 다면평가 진행 상태를 관리하는 Enum입니다.
 */
@Getter
@RequiredArgsConstructor
public enum MultiDimensionalEvalStatus {
    WAITING("평가 대기"),
    IN_PROGRESS("진행 중"),
    SUBMITTED("평가 완료");

    private final String description;
}
