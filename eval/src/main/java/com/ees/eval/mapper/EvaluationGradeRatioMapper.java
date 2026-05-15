package com.ees.eval.mapper;

import com.ees.eval.dto.EvaluationGradeRatioDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EvaluationGradeRatioMapper {
    
    /**
     * 특정 차수와 부서의 등급 비율을 조회합니다.
     * deptId가 null인 경우 전사 공통 비율을 조회합니다.
     */
    EvaluationGradeRatioDTO getGradeRatio(@Param("periodId") Long periodId, @Param("deptId") Long deptId);
    
    /**
     * 특정 차수의 모든 부서별 등급 비율을 조회합니다.
     * (N+1 최적화를 위해 사용됩니다)
     */
    java.util.List<EvaluationGradeRatioDTO> findByPeriodId(@Param("periodId") Long periodId);
    
    /**
     * 등급 비율을 저장하거나 업데이트합니다 (Upsert).
     */
    void upsertGradeRatio(EvaluationGradeRatioDTO dto);
}
