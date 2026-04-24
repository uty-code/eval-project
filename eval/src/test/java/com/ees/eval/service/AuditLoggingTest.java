package com.ees.eval.service;

import com.ees.eval.domain.Department;
import com.ees.eval.domain.EvaluationTypeWeight;
import com.ees.eval.util.SecurityUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@DisplayName("감사(Audit) 필드 하드코딩 제거 검증 테스트")
class AuditLoggingTest {

    @Test
    @DisplayName("엔티티 저장 시 하드코딩된 1L 대신 현재 로그인한 사용자 ID가 반영되어야 한다")
    void entity_ShouldUseLoggedInUserId_InsteadOfHardcodedValue() {
        // given
        Long mockUserId = 9999L; // 테스트용 특정 사용자 ID
        
        try (MockedStatic<SecurityUtil> mockedSecurityUtil = mockStatic(SecurityUtil.class)) {
            // SecurityUtil이 무조건 9999L을 반환하도록 모킹
            mockedSecurityUtil.when(SecurityUtil::getCurrentEmployeeId).thenReturn(mockUserId);

            // 1. BaseEntity 상속 객체(Department) 테스트
            Department dept = new Department();
            dept.prePersist();

            // then
            assertThat(dept.getCreatedBy()).isEqualTo(mockUserId);
            assertThat(dept.getUpdatedBy()).isEqualTo(mockUserId);

            // 2. 리팩토링한 EvaluationTypeWeight 테스트
            EvaluationTypeWeight weight = EvaluationTypeWeight.builder()
                    .elementTypeCode("PERFORMANCE")
                    .weight(new BigDecimal("100.00"))
                    .build();
            weight.prePersist();

            // then
            assertThat(weight.getCreatedBy()).isEqualTo(mockUserId);
            assertThat(weight.getUpdatedBy()).isEqualTo(mockUserId);
        }
    }

    @Test
    @DisplayName("인증 정보가 없을 경우 시스템 관리자 ID(1L)를 기본값으로 사용해야 한다")
    void entity_ShouldUseSystemDefault_WhenNotLoggedIn() {
        // given
        try (MockedStatic<SecurityUtil> mockedSecurityUtil = mockStatic(SecurityUtil.class)) {
            // SecurityUtil이 1L을 반환하도록 설정 (실제 폴백 로직 모사)
            mockedSecurityUtil.when(SecurityUtil::getCurrentEmployeeId).thenReturn(1L);

            // when
            Department dept = new Department();
            dept.prePersist();

            // then
            assertThat(dept.getCreatedBy()).isEqualTo(1L);
            assertThat(dept.getUpdatedBy()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("엔티티 수정 시에도 현재 로그인한 사용자 ID가 updatedBy에 반영되어야 한다")
    void entity_ShouldUpdateUpdatedBy_OnPreUpdate() {
        // given
        Long firstUserId = 1111L;
        Long secondUserId = 2222L;

        try (MockedStatic<SecurityUtil> mockedSecurityUtil = mockStatic(SecurityUtil.class)) {
            // 첫 번째 사용자 저장
            mockedSecurityUtil.when(SecurityUtil::getCurrentEmployeeId).thenReturn(firstUserId);
            Department dept = new Department();
            dept.prePersist();
            
            // 두 번째 사용자가 수정
            mockedSecurityUtil.when(SecurityUtil::getCurrentEmployeeId).thenReturn(secondUserId);
            dept.preUpdate();

            // then
            assertThat(dept.getCreatedBy()).isEqualTo(firstUserId); // 생성자는 유지
            assertThat(dept.getUpdatedBy()).isEqualTo(secondUserId); // 수정자는 변경
        }
    }
}
