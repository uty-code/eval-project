package com.ees.eval.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 평가 차수(evaluation_periods) 테이블에 대응하는 도메인 엔티티 클래스입니다.
 * 연도별 평가 차수(기수)를 관리하며, 시작일/종료일과 상태(STATUS)를 포함합니다.
 * 한 시점에 하나의 차수만 '진행 중(IN_PROGRESS)' 상태를 가질 수 있습니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class EvaluationPeriod extends BaseEntity {

    /** 평가 차수 고유 식별자 (PK) */
    private Long periodId;

    /** 평가 대상 연도 */
    private Integer periodYear;

    /** 평가 차수 명칭 (예: 2026년 상반기 평가) */
    private String periodName;

    /** 평가 상태 코드 (PLANNED, IN_PROGRESS, COMPLETED, CLOSED) */
    private String statusCode;

    /** 평가 시작일 */
    private LocalDate startDate;

    /** 평가 종료일 */
    private LocalDate endDate;

    /**
     * 평가 차수 엔티티를 생성하는 빌더 메서드입니다.
     *
     * @param periodId 차수 ID
     * @param periodYear 평가 연도
     * @param periodName 차수 명칭
     * @param statusCode 상태 코드
     * @param startDate 시작일
     * @param endDate 종료일
     */
    @Builder
    public EvaluationPeriod(Long periodId, Integer periodYear, String periodName,
                            String statusCode, LocalDate startDate, LocalDate endDate) {
        this.periodId = periodId;
        this.periodYear = periodYear;
        this.periodName = periodName;
        this.statusCode = statusCode;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
