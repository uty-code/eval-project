package com.ees.eval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 페이징 처리된 목록과 메타데이터를 함께 반환하기 위한 공통 DTO입니다.
 *
 * @param <T> 목록 요소의 타입
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponseDTO<T> {
    
    private List<T> content;
    private int currentPage;
    private int pageSize;
    private int totalElements;
    private int totalPages;

    /**
     * 기본 생성자 역할을 수행하며 자동으로 totalPages를 계산합니다.
     */
    public static <T> PageResponseDTO<T> of(List<T> content, int currentPage, int pageSize, int totalElements) {
        PageResponseDTO<T> dto = new PageResponseDTO<>();
        dto.setContent(content);
        dto.setCurrentPage(currentPage);
        dto.setPageSize(pageSize);
        dto.setTotalElements(totalElements);
        dto.setTotalPages((int) Math.ceil((double) totalElements / pageSize));
        return dto;
    }
}
