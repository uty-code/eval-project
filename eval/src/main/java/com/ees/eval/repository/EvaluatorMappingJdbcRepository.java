package com.ees.eval.repository;

import com.ees.eval.domain.EvaluatorMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * 평가자 매핑 대량 처리를 위한 JDBC 전용 Repository 구현체입니다.
 * <p>
 * MyBatis &lt;foreach&gt; 동적 SQL의 파라미터 개수 제약(MSSQL 2,100개 제한)과
 * 거대 SQL 파싱 비용을 원천 차단하기 위해 Spring 표준 JdbcTemplate.batchUpdate()를 활용합니다.
 * 고정 메타 컬럼(is_deleted, version, created_at, updated_at)을 SQL 상수로 처리하여
 * 바인딩 파라미터 수를 가변 6개로 최적화하고 1,000건 단위 패킷 전송을 지원합니다.
 * </p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EvaluatorMappingJdbcRepository {

    private static final int BATCH_SIZE = 1000;

    private static final String INSERT_BATCH_SQL = """
            insert into evaluator_mappings_51 (
                period_id, evaluatee_id, evaluator_id, relation_type_code,
                created_by, updated_by,
                is_deleted, version, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, 'n', 0, sysdatetime(), sysdatetime())
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 대용량 평가자 매핑 엔티티 목록을 PreparedStatement.addBatch() 기반으로 일괄 삽입합니다.
     *
     * @param mappings 일괄 삽입할 평가자 매핑 도메인 엔티티 목록
     * @return 각 배치 청크별 영향받은 행 수의 배열
     */
    public int[][] insertBatch(List<EvaluatorMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return new int[0][0];
        }

        log.debug("[EvaluatorMappingJdbcRepository] 매핑 대량 삽입 시작 - 총 대상 건수: {}, 청크 크기: {}", mappings.size(), BATCH_SIZE);

        ParameterizedPreparedStatementSetter<EvaluatorMapping> setter = new ParameterizedPreparedStatementSetter<>() {
            @Override
            public void setValues(PreparedStatement ps, EvaluatorMapping mapping) throws SQLException {
                ps.setLong(1, mapping.getPeriodId());
                ps.setLong(2, mapping.getEvaluateeId());
                ps.setLong(3, mapping.getEvaluatorId());
                ps.setString(4, mapping.getRelationTypeCode());

                if (mapping.getCreatedBy() != null) {
                    ps.setLong(5, mapping.getCreatedBy());
                } else {
                    ps.setNull(5, Types.BIGINT);
                }

                if (mapping.getUpdatedBy() != null) {
                    ps.setLong(6, mapping.getUpdatedBy());
                } else {
                    ps.setNull(6, Types.BIGINT);
                }
            }
        };

        int[][] result = jdbcTemplate.batchUpdate(INSERT_BATCH_SQL, mappings, BATCH_SIZE, setter);
        log.debug("[EvaluatorMappingJdbcRepository] 매핑 대량 삽입 완료 - 처리된 청크 배치 수: {}", result.length);
        return result;
    }
}
