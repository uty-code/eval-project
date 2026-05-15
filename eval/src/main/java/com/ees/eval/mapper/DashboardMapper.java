package com.ees.eval.mapper;

import com.ees.eval.dto.DashboardDtos.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DashboardMapper {

    /**
     * 특정 차수의 전체 평가 대상자 수 조회
     */
    int countTotalEvaluatees(@Param("periodId") Long periodId);

    /**
     * 특정 차수의 최종 확정(FinalGrade 존재) 인원 수 조회
     */
    int countFinalizedEmployees(@Param("periodId") Long periodId);

    /**
     * 특정 차수의 등급 분포 조회
     */
    List<GradeDistributionDTO> getGradeDistribution(@Param("periodId") Long periodId);

    /**
     * 특정 차수의 부서별 평균 점수 조회
     */
    List<DeptAverageScoreDTO> getDeptAverageScores(@Param("periodId") Long periodId);

    /**
     * 최근 최종 확정된 활동 상위 N건 조회
     */
    List<RecentActivityProjectionDTO> getRecentFinalizedActivities(@Param("periodId") Long periodId, @Param("limit") int limit);

    /**
     * [사원용] 본인의 자가평가 진행 상태 조회
     */
    String getSelfEvalStatus(@Param("empId") Long empId, @Param("periodId") Long periodId);

    /**
     * [사원용] 본인이 수행해야 할 평가(동료/상향 등)의 진행 현황 조회
     */
    PeerEvalProgressDTO getPeerEvalProgress(@Param("empId") Long empId, @Param("periodId") Long periodId);

    /**
     * [사원용] 본인의 최근 최종 등급 이력 조회
     */
    List<MyRecentGradeDTO> getMyRecentGrades(@Param("empId") Long empId, @Param("limit") int limit);
}
