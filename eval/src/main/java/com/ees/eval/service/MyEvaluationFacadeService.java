package com.ees.eval.service;

import com.ees.eval.dto.MyEvaluationPageDTO;
import com.ees.eval.dto.EvaluatorMappingDTO;
import java.util.Map;

/**
 * 자가평가(My Evaluation) 업무의 복잡한 비즈니스 로직을 오케스트레이션하는 파사드 서비스입니다.
 * 컨트롤러의 비대화를 방지하고, 중복된 검증 로직을 통합 관리합니다.
 */
public interface MyEvaluationFacadeService {

    /**
     * 자가평가 대시보드 구성을 위한 통합 데이터를 조회합니다.
     * 
     * @param empId      사원 ID
     * @param periodId   차수 ID (필터)
     * @param status     상태 (필터)
     * @param keyword    검색어 (필터)
     * @param page       페이지 번호
     * @param pageSize   페이지 크기
     * @return 대시보드용 모델 데이터 맵
     */
    Map<String, Object> getDashboardData(Long empId, Long periodId, String status, String keyword, int page, int pageSize);

    /**
     * 자가평가 작성을 위한 마법사(Wizard) 데이터를 구성합니다.
     * 
     * @param mappingId 매핑 ID
     * @param empId     현재 로그인한 사원 ID (권한 검증용)
     * @return 마법사 화면용 모델 데이터 맵
     */
    Map<String, Object> getWizardData(Long mappingId, Long empId);

    /**
     * 자가평가 데이터를 저장 또는 제출합니다.
     * 
     * @param mappingId 매핑 ID
     * @param params    평가 점수 및 의견 파라미터
     * @param empId     제출자 사원 ID
     */
    void submitEvaluation(Long mappingId, Map<String, String> params, Long empId);

    /**
     * 어드민용: 전체 자가평가 대시보드 데이터를 조회합니다.
     * 특정 사원에 제한하지 않고 모든 SELF 매핑을 대상으로 합니다.
     *
     * @param periodId 차수 ID (필터)
     * @param status   상태 (필터)
     * @param keyword  검색어 (필터)
     * @param page     페이지 번호
     * @param pageSize 페이지 크기
     * @return 대시보드용 모델 데이터 맵
     */
    Map<String, Object> getAdminDashboardData(Long periodId, String status, String keyword, int page, int pageSize);
}
