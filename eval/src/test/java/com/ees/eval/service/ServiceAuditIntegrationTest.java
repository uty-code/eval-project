package com.ees.eval.service;

import com.ees.eval.mapper.EvaluationElementMapper;
import com.ees.eval.service.impl.EvaluationElementServiceImpl;
import com.ees.eval.util.SecurityUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("서비스 레이어 감사(Audit) 필드 연동 테스트")
class ServiceAuditIntegrationTest {

    @Mock
    private EvaluationElementMapper elementMapper;

    @InjectMocks
    private EvaluationElementServiceImpl elementService;

    @Test
    @DisplayName("서비스의 삭제 메서드 호출 시 SecurityUtil을 통해 얻은 사용자 ID가 매퍼로 전달되어야 한다")
    void deleteElement_ShouldPassCurrentUserIdFromSecurityUtil() {
        // given
        Long targetElementId = 100L;
        Long mockUserId = 5555L; // 테스트용 ID

        try (MockedStatic<SecurityUtil> mockedSecurityUtil = mockStatic(SecurityUtil.class)) {
            // SecurityUtil이 5555L을 반환하도록 설정
            mockedSecurityUtil.when(SecurityUtil::getCurrentEmployeeId).thenReturn(mockUserId);
            
            // Mapper가 성공적으로 1행을 삭제했다고 가정
            given(elementMapper.softDelete(eq(targetElementId), eq(mockUserId), any())).willReturn(1);

            // when
            elementService.deleteElement(targetElementId);

            // then
            // 매퍼의 softDelete 메서드가 호출될 때, 두 번째 인자로 mockUserId(5555L)가 정확히 전달되는지 확인
            verify(elementMapper).softDelete(eq(targetElementId), eq(mockUserId), any());
        }
    }
}
