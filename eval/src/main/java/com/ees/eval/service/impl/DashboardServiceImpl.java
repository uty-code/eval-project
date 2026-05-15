package com.ees.eval.service.impl;

import com.ees.eval.dto.DashboardDtos.*;
import com.ees.eval.dto.DashboardStatsDTO;
import com.ees.eval.dto.EmployeeDashboardDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.RecentActivityDTO;
import com.ees.eval.dto.EvaluationResultDTO;
import com.ees.eval.mapper.DashboardMapper;
import com.ees.eval.service.DashboardService;
import com.ees.eval.service.DepartmentService;
import com.ees.eval.service.EmployeeService;
import com.ees.eval.service.EvaluationPeriodService;
import com.ees.eval.service.EvaluationResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;
    private final EmployeeService employeeService;
    private final DepartmentService departmentService;
    private final EvaluationPeriodService periodService;
    private final EvaluationResultService resultService;

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats() {
        // 1. 기초 통계
        long empCount = employeeService.countActiveEmployees();
        long deptCount = departmentService.countActiveDepartments();

        // 2. 현재 진행 중인 차수 조회
        List<EvaluationPeriodDTO> inProgressPeriods = periodService.getInProgressPeriods();
        if (inProgressPeriods.isEmpty()) {
            return DashboardStatsDTO.builder()
                    .employeeCount(empCount)
                    .departmentCount(deptCount)
                    .activePeriodName("진행 중인 차수 없음")
                    .completionRate(0.0)
                    .gradeDistribution(new HashMap<>())
                    .deptAverageScores(new HashMap<>())
                    .recentActivities(List.of())
                    .build();
        }

        EvaluationPeriodDTO activePeriod = inProgressPeriods.get(0);
        Long periodId = activePeriod.periodId();

        // 3. 완료율 계산
        int totalEvaluatees = dashboardMapper.countTotalEvaluatees(periodId);
        int finalizedCount = dashboardMapper.countFinalizedEmployees(periodId);
        double completionRate = totalEvaluatees > 0 ? (double) finalizedCount / totalEvaluatees * 100 : 0.0;

        // 4. 등급 분포
        List<GradeDistributionDTO> gradeList = dashboardMapper.getGradeDistribution(periodId);
        Map<String, Long> gradeDistribution = gradeList.stream()
                .collect(Collectors.toMap(
                        GradeDistributionDTO::gradeCode,
                        GradeDistributionDTO::count,
                        (a, b) -> a
                ));

        // 5. 부서별 평균 점수
        List<DeptAverageScoreDTO> deptAvgList = dashboardMapper.getDeptAverageScores(periodId);
        Map<String, Double> deptAverageScores = deptAvgList.stream()
                .filter(d -> d.deptName() != null && !d.deptName().endsWith("본부") && !d.deptName().endsWith("부서"))
                .collect(Collectors.toMap(
                        DeptAverageScoreDTO::deptName,
                        DeptAverageScoreDTO::avgScore,
                        (a, b) -> a
                ));

        // 6. 최근 활동
        List<RecentActivityProjectionDTO> activityList = dashboardMapper.getRecentFinalizedActivities(periodId, 5);
        List<RecentActivityDTO> recentActivities = activityList.stream()
                .map(a -> RecentActivityDTO.builder()
                        .evaluateeName(a.evaluateeName())
                        .deptName(a.deptName())
                        .grade(a.grade())
                        .activityType("평가 확정")
                        .activityTime(a.finalizedAt())
                        .build())
                .collect(Collectors.toList());

        return DashboardStatsDTO.builder()
                .employeeCount(empCount)
                .departmentCount(deptCount)
                .activePeriodName(activePeriod.periodName())
                .totalEvaluatees(totalEvaluatees)
                .finalizedCount(finalizedCount)
                .completionRate(completionRate)
                .gradeDistribution(gradeDistribution)
                .deptAverageScores(deptAverageScores)
                .recentActivities(recentActivities)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeDashboardDTO getEmployeeDashboardStats(Long empId) {
        List<EvaluationPeriodDTO> inProgressPeriods = periodService.getInProgressPeriods();
        if (inProgressPeriods.isEmpty()) {
            return EmployeeDashboardDTO.builder()
                    .activePeriodName("진행 중인 차수 없음")
                    .selfEvalStatus("NONE")
                    .pendingPeerEvals(0)
                    .totalPeerEvals(0)
                    .myRecentGrades(List.of())
                    .build();
        }

        EvaluationPeriodDTO activePeriod = inProgressPeriods.get(0);
        Long periodId = activePeriod.periodId();

        // 1. 자가평가 상태
        String selfStatus = dashboardMapper.getSelfEvalStatus(empId, periodId);

        // 2. 동료평가 현황
        PeerEvalProgressDTO progress = dashboardMapper.getPeerEvalProgress(empId, periodId);
        int totalPeer = progress != null ? progress.totalCount() : 0;
        int completedPeer = progress != null ? progress.completedCount() : 0;

        // 3. 최근 등급 이력
        List<MyRecentGradeDTO> recentGrades = dashboardMapper.getMyRecentGrades(empId, 3);

        // 4. D-Day 계산
        long dDay = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), activePeriod.endDate());

        // 5. 현재 평가 차수 상세 결과 (최적화: 단건 조회)
        EvaluationResultDTO currentResult = resultService.getResultByEmpId(periodId, empId);

        return EmployeeDashboardDTO.builder()
                .activePeriodName(activePeriod.periodName())
                .dDay(dDay)
                .selfEvalStatus(selfStatus)
                .pendingPeerEvals(totalPeer - completedPeer)
                .totalPeerEvals(totalPeer)
                .myRecentGrades(recentGrades)
                .currentResult(currentResult)
                .build();
    }
}
