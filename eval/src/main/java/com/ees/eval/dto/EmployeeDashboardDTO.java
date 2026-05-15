package com.ees.eval.dto;

import com.ees.eval.dto.DashboardDtos.MyRecentGradeDTO;
import lombok.Builder;
import java.util.List;

@Builder
public record EmployeeDashboardDTO(
    String activePeriodName,
    long dDay,
    String selfEvalStatus, // NOT_STARTED, IN_PROGRESS, COMPLETED
    int pendingPeerEvals,
    int totalPeerEvals,
    List<MyRecentGradeDTO> myRecentGrades,
    EvaluationResultDTO currentResult
) {}
