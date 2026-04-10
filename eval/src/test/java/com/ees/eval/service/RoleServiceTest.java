package com.ees.eval.service;

import com.ees.eval.dto.RoleDTO;
import com.ees.eval.exception.EesOptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class RoleServiceTest {

    @Autowired
    private RoleService roleService;

    @Test
    @DisplayName("Role 생성 및 조회 테스트")
    void createAndGetRoleTest() {
        RoleDTO dto = RoleDTO.builder()
                .roleName("ROLE_ADMIN")
                .description("관리자 권한")
                .build();
        
        RoleDTO saved = roleService.createRole(dto);
        assertThat(saved.roleId()).isNotNull();
        assertThat(saved.roleName()).isEqualTo("ROLE_ADMIN");
        assertThat(saved.isDeleted()).isEqualTo("n");
        assertThat(saved.version()).isEqualTo(0);

        RoleDTO found = roleService.getRoleById(saved.roleId());
        assertThat(found.roleName()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("낙관적 락 충돌 검증 테스트")
    void optimisticLockTest() {
        RoleDTO dto = RoleDTO.builder().roleName("ROLE_USER").description("일반 권한").build();
        RoleDTO saved = roleService.createRole(dto);
        
        RoleDTO tx1 = roleService.getRoleById(saved.roleId());
        RoleDTO tx2 = roleService.getRoleById(saved.roleId());

        // Java 21 Record 특성 상 수정이 불가능하므로 builder()를 빌려와 새 데이터 복제 (불변성)
        RoleDTO updatedTx1Attempt = RoleDTO.builder()
                .roleId(tx1.roleId())
                .roleName(tx1.roleName())
                .description("설명 변경 1")
                .isDeleted(tx1.isDeleted())
                .version(tx1.version())
                .createdAt(tx1.createdAt())
                .createdBy(tx1.createdBy())
                .build();
        
        RoleDTO updatedTx1 = roleService.updateRole(updatedTx1Attempt);
        assertThat(updatedTx1.version()).isEqualTo(1);

        RoleDTO updatedTx2Attempt = RoleDTO.builder()
                .roleId(tx2.roleId())
                .roleName(tx2.roleName())
                .description("설명 변경 2 - 충돌 예정")
                .isDeleted(tx2.isDeleted())
                .version(tx2.version())
                .createdAt(tx2.createdAt())
                .createdBy(tx2.createdBy())
                .build();

        assertThatThrownBy(() -> roleService.updateRole(updatedTx2Attempt))
                .isInstanceOf(EesOptimisticLockException.class)
                .hasMessageContaining("충돌");
    }

    @Test
    @DisplayName("소프트 델리트 테스트")
    void softDeleteTest() {
        RoleDTO dto = RoleDTO.builder().roleName("ROLE_TMP").description("임시 권한").build();
        RoleDTO saved = roleService.createRole(dto);

        roleService.deleteRole(saved.roleId());

        // Java 21 Unnamed variable (_) 활용
        try {
            roleService.getRoleById(saved.roleId());
        } catch (IllegalArgumentException _) {
            // expected exception, unnamed variable used
        }
    }
}
