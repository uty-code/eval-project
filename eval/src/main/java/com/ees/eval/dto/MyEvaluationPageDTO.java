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
    /**
     * 현재 페이지 번호를 반환합니다 (pageNum 프래그먼트 호환용).
     * @return 현재 페이지 번호
     */
    public int pageNum() {
        return currentPage;
    }

    /**
     * 페이지 그룹의 시작 번호를 계산하여 반환합니다.
     * @return 시작 페이지 번호
     */
    public int startPage() {
        int pageGroupSize = 10;
        return ((currentPage - 1) / pageGroupSize) * pageGroupSize + 1;
    }

    /**
     * 페이지 그룹의 끝 번호를 계산하여 반환합니다.
     * @return 끝 페이지 번호
     */
    public int endPage() {
        int startPage = startPage();
        return Math.min(startPage + 9, totalPages);
    }

    /**
     * 이전 페이지가 존재하는지 여부를 반환합니다.
     * @return 이전 페이지 존재 여부
     */
    public boolean hasPrevious() {
        return currentPage > 1;
    }

    /**
     * 다음 페이지가 존재하는지 여부를 반환합니다.
     * @return 다음 페이지 존재 여부
     */
    public boolean hasNext() {
        return currentPage < totalPages;
    }

    /**
     * 첫 페이지 여부를 반환합니다.
     * @return 첫 페이지 여부
     */
    public boolean isFirst() {
        return currentPage == 1;
    }

    /**
     * 마지막 페이지 여부를 반환합니다.
     * @return 마지막 페이지 여부
     */
    public boolean isLast() {
        return currentPage == totalPages;
    }
}

