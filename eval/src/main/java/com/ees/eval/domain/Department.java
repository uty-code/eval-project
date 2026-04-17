package com.ees.eval.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 부서(departments) 테이블에 대응하는 도메인 엔티티 클래스입니다.
 * BaseEntity를 상속하여 감사(Audit) 필드, 소프트 삭제, 낙관적 락 기능을 제공받습니다.
 * 상위 부서(parent_dept_id)를 통해 계층형 트리 구조를 지원합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Department extends BaseEntity {

    /** 부서 고유 식별자 (PK) */
    private Long deptId;

    /** 상위 부서 식별자 (FK -> departments, NULL이면 최상위 부서) */
    private Long parentDeptId;

    /** 부서명 */
    private String deptName;

    /** 사용 여부 (y: 사용중, n: 미사용중) */
    private String isActive;

    /**
     * 부서 엔티티를 생성하는 빌더 메서드입니다.
     *
     * @param deptId       부서 ID
     * @param parentDeptId 상위 부서 ID (최상위 부서일 경우 null)
     * @param deptName     부서 명칭
     * @param isActive     사용 여부 (y/n)
     */
    @Builder
    public Department(Long deptId, Long parentDeptId, String deptName, String isActive) {
        this.deptId = deptId;
        this.parentDeptId = parentDeptId;
        this.deptName = deptName;
        this.isActive = isActive;
    }
}
