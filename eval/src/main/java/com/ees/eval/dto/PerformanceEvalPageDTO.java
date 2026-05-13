package com.ees.eval.dto;

import java.util.List;

public record PerformanceEvalPageDTO(
    List<PerformanceEvalRowDTO> tasks,
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
