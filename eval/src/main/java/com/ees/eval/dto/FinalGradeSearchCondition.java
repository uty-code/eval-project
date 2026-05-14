package com.ees.eval.dto;

import org.springframework.util.StringUtils;

/**
 * 최종 등급 확정 목록 조회를 위한 검색 조건을 담는 DTO입니다.
 */
public record FinalGradeSearchCondition(
        Long periodId,
        Long deptId,
        String search,
        String tab,
        String status
) {
    /**
     * 검색어를 정규화하여 반환합니다. (공백 제거 및 소문자 변환)
     *
     * @return 정규화된 검색어, 없으면 null
     */
    public String getNormalizedSearch() {
        if (!StringUtils.hasText(search)) {
            return null;
        }
        return search.trim().toLowerCase();
    }

    /**
     * 검색어가 유효한지 확인합니다.
     */
    public boolean hasSearch() {
        return StringUtils.hasText(search);
    }
}
