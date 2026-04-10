package com.ees.eval.service;

import com.ees.eval.dto.PositionDTO;
import java.util.List;

/**
 * 직급(Position) 정보를 처리하는 서비스 인터페이스입니다.
 */
public interface PositionService {

    /**
     * ID를 기반으로 특정 직급 데이터를 조회합니다.
     *
     * @param positionId 직급 식별자
     * @return 직급 데이터 전송 객체(DTO)
     * @throws IllegalArgumentException 조회 결과가 없을 경우 발생
     */
    PositionDTO getPositionById(Long positionId);

    /**
     * 등록된 전체 직급 리스트를 조회합니다.
     *
     * @return 직급 DTO 목록
     */
    List<PositionDTO> getAllPositions();

    /**
     * 신규 직급 정보를 생성합니다.
     *
     * @param positionDto 생성할 데이터 원본
     * @return 생성 완료된 데이터 DTO (ID 포함)
     */
    PositionDTO createPosition(PositionDTO positionDto);

    /**
     * 직급 정보를 수정하며, 낙관적 락을 통해 충동을 방지합니다.
     *
     * @param positionDto 수정한 데이터 DTO
     * @return 수정 결과 최신 값
     * @throws com.ees.eval.exception.EesOptimisticLockException 동시 수정 시 발생
     */
    PositionDTO updatePosition(PositionDTO positionDto);

    /**
     * 직급 항목을 삭제 처리(Soft Delete)합니다.
     *
     * @param positionId 삭제할 직급의 ID
     * @throws IllegalArgumentException 대상이 존재하지 않을 시 발생
     */
    void deletePosition(Long positionId);
}
