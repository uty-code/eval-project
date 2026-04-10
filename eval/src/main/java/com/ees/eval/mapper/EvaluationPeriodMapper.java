package com.ees.eval.mapper;

import com.ees.eval.domain.EvaluationPeriod;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * evaluation_periods 테이블에 대한 데이터 접근 기능을 정의한 매퍼 인터페이스입니다.
 * 차수 CRUD와 상태별 중복 체크 쿼리를 포함합니다.
 */
@Mapper
public interface EvaluationPeriodMapper {

    /**
     * 차수 ID로 평가 차수 정보를 조회합니다.
     *
     * @param periodId 조회할 차수 식별자
     * @return 차수 엔티티를 담은 Optional 객체
     */
    Optional<EvaluationPeriod> findById(Long periodId);

    /**
     * 삭제되지 않은 모든 평가 차수 목록을 조회합니다.
     *
     * @return 전체 차수 리스트
     */
    List<EvaluationPeriod> findAll();

    /**
     * 특정 상태 코드를 가진 차수 목록을 조회합니다.
     * '진행 중' 중복 체크에 사용됩니다.
     *
     * @param statusCode 조회할 상태 코드
     * @return 해당 상태의 차수 리스트
     */
    List<EvaluationPeriod> findByStatusCode(String statusCode);

    /**
     * 새로운 평가 차수를 저장합니다.
     *
     * @param period 저장할 차수 엔티티
     * @return 삽입된 행의 수
     */
    int insert(EvaluationPeriod period);

    /**
     * 차수 정보를 수정합니다. 낙관적 락(version)이 적용됩니다.
     *
     * @param period 수정할 차수 엔티티
     * @return 업데이트된 행의 수
     */
    int update(EvaluationPeriod period);

    /**
     * 차수를 논리적으로 삭제합니다.
     *
     * @param periodId 삭제할 차수 ID
     * @param updatedBy 수정자 ID
     * @param updatedAt 수정 시각
     * @return 업데이트된 행의 수
     */
    int softDelete(@Param("periodId") Long periodId, @Param("updatedBy") Long updatedBy,
                   @Param("updatedAt") LocalDateTime updatedAt);
}
