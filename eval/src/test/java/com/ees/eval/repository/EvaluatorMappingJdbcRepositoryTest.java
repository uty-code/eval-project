package com.ees.eval.repository;

import com.ees.eval.domain.EvaluatorMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * EvaluatorMappingJdbcRepository 단위 테스트 (Mockito)
 * <p>
 * 1,000건 이상 대용량 매핑 처리 시 PreparedStatement.addBatch() 파라미터 바인딩과
 * 청크 크기(1,000건)가 정상적으로 동작하는지 검증합니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluatorMappingJdbcRepository 단위 테스트")
class EvaluatorMappingJdbcRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PreparedStatement preparedStatement;

    @InjectMocks
    private EvaluatorMappingJdbcRepository repository;

    @Test
    @DisplayName("빈 목록을 전달하면 batchUpdate를 호출하지 않고 빈 2차원 배열을 반환한다")
    void should_return_empty_array_when_mappings_empty() {
        int[][] result = repository.insertBatch(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("1,500건의 대용량 매핑 삽입 시 1,000건 단위 청크 배치로 jdbcTemplate.batchUpdate가 실행된다")
    void should_execute_batch_update_with_1000_chunk_size() throws SQLException {
        // given: 1,500건의 대량 가상 데이터 생성 (MSSQL 2,100개 파라미터 한계 돌파 규모)
        List<EvaluatorMapping> mappings = new ArrayList<>();
        for (long i = 1; i <= 1500; i++) {
            EvaluatorMapping m = EvaluatorMapping.builder()
                    .periodId(10L)
                    .evaluateeId(i)
                    .evaluatorId(i + 100)
                    .relationTypeCode("MANAGER")
                    .build();
            m.setCreatedBy(999L);
            m.setUpdatedBy(999L);
            mappings.add(m);
        }

        when(jdbcTemplate.batchUpdate(anyString(), anyList(), eq(1000), any()))
                .thenReturn(new int[][]{new int[1000], new int[500]});

        // when
        int[][] result = repository.insertBatch(mappings);

        // then
        assertThat(result).hasNumberOfRows(2);
        assertThat(result[0]).hasSize(1000);
        assertThat(result[1]).hasSize(500);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ParameterizedPreparedStatementSetter<EvaluatorMapping>> setterCaptor =
                ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);

        verify(jdbcTemplate, times(1)).batchUpdate(
                contains("insert into evaluator_mappings_51"),
                eq(mappings),
                eq(1000),
                setterCaptor.capture()
        );

        // 파라미터 바인딩 검증 (첫 번째 매핑 대상 6개 가변 파라미터 주입 확인)
        ParameterizedPreparedStatementSetter<EvaluatorMapping> setter = setterCaptor.getValue();
        setter.setValues(preparedStatement, mappings.get(0));

        verify(preparedStatement).setLong(1, 10L);
        verify(preparedStatement).setLong(2, 1L);
        verify(preparedStatement).setLong(3, 101L);
        verify(preparedStatement).setString(4, "MANAGER");
        verify(preparedStatement).setLong(5, 999L);
        verify(preparedStatement).setLong(6, 999L);
    }
}
