package com.ees.eval.mapper;

import com.ees.eval.domain.EvaluationPeriod;
import com.ees.eval.domain.EvaluatorMapping;
import com.ees.eval.support.AbstractMssqlTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("EvaluatorMappingMapper 통합 테스트 (MSSQL)")
class EvaluatorMappingMapperTest extends AbstractMssqlTest {

    @Autowired
    private EvaluationPeriodMapper periodMapper;

    @Autowired
    private EvaluatorMappingMapper mappingMapper;

    private Long validPeriodId;

    @BeforeEach
    void setUp() {
        // 테스트용 차수 생성 (FK 제약 조건 충족)
        EvaluationPeriod period = EvaluationPeriod.builder()
                .periodYear(2026)
                .periodName("테스트차수")
                .statusCode("READY")
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now().plusDays(30))
                .build();
        period.prePersist();
        periodMapper.insert(period);
        validPeriodId = period.getPeriodId();
    }

    @Test
    @DisplayName("findByEvaluatorId: relationTypeCode가 null이면 모든 유형을 조회해야 한다")
    void findByEvaluatorId_ShouldReturnAll_WhenTypeIsNull() {
        // given
        Long evaluatorId = 1000L; // admin
        Long evaluatee1 = 1001L; // 김철수
        Long evaluatee2 = 1002L; // 이영희

        mappingMapper.insert(createMapping(validPeriodId, evaluatee1, evaluatorId, "SELF"));
        mappingMapper.insert(createMapping(validPeriodId, evaluatee2, evaluatorId, "SUBORDINATE"));

        // when
        List<EvaluatorMapping> results = mappingMapper.findByEvaluatorId(validPeriodId, evaluatorId, null);

        // then
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        assertThat(results.stream().map(EvaluatorMapping::getRelationTypeCode))
                .contains("SELF", "SUBORDINATE");
    }

    @Test
    @DisplayName("findByEvaluatorId: 특정 relationTypeCode를 지정하면 해당 유형만 조회해야 한다")
    void findByEvaluatorId_ShouldFilterByType() {
        // given
        Long evaluatorId = 1003L; // 박준호
        Long evaluatee1 = 1004L; // 최미경
        Long evaluatee2 = 1005L; // 정민우

        mappingMapper.insert(createMapping(validPeriodId, evaluatee1, evaluatorId, "SELF"));
        mappingMapper.insert(createMapping(validPeriodId, evaluatee2, evaluatorId, "SUBORDINATE"));

        // when
        List<EvaluatorMapping> results = mappingMapper.findByEvaluatorId(validPeriodId, evaluatorId, "SUBORDINATE");

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRelationTypeCode()).isEqualTo("SUBORDINATE");
        assertThat(results.get(0).getEvaluateeId()).isEqualTo(evaluatee2);
    }

    private EvaluatorMapping createMapping(Long periodId, Long evaluateeId, Long evaluatorId, String type) {
        EvaluatorMapping m = EvaluatorMapping.builder()
                .periodId(periodId)
                .evaluateeId(evaluateeId)
                .evaluatorId(evaluatorId)
                .relationTypeCode(type)
                .build();
        m.prePersist();
        return m;
    }
}
