package com.ees.eval.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사원 접근 권한 정보를 담는 Entity 클래스입니다.
 * BaseEntity를 상속하여 공통 감사(Audit) 필드를 제공받습니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Role extends BaseEntity {
    private Long roleId;
    private String roleName;
    private String description;

    /**
     * 권한 엔티티를 생성하는 빌더 메서드입니다.
     *
     * @param roleId      권한 고유 ID
     * @param roleName    권한 명칭 (예: ROLE_ADMIN)
     * @param description 권한에 대한 상세 설명
     */
    @Builder
    public Role(Long roleId, String roleName, String description) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.description = description;
    }
}
