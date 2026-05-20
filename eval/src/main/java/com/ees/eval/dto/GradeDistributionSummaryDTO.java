package com.ees.eval.dto;

import lombok.Builder;

/**
 * 상대평가 등급 배분 현황 및 평가 진척도 요약을 전송하는 DTO입니다.
 */
@Builder
public record GradeDistributionSummaryDTO(
        String groupName,      // 부서명 또는 본부명
        Long deptId,           // 부서 ID
        String roleType,       // "leader" (부서장) 또는 "staff" (일반사원)
        
        // 등급별 목표 TO
        int targetS,
        int targetA,
        int targetB,
        int targetC,
        int targetD,
        
        // 실제 부여된 예상 등급 수
        int actualS,
        int actualA,
        int actualB,
        int actualC,
        int actualD,
        
        int totalEligible,     // 평가 대상 인원
        int completedCount     // 최종 완료(제출)된 인원
) {
    public int progressRate() {
        if (totalEligible == 0) return 0;
        return (int) Math.round((double) completedCount / totalEligible * 100.0);
    }
}
