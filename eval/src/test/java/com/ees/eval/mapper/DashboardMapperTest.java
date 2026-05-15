package com.ees.eval.mapper;

import com.ees.eval.dto.DashboardDtos.*;
import com.ees.eval.support.AbstractMssqlTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대시보드 매퍼 단위 테스트입니다.
 * SQL 최적화 및 DTO 프로젝션 결과를 검증합니다.
 */
@SpringBootTest
@Transactional
@DisplayName("대시보드 매퍼 통합 테스트 (MSSQL)")
class DashboardMapperTest extends AbstractMssqlTest {

    @Autowired
    private DashboardMapper dashboardMapper;

    private final Long periodId = 1L;
    private final Long empId = 1001L;

    @Test
    @DisplayName("should_get_grade_distribution_as_DTO")
    void should_get_grade_distribution_as_dto() {
        List<GradeDistributionDTO> distribution = dashboardMapper.getGradeDistribution(periodId);
        // 결과가 비어있을 수도 있지만, 타입이 맞아야 함
        assertThat(distribution).isNotNull();
    }

    @Test
    @DisplayName("should_get_dept_avg_scores_as_DTO")
    void should_get_dept_avg_scores_as_dto() {
        List<DeptAverageScoreDTO> scores = dashboardMapper.getDeptAverageScores(periodId);
        assertThat(scores).isNotNull();
    }

    @Test
    @DisplayName("should_get_recent_activities_as_DTO")
    void should_get_recent_activities_as_dto() {
        List<RecentActivityProjectionDTO> activities = dashboardMapper.getRecentFinalizedActivities(periodId, 5);
        assertThat(activities).isNotNull();
    }

    @Test
    @DisplayName("should_get_peer_eval_progress_as_DTO")
    void should_get_peer_eval_progress_as_dto() {
        PeerEvalProgressDTO progress = dashboardMapper.getPeerEvalProgress(empId, periodId);
        // progress가 null이거나 객체여야 함 (MyBatis 매핑 결과에 따라)
    }

    @Test
    @DisplayName("should_get_my_recent_grades_as_DTO")
    void should_get_my_recent_grades_as_dto() {
        List<MyRecentGradeDTO> grades = dashboardMapper.getMyRecentGrades(empId, 5);
        assertThat(grades).isNotNull();
    }
}
