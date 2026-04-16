package com.ees.eval.mapper;

import com.ees.eval.domain.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * roles_51 테이블에 대한 데이터 접근 기능을 정의한 인터페이스입니다.
 * MyBatis에 의해 구현체가 매핑됩니다.
 */
@Mapper
public interface RoleMapper {

    /**
     * 권한 ID로 단건 정보를 조회합니다.
     *
     * @param roleId 조회할 권한 식별자
     * @return 권한 엔티티를 담은 Optional 객체
     */
    Optional<Role> findById(Long roleId);

    /**
     * 권한명(role_name)으로 단건 정보를 조회합니다.
     *
     * @param roleName 조회할 권한 이름 (예: ROLE_USER)
     * @return 권한 엔티티를 담은 Optional 객체
     */
    Optional<Role> findByRoleName(String roleName);

    /**
     * 삭제되지 않은 모든 권한 정보를 조회합니다.
     *
     * @return 전체 권한 목록
     */
    List<Role> findAll();

    /**
     * 새로운 권한 정보를 테이블에 저장합니다.
     *
     * @param role 저장할 권한 엔티티 객체
     * @return 삽입된 행의 수
     */
    int insert(Role role);

    /**
     * 기존 권한 정보를 수정합니다. 낙관적 락(version)이 적용됩니다.
     *
     * @param role 수정할 권한 정보를 담은 엔티티
     * @return 업데이트된 행의 수 (낙관적 락 실패 시 0)
     */
    int update(Role role);

    /**
     * 특정 권한을 논리적으로 삭제(Soft Delete) 처리합니다.
     *
     * @param roleId 삭제 처리할 권한 식별자
     * @param updatedBy 수정한 사용자 ID
     * @param updatedAt 수정 시각
     * @return 업데이트된 행의 수
     */
    int softDelete(@Param("roleId") Long roleId, @Param("updatedBy") Long updatedBy, @Param("updatedAt") LocalDateTime updatedAt);
}
