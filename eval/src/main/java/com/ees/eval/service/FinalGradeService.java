package com.ees.eval.service;

import com.ees.eval.dto.FinalGradeSearchCondition;
import com.ees.eval.dto.FinalGradeTaskDTO;
import java.util.List;

/**
 * 최종 등급 확정 업무를 처리하는 서비스 인터페이스입니다.
 */
public interface FinalGradeService {

    /**
     * 특정 임원이 담당하는 최종 등급 확정 대상 목록을 조회합니다.
     * N+1 문제를 해결하기 위해 벌크 조회 및 메모리 매핑을 수행합니다.
     *
     * @param executiveEmpId 임원(평가자) 사번
     * @param condition      검색 및 필터 조건 (periodId 포함)
     * @return 상태 플래그가 계산된 대상 목록
     */
    List<FinalGradeTaskDTO> getFinalGradeTasks(Long executiveEmpId, FinalGradeSearchCondition condition);

    /**
     * 어드민용: 임원 필터 없이 전체 최종 등급 대상자 목록을 조회합니다.
     *
     * @param condition 검색 및 필터 조건
     * @return 전체 최종 등급 대상 목록
     */
    List<FinalGradeTaskDTO> getAdminFinalGradeTasks(FinalGradeSearchCondition condition);

    /**
     * 본부/부서별 상대평가 등급 배분 목표(TO)와 실제 부여 현황 및 진척도를 집계합니다.
     *
     * @param executiveEmpId 임원(평가자) 사번 (isAdmin이 true인 경우 null 가능)
     * @param condition      검색 및 필터 조건
     * @param isAdmin        관리자 여부
     * @return 각 그룹별 등급 배분 현황 요약 목록
     */
    List<com.ees.eval.dto.GradeDistributionSummaryDTO> getGradeDistributionSummaries(
            Long executiveEmpId,
            FinalGradeSearchCondition condition,
            boolean isAdmin);
}
