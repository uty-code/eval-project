package com.ees.eval.domain;

import lombok.*;

import java.math.BigDecimal;

/**
 * evaluations_51 테이블에 대응하는 도메인 클래스입니다.
 * 피평가자의 각 평가요소에 대한 점수, 코멘트, 확정 상태를 관리합니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evaluation extends BaseEntity {

    private Long evalId;          // PK
    private Long mappingId;       // 평가자 매핑 ID (FK)
    private Long elementId;       // 평가 요소 ID (FK)
    private BigDecimal score;     // 평가 점수
    private BigDecimal oldScore;  // 이전 점수 (변경 이력용)
    private String reason;        // 점수 산정 이유
    private String comments;      // 서술형 코멘트
    private BigDecimal totalScore;        // 합산 점수
    private String gradeCode;             // 등급 코드 (A/B/C 등)
    private String confirmStatusCode;     // 확정 상태 코드 (DRAFT/SUBMITTED/CONFIRMED)
}
