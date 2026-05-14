package com.ees.eval.dto;

import java.util.List;

/**
 * 나의 자가평가 목록 페이징 결과를 담는 DTO입니다.
 */
public record MyEvaluationPageDTO(
    List<MyEvaluationRowDTO> content,
    int currentPage,
    int totalPages,
    long totalElements,
    int pageSize
) {
}
