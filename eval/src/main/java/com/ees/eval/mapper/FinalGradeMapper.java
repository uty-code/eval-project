package com.ees.eval.mapper;

import com.ees.eval.domain.FinalGrade;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * final_grades_51 테이블에 대한 MyBatis 매퍼 인터페이스입니다.
 */
@Mapper
public interface FinalGradeMapper {

    void insert(FinalGrade finalGrade);

    Optional<FinalGrade> findByPeriodIdAndEmpId(@Param("periodId") Long periodId, @Param("empId") Long empId);

    void update(FinalGrade finalGrade);

    List<FinalGrade> findByPeriodId(@Param("periodId") Long periodId);
    
    List<FinalGrade> findByPeriodIdAndDeptId(@Param("periodId") Long periodId, @Param("deptId") Long deptId);
    
    void deleteByPeriodId(@Param("periodId") Long periodId);
}
