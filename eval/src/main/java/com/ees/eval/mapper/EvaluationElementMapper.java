package com.ees.eval.mapper;

import com.ees.eval.domain.EvaluationElement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * evaluation_elements_51 테이블에 대한 데이터 접근 기능을 정의한 매퍼 인터페이스입니다.
 * 항목 CRUD와 차수별 가중치 합계 조회 쿼리를 포함합니다.
 */
@Mapper
public interface EvaluationElementMapper {

    /**
     * 항목 ID로 평가 항목 정보를 조회합니다.
     *
     * @param elementId 조회할 항목 식별자
     * @return 항목 엔티티를 담은 Optional 객체
     */
    Optional<EvaluationElement> findById(Long elementId);

    /**
     * 특정 차수 및 부서에 귀속된 평가 항목 목록을 조회합니다.
     *
     * @param periodId 대상 차수 식별자
     * @param deptId   대상 부서 식별자 (NULL이면 부서와 무관하게 조회하거나 전사 공통 조회)
     * @return 해당 조건의 항목 리스트
     */
    List<EvaluationElement> findByPeriodId(@Param("periodId") Long periodId, @Param("deptId") Long deptId);

    /**
     * 특정 차수 및 부서에 등록된 모든 항목의 가중치 합계를 조회합니다.
     * 특정 항목을 제외한 합계를 구할 수 있도록 excludeElementId 파라미터를 지원합니다.
     *
     * @param periodId         대상 차수 식별자
     * @param deptId           대상 부서 식별자
     * @param excludeElementId 합계에서 제외할 항목 ID (없으면 null)
     * @return 가중치 합계
     */
    BigDecimal sumWeightByPeriodId(@Param("periodId") Long periodId,
                                   @Param("deptId") Long deptId,
                                   @Param("excludeElementId") Long excludeElementId);

    /**
     * 새로운 평가 항목을 저장합니다.
     *
     * @param element 저장할 항목 엔티티
     * @return 삽입된 행의 수
     */
    int insert(EvaluationElement element);

    /**
     * 항목 정보를 수정합니다. 낙관적 락이 적용됩니다.
     *
     * @param element 수정할 항목 엔티티
     * @return 업데이트된 행의 수
     */
    int update(EvaluationElement element);

    /**
     * 항목을 논리적으로 삭제합니다.
     *
     * @param elementId 삭제할 항목 ID
     * @param updatedBy 수정자 ID
     * @param updatedAt 수정 시각
     * @return 업데이트된 행의 수
     */
    int softDelete(@Param("elementId") Long elementId, @Param("updatedBy") Long updatedBy,
                   @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 특정 차수 및 부서의 모든 평가 항목을 논리적으로 삭제(초기화)합니다.
     */
    int resetByPeriodAndDept(@Param("periodId") Long periodId, @Param("deptId") Long deptId,
                             @Param("updatedBy") Long updatedBy, @Param("updatedAt") LocalDateTime updatedAt);
}
