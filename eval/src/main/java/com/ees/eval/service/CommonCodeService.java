package com.ees.eval.service;

import com.ees.eval.dto.CommonCodeDTO;
import java.util.List;

/**
 * 공통 코드(CommonCode) 관리를 위한 비즈니스 로직 규격을 정의한 인터페이스입니다.
 */
public interface CommonCodeService {

    /**
     * 코드 고유 번호를 활용하여 정보를 조회합니다.
     *
     * @param codeId 조회 대상 식별자
     * @return 공통 코드 DTO
     * @throws IllegalArgumentException 코드 ID가 존재하지 않거나 조회가 실패할 시 발생
     */
    CommonCodeDTO getCodeById(Long codeId);

    /**
     * 시스템에서 정의된 모든 삭제되지 않은 공통 코드 목록을 가져옵니다.
     *
     * @return 전체 코드 리스트 (DTO)
     */
    List<CommonCodeDTO> getAllCodes();

    /**
     * 특정 그룹(그룹 코드)에 묶여 있는 상세 코드 리스트를 조회합니다.
     *
     * @param groupCode 검색할 상위 그룹 구분 코드
     * @return 필터링된 공통 코드 목록 (DTO)
     */
    List<CommonCodeDTO> getCodesByGroupCode(String groupCode);

    /**
     * 새로운 공통 코드를 시스템에 등록합니다.
     *
     * @param codeDto 저장할 소스 데이터 DTO
     * @return 등록 완료된 결과물 정보 DTO
     */
    CommonCodeDTO createCode(CommonCodeDTO codeDto);

    /**
     * 공통 코드 정보를 수정하며, 낙관적 락에 의해 데이터 동기화 이슈를 방지합니다.
     *
     * @param codeDto 수정할 정보가 담긴 소스 DTO
     * @return 최신화되어 수정 반영된 코드 정보 (DTO)
     * @throws com.ees.eval.exception.EesOptimisticLockException 동시 수정 상황 발생 시 예외
     */
    CommonCodeDTO updateCode(CommonCodeDTO codeDto);

    /**
     * 시스템 공통 코드를 삭제(Soft Delete)합니다.
     *
     * @param codeId 삭제 처리할 고유 코드 번호
     * @throws IllegalArgumentException 삭제 대상을 찾지 못할 시 발생
     */
    void deleteCode(Long codeId);
}
