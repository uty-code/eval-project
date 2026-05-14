package com.ees.eval.dto;

import java.util.List;

/**
 * 다면평가 페이징 정보를 담는 DTO입니다.
 */
public record MultiDimensionalEvalPageDTO(
    List<MultiDimensionalEvalRowDTO> tasks,
    int currentPage,
    int totalPages,
    long totalCount,
    int pageSize
) {
    public boolean hasPrevious() {
        return currentPage > 1;
    }

    public boolean hasNext() {
        return currentPage < totalPages;
    }
}
