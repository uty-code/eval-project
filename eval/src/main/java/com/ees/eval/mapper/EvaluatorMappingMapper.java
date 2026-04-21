package com.ees.eval.mapper;

import com.ees.eval.domain.EvaluatorMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * evaluator_mappings_51 테이블에 대한 데이터 접근 기능을 정의한 매퍼 인터페이스입니다.
 * 중복 체크, 평가자/피평가자 관점의 조회 쿼리를 포함합니다.
 */
@Mapper
public interface EvaluatorMappingMapper {

    /**
     * 매핑 ID로 평가자 매핑 정보를 조회합니다.
     *
     * @param mappingId 매핑 식별자
     * @return 매핑 엔티티를 담은 Optional
     */
    Optional<EvaluatorMapping> findById(Long mappingId);

    /**
     * 특정 차수 및 부서의 매핑 목록을 조회합니다.
     *
     * @param periodId 차수 식별자
     * @param deptId   부서 식별자
     * @return 매핑 리스트
     */
    List<EvaluatorMapping> findByPeriodIdAndDeptId(@Param("periodId") Long periodId, @Param("deptId") Long deptId);

    /**
     * 특정 차수에서 '내가 평가해야 할 목록'을 조회합니다.
     * (evaluator_id = 나 → 내가 평가자인 매핑들)
     *
     * @param periodId 차수 식별자
     * @param evaluatorId 평가자(나) 사원 ID
     * @return 내가 수행해야 할 평가 매핑 리스트
     */
    List<EvaluatorMapping> findByEvaluatorId(@Param("periodId") Long periodId,
                                              @Param("evaluatorId") Long evaluatorId);

    /**
     * 특정 차수에서 '나를 평가하는 사람 목록'을 조회합니다.
     * (evaluatee_id = 나 → 나를 평가하는 매핑들)
     *
     * @param periodId 차수 식별자
     * @param evaluateeId 피평가자(나) 사원 ID
     * @return 나를 평가하는 매핑 리스트
     */
    List<EvaluatorMapping> findByEvaluateeId(@Param("periodId") Long periodId,
                                              @Param("evaluateeId") Long evaluateeId);

    /**
     * 동일 차수에서 동일한 평가 관계(차수+피평가자+평가자+관계유형)가 이미 존재하는지 확인합니다.
     *
     * @param periodId 차수 ID
     * @param evaluateeId 피평가자 ID
     * @param evaluatorId 평가자 ID
     * @param relationTypeCode 관계 유형
     * @return 존재하면 1 이상, 없으면 0
     */
    int countDuplicate(@Param("periodId") Long periodId, @Param("evaluateeId") Long evaluateeId,
                       @Param("evaluatorId") Long evaluatorId, @Param("relationTypeCode") String relationTypeCode);

    /**
     * 새로운 평가자 매핑을 저장합니다.
     *
     * @param mapping 저장할 매핑 엔티티
     * @return 삽입된 행의 수
     */
    int insert(EvaluatorMapping mapping);

    /**
     * 매핑 정보를 수정합니다. 낙관적 락이 적용됩니다.
     *
     * @param mapping 수정할 매핑 엔티티
     * @return 업데이트 행 수
     */
    int update(EvaluatorMapping mapping);

    /**
     * 매핑을 논리적으로 삭제합니다.
     *
     * @param mappingId 삭제할 매핑 ID
     * @param updatedBy 수정자 ID
     * @param updatedAt 수정 시각
     * @return 업데이트 행 수
     */
    int softDelete(@Param("mappingId") Long mappingId, @Param("updatedBy") Long updatedBy,
                   @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 특정 차수의 모든 SELF 매핑을 논리 삭제 처리합니다.
     */
    int deleteSelfMappingsByPeriod(@Param("periodId") Long periodId);
}
