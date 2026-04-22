package com.ees.eval.dto;

import java.util.List;

/**
 * 사원 목록 페이지네이션 정보를 담는 불변 데이터 전송 객체(Record)입니다.
 * 사원 목록과 페이지 메타데이터를 하나의 객체로 뷰에 전달합니다.
 *
 * @param employees  현재 페이지의 사원 목록
 * @param pageNum    현재 페이지 번호 (1부터 시작)
 * @param pageSize   페이지당 표시할 사원 수
 * @param totalCount 검색 조건에 해당하는 전체 사원 수
 * @param totalPages 전체 페이지 수
 * @param startPage  페이지 번호 그룹의 시작 번호 (예: 1)
 * @param endPage    페이지 번호 그룹의 끝 번호 (예: 10)
 */
public record EmployeePageDTO(
        List<EmployeeDTO> employees,
        int pageNum,
        int pageSize,
        long totalCount,
        int totalPages,
        int startPage,
        int endPage
) {
    /**
     * 페이지네이션 메타데이터를 계산하여 EmployeePageDTO를 생성합니다.
     *
     * @param employees  현재 페이지 사원 목록
     * @param pageNum    현재 페이지 번호
     * @param pageSize   페이지 크기
     * @param totalCount 전체 건수
     * @return 페이지 메타데이터가 포함된 EmployeePageDTO
     */
    public static EmployeePageDTO of(List<EmployeeDTO> employees, int pageNum, int pageSize, long totalCount) {
        // 전체 페이지 수 계산 (올림 처리)
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        totalPages = Math.max(totalPages, 1); // 결과가 없어도 최소 1페이지

        // 페이지 그룹 계산 (화면에 한 번에 표시할 페이지 버튼 수: 10개)
        int pageGroupSize = 10;
        int startPage = ((pageNum - 1) / pageGroupSize) * pageGroupSize + 1;
        int endPage   = Math.min(startPage + pageGroupSize - 1, totalPages);

        return new EmployeePageDTO(employees, pageNum, pageSize, totalCount, totalPages, startPage, endPage);
    }

    /** 이전 페이지 존재 여부 */
    public boolean hasPrevious() {
        return pageNum > 1;
    }

    /** 다음 페이지 존재 여부 */
    public boolean hasNext() {
        return pageNum < totalPages;
    }

    /** 이전 그룹 존재 여부 */
    public boolean hasPreviousGroup() {
        return startPage > 1;
    }

    /** 다음 그룹 존재 여부 */
    public boolean hasNextGroup() {
        return endPage < totalPages;
    }

    /** 첫 페이지 여부 */
    public boolean isFirst() {
        return pageNum == 1;
    }

    /** 마지막 페이지 여부 */
    public boolean isLast() {
        return pageNum == totalPages;
    }
}
