package com.ees.eval.mapper;

import com.ees.eval.domain.Position;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * positions 테이블 데이터 접근 기능을 수행하는 매퍼 인터페이스입니다.
 */
@Mapper
public interface PositionMapper {

    /**
     * 직급 ID로 정보를 조회합니다.
     *
     * @param positionId 직급 식별자
     * @return 직급 정보를 담은 Optional
     */
    Optional<Position> findById(Long positionId);

    /**
     * 삭제되지 않은 모든 직급 정보를 계층 수준에 따라 정렬하여 조회합니다.
     *
     * @return 직급 목록
     */
    List<Position> findAll();

    /**
     * 새로운 직급 정보를 저장합니다.
     *
     * @param position 저장할 직급 엔티티 객체
     * @return 삽입된 행의 수
     */
    int insert(Position position);

    /**
     * 직급 정보를 수정합니다. 버전 번호를 통한 동시성 제어가 이루어집니다.
     *
     * @param position 수정 정보를 담은 엔티티
     * @return 수정한 행의 수 (낙관적 락 실패 시 0 반환)
     */
    int update(Position position);

    /**
     * 특정 직급을 삭제 상태('y')로 업데이트합니다.
     *
     * @param positionId 대상 직급의 식별자
     * @param updatedBy 수정한 사용자 정보
     * @param updatedAt 수정 시각
     * @return 업데이트 결과로 변경된 행의 수
     */
    int softDelete(@Param("positionId") Long positionId, @Param("updatedBy") Long updatedBy, @Param("updatedAt") LocalDateTime updatedAt);
}
