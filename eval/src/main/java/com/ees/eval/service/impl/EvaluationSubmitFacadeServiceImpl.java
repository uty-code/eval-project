package com.ees.eval.service.impl;

import com.ees.eval.service.EvaluationService;
import com.ees.eval.service.EvaluationSubmitFacadeService;
import com.ees.eval.service.FinalGradeProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationSubmitFacadeServiceImpl implements EvaluationSubmitFacadeService {

    private final EvaluationService evaluationService;
    private final FinalGradeProcessor finalGradeProcessor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitAndProcess(Long mappingId, Map<String, String> params, Long empId, 
                                 Long periodId, Long evaluateeId, Long deptId, 
                                 String relationTypeCode, boolean forceRelativeCalculation) {
        
        log.info("[Facade] 평가 제출 및 연산 트랜잭션 시작 - mappingId={}, empId={}", mappingId, empId);

        // 1. 평가 내역(Evaluation) Upsert 및 제출 상태 변경
        // (트랜잭션 내 1단계 커밋 대기)
        evaluationService.upsertEvaluations(mappingId, params, empId);

        // 2. 종합 점수 산출 및 최종 등급 테이블 갱신 (부서 상대평가 연동)
        // (오류 발생 시 1단계 평가 내역 저장까지 모두 원자적으로 롤백됨)
        finalGradeProcessor.processGradeAndRanking(
                periodId, evaluateeId, deptId, relationTypeCode, empId, forceRelativeCalculation
        );

        log.info("[Facade] 평가 제출 및 연산 트랜잭션 정상 완료 - mappingId={}", mappingId);
    }
}
