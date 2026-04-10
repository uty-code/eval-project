package com.ees.eval.service.impl;

import com.ees.eval.domain.Role;
import com.ees.eval.dto.RoleDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import com.ees.eval.mapper.RoleMapper;
import com.ees.eval.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RoleService 인터페이스의 실제 비즈니스 로직 구현체입니다.
 * 모든 변경 작업에는 @Transactional이 적용됩니다.
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;

    /**
     * 특정 권한 조회 (읽기 전용 트랜잭션)
     *
     * @param roleId 권한 ID
     * @return 조회된 RoleDTO
     */
    @Override
    @Transactional(readOnly = true)
    public RoleDTO getRoleById(Long roleId) {
        // 매퍼를 통해 데이터 존재 여부 확인 및 엔티티 패치
        Role role = roleMapper.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found with id: " + roleId));
        return convertToDto(role);
    }

    /**
     * 전체 권한 리스트 조회
     *
     * @return RoleDTO 리스트
     */
    @Override
    @Transactional(readOnly = true)
    public List<RoleDTO> getAllRoles() {
        // 전체 조회를 수행하고 스트림을 통해 DTO로 변환
        return roleMapper.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 신규 권한 생성
     *
     * @param roleDto 생성할 데이터
     * @return 저장된 RoleDTO
     */
    @Override
    @Transactional
    public RoleDTO createRole(RoleDTO roleDto) {
        // DTO를 엔티티로 변환 후 감사 필드 및 초기값 설정
        Role role = convertToEntity(roleDto);
        role.prePersist();
        
        // 데이터베이스 삽입 수행
        roleMapper.insert(role);
        
        return convertToDto(role);
    }

    /**
     * 권한 정보 수정 (낙관적 락 처리)
     *
     * @param roleDto 수정 요청 데이터
     * @return 최신화된 RoleDTO
     */
    @Override
    @Transactional
    public RoleDTO updateRole(RoleDTO roleDto) {
        // 엔티티 변환 시 현재 버전 정보 포함
        Role role = convertToEntity(roleDto);
        role.preUpdate();
        
        // 업데이트 수행 시 영향받은 행의 수 체크로 낙관적 락 검증
        int updatedRows = roleMapper.update(role);
        if (updatedRows == 0) {
            throw new EesOptimisticLockException("정보가 다른 사용자에 의해 변경되었거나 이미 삭제되었습니다. (락 충돌)");
        }
        
        // 반영된 최신 데이터를 다시 조회하여 반환
        return getRoleById(role.getRoleId());
    }

    /**
     * 권한 논리 삭제
     *
     * @param roleId 대상 ID
     */
    @Override
    @Transactional
    public void deleteRole(Long roleId) {
        Long currentUserId = 1L; // 추후 세션 유저로 대체 예정
        
        // 매퍼를 통해 is_deleted 값을 'y'로 업데이트
        int updatedRows = roleMapper.softDelete(roleId, currentUserId, LocalDateTime.now());
        if (updatedRows == 0) {
            throw new IllegalArgumentException("삭제 대상 Role을 찾을 수 없습니다. roleId: " + roleId);
        }
    }

    /**
     * 엔티티(Role)를 DTO(RoleDTO)로 변환하는 헬퍼 메서드입니다.
     */
    private RoleDTO convertToDto(Role role) {
        return RoleDTO.builder()
                .roleId(role.getRoleId())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .isDeleted(role.getIsDeleted())
                .version(role.getVersion())
                .createdAt(role.getCreatedAt())
                .createdBy(role.getCreatedBy())
                .updatedAt(role.getUpdatedAt())
                .updatedBy(role.getUpdatedBy())
                .build();
    }

    /**
     * DTO(RoleDTO)를 엔티티(Role)로 변환하는 헬퍼 메서드입니다.
     */
    private Role convertToEntity(RoleDTO dto) {
        Role role = Role.builder()
                .roleId(dto.roleId())
                .roleName(dto.roleName())
                .description(dto.description())
                .build();
        role.setIsDeleted(dto.isDeleted());
        role.setVersion(dto.version());
        role.setCreatedAt(dto.createdAt());
        role.setCreatedBy(dto.createdBy());
        role.setUpdatedAt(dto.updatedAt());
        role.setUpdatedBy(dto.updatedBy());
        return role;
    }
}
