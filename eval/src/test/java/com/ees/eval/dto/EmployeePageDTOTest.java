package com.ees.eval.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeePageDTOTest {

    @Test
    @DisplayName("성공: 기본 페이지네이션 메타데이터가 정확하게 계산되어야 한다")
    void of_WithNormalInputs_CalculatesCorrectMetadata() {
        // given
        List<EmployeeDTO> employees = Collections.emptyList();
        int pageNum = 1;
        int pageSize = 10;
        long totalCount = 105;

        // when
        EmployeePageDTO result = EmployeePageDTO.of(employees, pageNum, pageSize, totalCount);

        // then
        assertThat(result.pageNum()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.totalCount()).isEqualTo(105);
        assertThat(result.totalPages()).isEqualTo(11); // 10.5 -> 11
        assertThat(result.startPage()).isEqualTo(1);
        assertThat(result.endPage()).isEqualTo(10);
        
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPreviousGroup()).isFalse();
        assertThat(result.hasNextGroup()).isTrue();
    }

    @Test
    @DisplayName("성공: 데이터가 없는 경우 최소 1페이지와 올바른 플래그를 반환해야 한다")
    void of_WithZeroTotalCount_ReturnsDefaultMetadata() {
        // given
        List<EmployeeDTO> employees = Collections.emptyList();
        int pageNum = 1;
        int pageSize = 10;
        long totalCount = 0;

        // when
        EmployeePageDTO result = EmployeePageDTO.of(employees, pageNum, pageSize, totalCount);

        // then
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.startPage()).isEqualTo(1);
        assertThat(result.endPage()).isEqualTo(1);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isTrue();
        assertThat(result.hasPrevious()).isFalse();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("성공: 11페이지 요청 시 두 번째 그룹(11~20)이 계산되어야 한다")
    void of_WhenPageNumIsEleven_CalculatesSecondGroup() {
        // given
        List<EmployeeDTO> employees = Collections.emptyList();
        int pageNum = 11;
        int pageSize = 10;
        long totalCount = 200;

        // when
        EmployeePageDTO result = EmployeePageDTO.of(employees, pageNum, pageSize, totalCount);

        // then
        assertThat(result.startPage()).isEqualTo(11);
        assertThat(result.endPage()).isEqualTo(20);
        assertThat(result.hasPreviousGroup()).isTrue();
        assertThat(result.hasNextGroup()).isFalse();
    }

    @Test
    @DisplayName("성공: 전체 건수가 페이지 크기의 배수인 경우 총 페이지 수가 정확해야 한다")
    void of_WithExactMultipleTotalCount_CalculatesCorrectTotalPages() {
        // given
        List<EmployeeDTO> employees = Collections.emptyList();
        int pageNum = 1;
        int pageSize = 10;
        long totalCount = 100;

        // when
        EmployeePageDTO result = EmployeePageDTO.of(employees, pageNum, pageSize, totalCount);

        // then
        assertThat(result.totalPages()).isEqualTo(10);
        assertThat(result.isLast()).isFalse();
        
        // when: 10페이지 요청
        EmployeePageDTO lastPage = EmployeePageDTO.of(employees, 10, pageSize, totalCount);
        assertThat(lastPage.isLast()).isTrue();
    }

    @Test
    @DisplayName("상공: 전체 페이지 범위를 벗어난 페이지 요청 시에도 올바른 메타데이터를 반환해야 한다")
    void of_WhenPageNumExceedsTotalPages_ReturnsSafeMetadata() {
        // given: 50건, 크기 10 -> 총 5페이지
        List<EmployeeDTO> employees = Collections.emptyList();
        int pageNum = 10;
        int pageSize = 10;
        long totalCount = 50;

        // when
        EmployeePageDTO result = EmployeePageDTO.of(employees, pageNum, pageSize, totalCount);

        // then
        assertThat(result.totalPages()).isEqualTo(5);
        assertThat(result.pageNum()).isEqualTo(10);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.isLast()).isFalse(); // 10 != 5
    }

    @Test
    @DisplayName("레코드 특성: 동일한 데이터를 가진 객체는 서로 동등해야 한다")
    void recordEqualityTest() {
        // given
        List<EmployeeDTO> employees = Collections.emptyList();
        EmployeePageDTO dto1 = EmployeePageDTO.of(employees, 1, 10, 100);
        EmployeePageDTO dto2 = EmployeePageDTO.of(employees, 1, 10, 100);

        // then
        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        assertThat(dto1.toString()).contains("pageNum=1", "totalCount=100");
    }
}
