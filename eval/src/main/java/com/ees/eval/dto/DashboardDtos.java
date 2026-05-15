package com.ees.eval.dto;

import java.time.LocalDateTime;

/**
 * 대시보드 통계 및 집계 전용 Record DTO 모음입니다.
 * MyBatis ResultType으로 직접 사용되어 성능을 최적화합니다.
 */
public class DashboardDtos {

    /**
     * 등급 분포 집계 DTO
     */
    public record GradeDistributionDTO(
            String gradeCode,
            long count
    ) {}

    /**
     * 부서별 평균 점수 DTO
     */
    public record DeptAverageScoreDTO(
            Long deptId,
            String deptName,
            double avgScore
    ) {}

    /**
     * 최근 확정 활동 DTO
     */
    public record RecentActivityProjectionDTO(
            String evaluateeName,
            String deptName,
            String grade,
            LocalDateTime finalizedAt
    ) {}

    /**
     * 동료평가 진행 현황 DTO
     */
    public record PeerEvalProgressDTO(
            int totalCount,
            int completedCount
    ) {}

    /**
     * 개인 최근 등급 이력 DTO
     */
    public record MyRecentGradeDTO(
            String periodName,
            String grade
    ) {}
}
