package com.ees.eval.mapper;

import com.ees.eval.domain.CommonCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * common_codes_51 테이블 관리 기능을 정의한 매퍼 인터페이스입니다.
 */
@Mapper
public interface CommonCodeMapper {

    /**
     * 특정 코드 ID를 통해 공통 코드 정보를 조회합니다.
     *
     * @param codeId 조회할 코드 식별자
     * @return 조회된 공통 코드 정보를 담은 Optional
     */
    Optional<CommonCode> findById(Long codeId);

    /**
     * 시스템에 등록된 모든 공통 코드 목록을 그룹 코드 순으로 조회합니다.
     *
     * @return 전체 공통 코드 리스트
     */
    List<CommonCode> findAll();

    /**
     * 특정 그룹에 속한 공통 코드 목록을 필터링하여 조회합니다.
     *
     * @param groupCode 필터링할 코드 그룹 구분자
     * @return 해당 그룹의 공통 코드 리스트
     */
    List<CommonCode> findByGroupCode(String groupCode);

    /**
     * 새로운 공통 코드를 생성합니다.
     *
     * @param commonCode 저장할 코드 데이터 엔티티
     * @return 생성된 레코드 수
     */
    int insert(CommonCode commonCode);

    /**
     * 기존 공통 코드 정보를 업데이트합니다. 낙관적 락 버전 체크가 동반됩니다.
     *
     * @param commonCode 수정한 정보를 담고 있는 엔티티
     * @return 수정한 행의 수
     */
    int update(CommonCode commonCode);

    /**
     * 특정 공통 코드를 삭제 상태로 변경합니다.
     *
     * @param codeId 삭제할 대상 코드의 ID
     * @param updatedBy 수정을 시도하는 사용자 식별번호
     * @param updatedAt 수정 시각 데이터
     * @return 업데이트된 행의 수
     */
    int softDelete(@Param("codeId") Long codeId, @Param("updatedBy") Long updatedBy, @Param("updatedAt") LocalDateTime updatedAt);
}
