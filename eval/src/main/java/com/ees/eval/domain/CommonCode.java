package com.ees.eval.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 시스템 전반에서 쓰이는 공통 코드(그룹화 속성)를 관리하는 Entity 클래스입니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class CommonCode extends BaseEntity {
    private Long codeId;
    private String groupCode;
    private String codeValue;
    private String codeName;
    private String description;

    /**
     * 공통 코드 엔티티를 생성하기 위한 빌더입니다.
     *
     * @param codeId      코드 분류의 고유 식별자 ID
     * @param groupCode   그룹핑을 위해 쓰이는 상위 코드명 (예: EVAL_STEP)
     * @param codeValue   상세 코드 값 (예: STEP1)
     * @param codeName    표시되는 화면 코드 명칭
     * @param description 공통 코드의 세부 설명
     */
    @Builder
    public CommonCode(Long codeId, String groupCode, String codeValue, String codeName, String description) {
        this.codeId = codeId;
        this.groupCode = groupCode;
        this.codeValue = codeValue;
        this.codeName = codeName;
        this.description = description;
    }
}
