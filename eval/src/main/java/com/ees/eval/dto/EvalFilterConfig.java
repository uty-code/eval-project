package com.ees.eval.dto;

import lombok.Builder;
import java.util.List;

/**
 * 공통 평가 필터 바 UI의 설정을 계층적으로 안전하게 관리하는 불변 구성 DTO(Record)입니다.
 * 필터의 각 파트를 독립된 레코드로 분할 합성하여 설계 책임을 분리하고 무분별한 확장을 방지합니다.
 *
 * @param actionUrl 필터 적용(조회) 시 요청을 전송할 URL
 * @param htmxTarget HTMX가 갱신할 대상 DOM 요소 ID (예: #eval-table-container)
 * @param htmxSelect HTMX가 응답받은 HTML에서 추출해 갱신할 부분 영역 ID
 * @param periodFilter 평가 차수(기수) 관련 필터 설정
 * @param deptFilter 부서 조회 트리 관련 필터 설정
 * @param statusFilter 평가 상태 필터 설정
 * @param keywordFilter 검색어(사원명/사번) 필터 설정
 * @param hiddenParams 폼 제출 시 항상 동반되어야 할 숨김 파라미터 리스트
 * @param showReset 초기화 버튼 표시 여부
 */
@Builder
public record EvalFilterConfig(
    String actionUrl,
    String htmxTarget,
    String htmxSelect,
    PeriodFilter periodFilter,
    DeptFilter deptFilter,
    StatusFilter statusFilter,
    KeywordFilter keywordFilter,
    List<HiddenParam> hiddenParams,
    boolean showReset
) {
    /**
     * 평가 차수(기수) 필터 관련 설정 객체입니다.
     *
     * @param show 차수 필터 노출 여부
     * @param periods 전체 활성 평가 차수 목록
     * @param selectedId 선택된 평가 차수 식별자
     * @param showAll '전체 차수' 조회 옵션 제공 여부
     */
    @Builder
    public record PeriodFilter(
        boolean show,
        List<EvaluationPeriodDTO> periods,
        Long selectedId,
        boolean showAll
    ) {}

    /**
     * 부서 트리 필터 관련 설정 객체입니다.
     *
     * @param show 부서 트리 필터 노출 여부
     * @param departments 사용자의 권한 및 조직도 규칙에 의해 프루닝된 계층형 부서 트리 목록
     * @param selectedId 선택된 부서 식별자
     */
    @Builder
    public record DeptFilter(
        boolean show,
        List<DepartmentDTO> departments,
        Long selectedId
    ) {}

    /**
     * 평가 상태 필터 관련 설정 객체입니다.
     *
     * @param show 상태 필터 노출 여부
     * @param options 상태에 표시될 어댑터형 필터 옵션 리스트
     * @param selectedValue 선택된 상태 코드 값
     */
    @Builder
    public record StatusFilter(
        boolean show,
        List<FilterOption> options,
        String selectedValue
    ) {}

    /**
     * 검색어 필터 관련 설정 객체입니다.
     *
     * @param show 검색 필터 노출 여부
     * @param keyword 입력된 검색 키워드
     * @param placeholder 검색창에 표시될 플레이스홀더 문구
     */
    @Builder
    public record KeywordFilter(
        boolean show,
        String keyword,
        String placeholder
    ) {}
}
