package com.ees.eval.mapper;

import com.ees.eval.domain.Evaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * evaluations_51 테이블에 대한 MyBatis 매퍼 인터페이스입니다.
 */
@Mapper
public interface EvaluationMapper {

    /** 평가 레코드 단건 삽입 */
    void insert(Evaluation evaluation);

    /** mappingId + elementId 기준으로 기존 레코드 조회 (중복 저장 방지) */
    Optional<Evaluation> findByMappingIdAndElementId(@Param("mappingId") Long mappingId,
                                                     @Param("elementId") Long elementId);

    /** 평가 레코드 업데이트 (코멘트, 상태 변경) */
    int update(Evaluation evaluation);

    /** mappingId 기준으로 해당 매핑의 전체 평가 내용 조회 */
    List<Evaluation> findByMappingId(@Param("mappingId") Long mappingId);
}
