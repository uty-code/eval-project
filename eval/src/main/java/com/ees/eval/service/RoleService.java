package com.ees.eval.service;

import com.ees.eval.dto.RoleDTO;
import java.util.List;

/**
 * 프로젝트 내 권한(Role) 관리 업무를 담당하는 서비스 인터페이스입니다.
 */
public interface RoleService {

    /**
     * 권한 ID를 이용해 특정 권한 정보(DTO)를 조회합니다.
     *
     * @param roleId 조회할 대상 권한의 ID
     * @return 조회된 권한 정보를 담은 DTO 객체
     * @throws IllegalArgumentException 아이디에 해당하는 권한이 없을 경우 발생
     */
    RoleDTO getRoleById(Long roleId);

    /**
     * 시스템 내에 존재하는 모든 권한 목록을 조회하여 반환합니다.
     *
     * @return 권한 DTO 리스트
     */
    List<RoleDTO> getAllRoles();

    /**
     * 신규 권한 정보를 생성하고 저장합니다.
     *
     * @param roleDto 저장하려는 권한 원본 데이터 DTO
     * @return 데이터베이스에 저장된 후의 권한 정보 DTO
     */
    RoleDTO createRole(RoleDTO roleDto);

    /**
     * 기존 권한 정보를 수정합니다. 버전 체크를 통해 데이터 정합성을 보장합니다.
     *
     * @param roleDto 수정할 데이터가 포함된 DTO 객체
     * @return 수정 처리 완료 후 최신 권한 정보 DTO
     * @throws com.ees.eval.exception.EesOptimisticLockException 데이터 충돌 시 발생
     */
    RoleDTO updateRole(RoleDTO roleDto);

    /**
     * 특정 권한 항목을 논리적으로 삭제 처리합니다.
     *
     * @param roleId 삭제할 권한의 고유 식별자
     * @throws IllegalArgumentException 존재하지 않는 권한일 경우 발생
     */
    void deleteRole(Long roleId);
}
