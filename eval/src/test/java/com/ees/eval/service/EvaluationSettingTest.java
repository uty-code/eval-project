package com.ees.eval.service;

import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.support.AbstractMssqlTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 평가 차수(EvaluationPeriod)와 평가 항목(EvaluationElement)의 통합 테스트 클래스입니다.
 * 상태 전이, 중복 체크, 가중치 검증, 차수-항목 연동 조회를 검증합니다.
 */
@SpringBootTest
@Transactional
class EvaluationSettingTest extends com.ees.eval.support.AbstractMssqlTest {

    @Autowired
    private EvaluationPeriodService periodService;

    @Autowired
    private EvaluationElementService elementService;

    /**
     * 테스트 시작 전 기존 데이터로 인한 충돌을 방지하기 위해
     * '진행 중' 상태인 모든 차수를 '완료' 상태로 전환합니다.
     */
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        periodService.getAllPeriods().stream()
                .filter(p -> "IN_PROGRESS".equals(p.statusCode()))
                .forEach(p -> periodService.transitionStatus(p.periodId(), "COMPLETED"));
    }

    /**
     * 차수 생성 후 상태 전이(PLANNED → IN_PROGRESS → COMPLETED → CLOSED)를 검증합니다.
     */
    @Test
    @DisplayName("차수 상태 전이 - Pattern Matching 기반 전체 흐름")
    void statusTransitionTest() {
        // given: 차수 생성 (초기 상태 PLANNED)
        EvaluationPeriodDTO dto = EvaluationPeriodDTO.builder()
                .periodYear(2026)
                .periodName("통합테스트용 상반기 평가")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build();
        EvaluationPeriodDTO saved = periodService.createPeriod(dto);
        assertThat(saved.statusCode()).isEqualTo("PLANNED");

        // when: PLANNED → IN_PROGRESS
        EvaluationPeriodDTO inProgress = periodService.transitionStatus(saved.periodId(), "IN_PROGRESS");
        assertThat(inProgress.statusCode()).isEqualTo("IN_PROGRESS");

        // when: IN_PROGRESS → COMPLETED
        EvaluationPeriodDTO completed = periodService.transitionStatus(saved.periodId(), "COMPLETED");
        assertThat(completed.statusCode()).isEqualTo("COMPLETED");

        // when: COMPLETED → CLOSED
        EvaluationPeriodDTO closed = periodService.transitionStatus(saved.periodId(), "CLOSED");
        assertThat(closed.statusCode()).isEqualTo("CLOSED");
    }

    /**
     * '진행 중' 상태의 차수가 이미 존재할 때 또 다른 차수를 '진행 중'으로 전환하면
     * 예외가 발생하는지 검증합니다.
     */
    @Test
    @DisplayName("차수 중복 체크 - 진행 중 차수 2개 방지")
    void duplicateInProgressTest() {
        // given: 첫 번째 차수를 IN_PROGRESS로 전환
        EvaluationPeriodDTO dto1 = EvaluationPeriodDTO.builder()
                .periodYear(2026).periodName("테스트용 상반기")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 6, 30))
                .build();
        EvaluationPeriodDTO saved1 = periodService.createPeriod(dto1);
        periodService.transitionStatus(saved1.periodId(), "IN_PROGRESS");

        // given: 두 번째 차수 생성
        EvaluationPeriodDTO dto2 = EvaluationPeriodDTO.builder()
                .periodYear(2026).periodName("테스트용 하반기")
                .startDate(LocalDate.of(2026, 7, 1)).endDate(LocalDate.of(2026, 12, 31))
                .build();
        EvaluationPeriodDTO saved2 = periodService.createPeriod(dto2);

        // then: 두 번째 차수를 IN_PROGRESS로 전환 시도 → 예외 발생
        assertThatThrownBy(() -> periodService.transitionStatus(saved2.periodId(), "IN_PROGRESS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중");
    }

    /**
     * 유효하지 않은 상태 전이 시 예외가 발생하는지 검증합니다.
     */
    @Test
    @DisplayName("차수 상태 전이 실패 - 잘못된 전이 경로")
    void invalidTransitionTest() {
        EvaluationPeriodDTO dto = EvaluationPeriodDTO.builder()
                .periodYear(2026).periodName("테스트 차수")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 6, 30))
                .build();
        EvaluationPeriodDTO saved = periodService.createPeriod(dto);

        // PLANNED → COMPLETED (건너뜀) → 예외
        assertThatThrownBy(() -> periodService.transitionStatus(saved.periodId(), "COMPLETED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("유효하지 않은 상태 전이");
    }

    /**
     * 평가 항목 생성 및 가중치 합계 100 검증을 수행합니다.
     */
    @Test
    @DisplayName("항목 가중치 검증 - 합계 100 초과 시 예외")
    void weightValidationTest() {
        // given: 차수 생성
        EvaluationPeriodDTO periodDto = EvaluationPeriodDTO.builder()
                .periodYear(2026).periodName("가중치 테스트 차수")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 6, 30))
                .build();
        EvaluationPeriodDTO period = periodService.createPeriod(periodDto);

        // when: 성과 항목 60% + 역량 항목 30% = 90% (성공)
        elementService.createElement(EvaluationElementDTO.builder()
                .periodId(period.periodId()).elementTypeCode("PERFORMANCE")
                .elementName("업무 달성도").maxScore(new BigDecimal("100.00"))
                .weight(new BigDecimal("60.00")).build());

        elementService.createElement(EvaluationElementDTO.builder()
                .periodId(period.periodId()).elementTypeCode("COMPETENCY")
                .elementName("리더십").maxScore(new BigDecimal("100.00"))
                .weight(new BigDecimal("30.00")).build());

        // then: 추가로 20% 시도 → 합계 110% → 예외 발생
        assertThatThrownBy(() -> elementService.createElement(EvaluationElementDTO.builder()
                .periodId(period.periodId()).elementTypeCode("COMPETENCY")
                .elementName("의사소통").maxScore(new BigDecimal("100.00"))
                .weight(new BigDecimal("20.00")).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("가중치 합이 100을 초과");

        // then: 10%로 정확히 맞추면 성공, 합계 = 100
        elementService.createElement(EvaluationElementDTO.builder()
                .periodId(period.periodId()).elementTypeCode("COMPETENCY")
                .elementName("의사소통").maxScore(new BigDecimal("100.00"))
                .weight(new BigDecimal("10.00")).build());

        assertThat(elementService.validateWeightSum(period.periodId(), null)).isTrue();
    }

    /**
     * 차수를 생성하고 귀속된 항목들을 한 번에 조회하는 연동 테스트입니다.
     * Sequenced Collections(getFirst/getLast)를 활용합니다.
     */
    @Test
    @DisplayName("차수-항목 연동 조회 - 차수별 항목 리스트")
    void periodElementIntegrationTest() {
        // given: 차수 생성
        EvaluationPeriodDTO periodDto = EvaluationPeriodDTO.builder()
                .periodYear(2026).periodName("연동 테스트 차수")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31))
                .build();
        EvaluationPeriodDTO period = periodService.createPeriod(periodDto);

        // given: 성과 + 역량 항목 등록
        elementService.createElement(EvaluationElementDTO.builder()
                .periodId(period.periodId()).elementTypeCode("COMPETENCY")
                .elementName("팀워크").maxScore(new BigDecimal("100.00"))
                .weight(new BigDecimal("40.00")).build());

        elementService.createElement(EvaluationElementDTO.builder()
                .periodId(period.periodId()).elementTypeCode("PERFORMANCE")
                .elementName("목표 달성률").maxScore(new BigDecimal("100.00"))
                .weight(new BigDecimal("60.00")).build());

        // when: 차수별 항목 조회
        List<EvaluationElementDTO> elements = elementService.getElementsByPeriodId(period.periodId(), null);

        // then: Sequenced Collections 활용한 검증 (element_type_code ASC 정렬)
        assertThat(elements).hasSize(2);
        assertThat(elements.getFirst().elementTypeCode()).isEqualTo("COMPETENCY");
        assertThat(elements.getLast().elementTypeCode()).isEqualTo("PERFORMANCE");

        // then: 가중치 합 정확히 100 검증
        assertThat(elementService.validateWeightSum(period.periodId(), null)).isTrue();
    }
}
