package com.ees.eval.mapper;

import com.ees.eval.domain.EvaluationTypeWeight;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface EvaluationTypeWeightMapper {
    
    List<EvaluationTypeWeight> findByPeriodId(@Param("periodId") Long periodId, @Param("deptId") Long deptId, @Param("targetRoleCode") String targetRoleCode);
    
    List<EvaluationTypeWeight> findAllByPeriodId(@Param("periodId") Long periodId);
    
    Optional<EvaluationTypeWeight> findById(Long weightId);
    
    int insert(EvaluationTypeWeight weight);
    
    int insertBatch(@Param("weights") List<EvaluationTypeWeight> weights);
    
    int update(EvaluationTypeWeight weight);
    
    int deleteByPeriodId(@Param("periodId") Long periodId, @Param("deptId") Long deptId, @Param("targetRoleCode") String targetRoleCode);
}
