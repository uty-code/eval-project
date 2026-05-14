package com.ees.eval.dto;

import lombok.Builder;
import java.util.List;
import java.util.Map;

@Builder
public record EmployeeDashboardDTO(
    String activePeriodName,
    long dDay,
    String selfEvalStatus, // NOT_STARTED, IN_PROGRESS, COMPLETED
    int pendingPeerEvals,
    int totalPeerEvals,
    List<Map<String, Object>> myRecentGrades, // period_name, final_grade_code
    EvaluationResultDTO currentResult
) {}
