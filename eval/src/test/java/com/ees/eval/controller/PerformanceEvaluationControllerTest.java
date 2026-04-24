package com.ees.eval.controller;

import com.ees.eval.domain.EvaluatorMapping;
import java.math.BigDecimal;
import com.ees.eval.domain.Evaluation;
import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.dto.EvaluatorMappingDTO;
import com.ees.eval.dto.EvaluationElementDTO;
import com.ees.eval.mapper.EvaluationMapper;
import com.ees.eval.mapper.EvaluatorMappingMapper;
import com.ees.eval.service.EvaluationElementService;
import com.ees.eval.service.EvaluationPeriodService;
import com.ees.eval.service.EvaluatorMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasValue;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PerformanceEvaluationController의 테스트 클래스입니다.
 * 하나씩 테스트를 작성하고 검증하는 원칙을 준수합니다.
 */
@ExtendWith(MockitoExtension.class)
class PerformanceEvaluationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EvaluationPeriodService periodService;
    @Mock
    private EvaluatorMappingService mappingService;
    @Mock
    private EvaluationElementService elementService;
    @Mock
    private EvaluationMapper evaluationMapper;
    @Mock
    private EvaluatorMappingMapper evaluatorMappingMapper;

    @InjectMocks
    private PerformanceEvaluationController performanceEvaluationController;

    private UserDetails mockUser;

    @BeforeEach
    void setUp() {
        // @AuthenticationPrincipal 처리를 위한 모의 ArgumentResolver 설정
        mockUser = User.withUsername("1001")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(performanceEvaluationController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().isAssignableFrom(UserDetails.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return mockUser;
                    }
                })
                .build();
    }

    /**
     * [GET] 평가 목록 조회 - 활성 차수가 자동으로 선택되는지 검증합니다.
     * @throws Exception 테스트 실패 시 발생
     */
    @Test
    @DisplayName("목록 조회 - 활성 차수가 있을 경우 자동으로 선택되어야 한다")
    void list_ShouldSelectActivePeriodAutomatically() throws Exception {
        // given
        EvaluationPeriodDTO activePeriod = EvaluationPeriodDTO.builder()
                .periodId(1L).periodName("2024 상반기").statusCode("ACTIVE").build();
        EvaluationPeriodDTO inactivePeriod = EvaluationPeriodDTO.builder()
                .periodId(2L).periodName("2023 하반기").statusCode("COMPLETED").build();

        given(periodService.getAllPeriods()).willReturn(List.of(inactivePeriod, activePeriod));
        given(mappingService.getMyEvaluationTasks(anyLong(), anyLong())).willReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(get("/eval/performance"))
                .andExpect(status().isOk())
                .andExpect(view().name("eval/performance/list"))
                .andExpect(model().attribute("selectedPeriod", activePeriod))
                .andExpect(model().attributeExists("periods", "tasks"));
    }

    /**
     * [GET] 평가 목록 조회 - 자가평가 제출 여부(성과/역량) 계산 로직을 검증합니다.
     */
    @Test
    @DisplayName("목록 조회 - 자가평가 제출 시 성과/역량별 완료 여부가 모델에 반영되어야 한다")
    void list_ShouldCalculateSubmissionStatus() throws Exception {
        // given
        Long periodId = 1L;
        Long mappingId = 100L;
        EvaluationPeriodDTO period = EvaluationPeriodDTO.builder().periodId(periodId).statusCode("ACTIVE").build();
        EvaluatorMappingDTO selfTask = EvaluatorMappingDTO.builder()
                .mappingId(mappingId).evaluateeId(1001L).relationTypeCode("SELF").build();

        given(periodService.getAllPeriods()).willReturn(List.of(period));
        given(periodService.getPeriodById(anyLong())).willReturn(period);
        given(mappingService.getMyEvaluationTasks(anyLong(), anyLong())).willReturn(List.of(selfTask));

        // 성과(PERFORMANCE) 항목 1개 제출됨
        Evaluation submittedEval = Evaluation.builder().elementId(10L).confirmStatusCode("SUBMITTED").build();
        given(evaluationMapper.findByMappingId(mappingId)).willReturn(List.of(submittedEval));

        com.ees.eval.dto.EvaluationElementDTO perfElement = com.ees.eval.dto.EvaluationElementDTO.builder()
                .elementId(10L).elementTypeCode("PERFORMANCE").build();
        com.ees.eval.dto.EvaluationElementDTO compElement = com.ees.eval.dto.EvaluationElementDTO.builder()
                .elementId(20L).elementTypeCode("COMPETENCY").build();
        given(elementService.getElementsByPeriodId(anyLong(), any())).willReturn(List.of(perfElement, compElement));

        // when & then
        mockMvc.perform(get("/eval/performance").param("periodId", periodId.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selfPerfSubmitted", true))
                .andExpect(model().attribute("selfCompSubmitted", false));
    }

    /**
     * [GET] 평가 폼 조회 - 요청된 evalType(성과/역량)에 따라 평가 요소가 필터링되는지 검증합니다.
     */
    @Test
    @DisplayName("폼 조회 - 성과 평가 요청 시 성과 항목만 노출되어야 한다")
    void getForm_ShouldFilterElementsByType() throws Exception {
        // given
        Long mappingId = 100L;
        EvaluatorMappingDTO mapping = EvaluatorMappingDTO.builder()
                .mappingId(mappingId).periodId(1L).evaluateeId(2001L).relationTypeCode("SELF").build();
        
        given(mappingService.getMappingById(mappingId)).willReturn(mapping);
        given(evaluationMapper.findByMappingId(mappingId)).willReturn(Collections.emptyList());

        com.ees.eval.dto.EvaluationElementDTO perfElement = com.ees.eval.dto.EvaluationElementDTO.builder()
                .elementId(10L).elementTypeCode("PERFORMANCE").build();
        com.ees.eval.dto.EvaluationElementDTO compElement = com.ees.eval.dto.EvaluationElementDTO.builder()
                .elementId(20L).elementTypeCode("COMPETENCY").build();
        given(elementService.getElementsByPeriodId(anyLong(), any())).willReturn(List.of(perfElement, compElement));

        // when & then
        mockMvc.perform(get("/eval/performance/form")
                        .param("mappingId", mappingId.toString())
                        .param("evalType", "PERFORMANCE"))
                .andExpect(status().isOk())
                .andExpect(view().name("eval/performance/form"))
                .andExpect(model().attribute("elements", List.of(perfElement))) // 성과 항목만 포함
                .andExpect(model().attribute("evalType", "PERFORMANCE"));
    }

    /**
     * [GET] 평가 폼 조회 - 상급자(MANAGER) 평가 시 피평가자의 자가평가 코멘트가 정상적으로 로드되는지 검증합니다.
     */
    @Test
    @DisplayName("폼 조회 - 상급자 평가 시 팀원의 자가평가 코멘트가 모델에 포함되어야 한다")
    void getForm_ShouldLoadSelfEvaluationCommentsForManager() throws Exception {
        // given
        Long mappingId = 100L;
        Long evaluateeId = 2001L;
        Long periodId = 1L;
        Long selfMappingId = 50L;

        // 1. 현재 평가 매핑 (MANAGER 관계)
        EvaluatorMappingDTO mapping = EvaluatorMappingDTO.builder()
                .mappingId(mappingId).periodId(periodId).evaluateeId(evaluateeId).relationTypeCode("MANAGER").build();
        given(mappingService.getMappingById(mappingId)).willReturn(mapping);
        given(evaluationMapper.findByMappingId(mappingId)).willReturn(Collections.emptyList());

        // 2. 피평가자의 자가평가 매핑 찾기
        EvaluatorMapping selfMapping = new EvaluatorMapping();
        selfMapping.setMappingId(selfMappingId);
        selfMapping.setRelationTypeCode("SELF");
        selfMapping.setIsDeleted("n");
        given(evaluatorMappingMapper.findByEvaluateeId(periodId, evaluateeId)).willReturn(List.of(selfMapping));

        // 3. 자가평가 데이터 모킹 (코멘트 포함)
        Evaluation selfEval = Evaluation.builder()
                .elementId(10L).comments("자가평가 성과 코멘트").confirmStatusCode("SUBMITTED").build();
        given(evaluationMapper.findByMappingId(selfMappingId)).willReturn(List.of(selfEval));

        given(elementService.getElementsByPeriodId(anyLong(), any())).willReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(get("/eval/performance/form")
                        .param("mappingId", mappingId.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("selfEvalMap"))
                .andExpect(model().attribute("selfEvalMap", java.util.Map.of(10L, "자가평가 성과 코멘트")));
    }

    /**
     * [POST] 평가 제출 - 기존 데이터가 없는 경우 신규 저장(Insert)이 수행되는지 검증합니다.
     */
    @Test
    @DisplayName("제출 - 기존 데이터가 없으면 신규 저장하고 리다이렉트되어야 한다")
    void submitForm_ShouldInsertNewEvaluation() throws Exception {
        // given
        Long mappingId = 100L;
        Long elementId = 10L;
        String evalType = "PERFORMANCE";

        given(evaluationMapper.findByMappingIdAndElementId(mappingId, elementId)).willReturn(Optional.empty());

        // when & then
        mockMvc.perform(post("/eval/performance/submit")
                        .param("mappingId", mappingId.toString())
                        .param("evalType", evalType)
                        .param("score_" + elementId, "4.5")
                        .param("comment_" + elementId, "훌륭한 성과입니다."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/eval/performance/form?mappingId=" + mappingId + "&evalType=" + evalType))
                .andExpect(flash().attributeExists("successMessage"));

        verify(evaluationMapper).insert(any(Evaluation.class));
    }

    /**
     * [POST] 평가 제출 - 기존 데이터가 있는 경우 수정(Update)이 수행되는지 검증합니다.
     */
    @Test
    @DisplayName("제출 - 기존 데이터가 있으면 수정하고 리다이렉트되어야 한다")
    void submitForm_ShouldUpdateExistingEvaluation() throws Exception {
        // given
        Long mappingId = 100L;
        Long elementId = 10L;
        String evalType = "PERFORMANCE";

        Evaluation existingEval = Evaluation.builder()
                .mappingId(mappingId)
                .elementId(elementId)
                .score(BigDecimal.valueOf(3.0))
                .comments("이전 코멘트")
                .build();

        given(evaluationMapper.findByMappingIdAndElementId(mappingId, elementId))
                .willReturn(Optional.of(existingEval));

        // when & then
        mockMvc.perform(post("/eval/performance/submit")
                        .param("mappingId", mappingId.toString())
                        .param("evalType", evalType)
                        .param("score_" + elementId, "4.0")
                        .param("comment_" + elementId, "수정된 코멘트"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/eval/performance/form?mappingId=" + mappingId + "&evalType=" + evalType))
                .andExpect(flash().attributeExists("successMessage"));

        verify(evaluationMapper).update(any(Evaluation.class));
    }

    /**
     * [POST] 평가 제출 - 잘못된 점수 형식(숫자가 아닌 문자열)이 입력된 경우의 예외 처리를 검증합니다.
     */
    @Test
    @DisplayName("제출 - 점수 형식이 잘못되면 에러 메시지와 함께 리다이렉트되어야 한다")
    void submitForm_ShouldHandleInvalidScoreFormat() throws Exception {
        // given
        Long mappingId = 100L;
        Long elementId = 10L;
        String evalType = "PERFORMANCE";

        // when & then
        mockMvc.perform(post("/eval/performance/submit")
                        .param("mappingId", mappingId.toString())
                        .param("evalType", evalType)
                        .param("score_" + elementId, "invalid_score") // 숫자가 아님
                        .param("comment_" + elementId, "코멘트"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/eval/performance/form?mappingId=" + mappingId + "&evalType=" + evalType))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("평가 목록 조회 - 등록된 차수가 전혀 없는 경우 (Gate A)")
    void list_ShouldHandleNoPeriods() throws Exception {
        // given
        when(periodService.getAllPeriods()).thenReturn(java.util.Collections.emptyList());

        // when & then
        mockMvc.perform(get("/eval/performance"))
            .andExpect(status().isOk())
            .andExpect(view().name("eval/performance/list"))
            .andExpect(model().attributeExists("periods"))
            .andExpect(model().attributeDoesNotExist("selectedPeriod"));

        verify(periodService).getAllPeriods();
        verifyNoInteractions(mappingService, elementService, evaluationMapper, evaluatorMappingMapper);
    }

    @Test
    @DisplayName("평가 목록 조회 - 자가평가 미제출 및 팀원 매핑 부재 상황 (Gate B, C, D)")
    void list_ShouldHandleIncompleteTasks() throws Exception {
        // given
        Long empId = 1L;
        Long periodId = 10L;
        EvaluationPeriodDTO period = EvaluationPeriodDTO.builder()
                .periodId(periodId).periodName("2024 상반기").statusCode("ACTIVE").build();
        when(periodService.getAllPeriods()).thenReturn(List.of(period));

        // 나의 업무 목록: SELF 1개, MANAGER 1개
        EvaluatorMappingDTO selfTask = EvaluatorMappingDTO.builder()
                .mappingId(100L).periodId(periodId).evaluateeId(empId).evaluatorId(empId)
                .relationTypeCode("SELF").isDeleted("n").build();
        EvaluatorMappingDTO teamTask = EvaluatorMappingDTO.builder()
                .mappingId(1001L).periodId(periodId).evaluateeId(2L).evaluatorId(empId)
                .relationTypeCode("MANAGER").isDeleted("n").build();
        when(mappingService.getMyEvaluationTasks(anyLong(), anyLong())).thenReturn(List.of(selfTask, teamTask));

        // Gate B: 나의 자가평가 데이터가 아예 없음 (empty list)
        when(evaluationMapper.findByMappingId(100L)).thenReturn(Collections.emptyList());

        // 팀원 업무 처리
        when(elementService.getElementsByPeriodId(periodId, null)).thenReturn(Collections.emptyList());
        when(evaluationMapper.findByMappingId(1001L)).thenReturn(Collections.emptyList());

        // Gate C & D: 팀원의 SELF 매핑 조회
        // 팀원(2L)에 대한 SELF 매핑이 없다고 가정 (Gate C 공략)
        when(evaluatorMappingMapper.findByEvaluateeId(periodId, 2L)).thenReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(get("/eval/performance"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("selfPerfSubmitted", false)) // Gate B 결과
            .andExpect(model().attribute("selfCompSubmitted", false)) // Gate B 결과
            .andExpect(result -> {
                java.util.Map<Long, Boolean> map = (java.util.Map<Long, Boolean>) result.getModelAndView().getModel().get("evaluateeSelfSubmittedMap");
                org.junit.jupiter.api.Assertions.assertNotNull(map);
                org.junit.jupiter.api.Assertions.assertTrue(map.containsKey(1001L));
                org.junit.jupiter.api.Assertions.assertFalse(map.get(1001L));
            }); // Gate C 결과

        verify(evaluationMapper).findByMappingId(100L);
        verify(evaluatorMappingMapper).findByEvaluateeId(periodId, 2L);
    }

    /**
     * [GET] 평가 폼 조회 - 관계 유형(SELF/EXECUTIVE) 및 자가평가 참조 데이터 충돌 해결(Gate E, F, G)을 검증합니다.
     */
    @Test
    @DisplayName("폼 조회 - EXECUTIVE 관계 유형 인식 및 자가평가 데이터 충돌 시 첫 번째 값을 유지해야 한다")
    void getForm_ShouldHandleVariousRelationTypes() throws Exception {
        // given
        Long mappingId = 500L;
        Long periodId = 1L;
        Long evaluateeId = 200L;
        
        // EXECUTIVE 유형 매핑 (Gate F 공략)
        EvaluatorMappingDTO mapping = EvaluatorMappingDTO.builder()
                .mappingId(mappingId).periodId(periodId).evaluateeId(evaluateeId)
                .relationTypeCode("EXECUTIVE").build();
        
        given(mappingService.getMappingById(mappingId)).willReturn(mapping);
        
        // 피평가자의 자가평가 데이터 조회 (Gate G: 충돌 발생 시나리오)
        // 동일한 elementId(10L)를 가진 평가 데이터가 2개 있다고 가정
        Evaluation eval1 = Evaluation.builder().elementId(10L).score(java.math.BigDecimal.valueOf(5)).comments("첫 번째").build();
        Evaluation eval2 = Evaluation.builder().elementId(10L).score(java.math.BigDecimal.valueOf(3)).comments("두 번째").build();
        
        EvaluatorMapping selfMapping = new EvaluatorMapping();
        selfMapping.setMappingId(600L);
        selfMapping.setRelationTypeCode("SELF");
        selfMapping.setIsDeleted("n");
        
        given(evaluatorMappingMapper.findByEvaluateeId(periodId, evaluateeId)).willReturn(List.of(selfMapping));
        // 모든 findByMappingId 호출에 대해 대응 (500L: 현재 폼, 600L: 자가평가 참조)
        // 600L일 때만 eval1, eval2를 반환하고 나머지는 빈 리스트를 반환하도록 정교하게 설정
        given(evaluationMapper.findByMappingId(anyLong())).willAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (Long.valueOf(600L).equals(id)) return List.of(eval1, eval2);
            return Collections.emptyList();
        });
        
        EvaluationElementDTO element = EvaluationElementDTO.builder()
                .elementId(10L).elementTypeCode("PERFORMANCE").build();
        given(elementService.getElementsByPeriodId(anyLong(), any())).willReturn(List.of(element));

        // when & then
        mockMvc.perform(get("/eval/performance/form").param("mappingId", mappingId.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("mapping", "evalType", "elements", "savedMap", "submitted", "selfEvalMap"))
                .andExpect(model().attribute("submitted", false))
                .andExpect(result -> {
                    // Gate G 결과 검증: selfEvalMap이 (a, b) -> a 로직을 통해 "첫 번째" 코멘트를 가지고 있어야 함
                    Map<Long, String> selfEvalMap = (Map<Long, String>) result.getModelAndView().getModel().get("selfEvalMap");
                    org.junit.jupiter.api.Assertions.assertEquals("첫 번째", selfEvalMap.get(10L));
                });
    }

    /**
     * [GET] 평가 폼 조회 - 잘못된 evalType 파라미터 처리(Gate H)를 검증합니다.
     */
    @Test
    @DisplayName("폼 조회 - 잘못된 evalType이 전달되면 PERFORMANCE로 기본 설정되어야 한다")
    void getForm_ShouldDefaultToPerformanceForInvalidEvalType() throws Exception {
        // given
        Long mappingId = 700L;
        EvaluatorMappingDTO mapping = EvaluatorMappingDTO.builder()
                .mappingId(mappingId).periodId(1L).relationTypeCode("SELF").build();
        
        given(mappingService.getMappingById(mappingId)).willReturn(mapping);
        given(elementService.getElementsByPeriodId(anyLong(), any())).willReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(get("/eval/performance/form")
                .param("mappingId", mappingId.toString())
                .param("evalType", "INVALID")) // Gate H 유발
                .andExpect(status().isOk())
                .andExpect(model().attribute("evalType", "PERFORMANCE")); // 기본값 복구 확인
    }

    /**
     * [POST] 평가 제출 - 점수 파싱 실패 시 처리(Gate I')를 검증합니다.
     */
    @Test
    @DisplayName("제출 - 점수 형식이 잘못되면 에러 메시지와 함께 폼으로 리다이렉트되어야 한다")
    void submitForm_ShouldRedirectOnInvalidScore() throws Exception {
        // given
        Long mappingId = 800L;
        String evalType = "PERFORMANCE";

        // when & then
        mockMvc.perform(post("/eval/performance/submit")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")) // empId=1001
                .param("mappingId", mappingId.toString())
                .param("evalType", evalType)
                .param("score_10", "INVALID")) // Gate I' 유발
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "잘못된 점수 형식입니다."))
                .andExpect(view().name("redirect:/eval/performance/form?mappingId=" + mappingId + "&evalType=" + evalType));
    }

    /**
     * [POST] 평가 제출 - 숫자가 아닌 elementId 키 처리(Gate J)를 검증합니다.
     */
    @Test
    @DisplayName("제출 - 숫자가 아닌 elementId 키가 전달되면 해당 항목은 무시되어야 한다")
    void submitForm_ShouldIgnoreInvalidElementIdKeys() throws Exception {
        // given
        Long mappingId = 900L;
        
        // when & then
        mockMvc.perform(post("/eval/performance/submit")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001"))
                .param("mappingId", mappingId.toString())
                .param("score_XYZ", "100") // Gate J 유발 (XYZ는 숫자가 아님)
                .param("evalType", "PERFORMANCE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name(org.hamcrest.Matchers.containsString("redirect:/eval/performance/form")));
        
        // 에러 없이 성공적으로 리다이렉트되면 OK (예외가 ignore 됨)
    }

    /**
     * [POST] 평가 제출 - 데이터 업데이트 및 삽입(Gate K/L)을 검증합니다.
     */
    @Test
    @DisplayName("제출 - 기존 데이터가 있으면 업데이트하고, 없으면 신규 저장해야 한다")
    void submitForm_ShouldPerformUpsertCorrectly() throws Exception {
        // given
        Long mappingId = 1000L;
        Long elementIdExisting = 10L;
        Long elementIdNew = 20L;
        
        Evaluation existingEval = Evaluation.builder()
                .mappingId(mappingId).elementId(elementIdExisting).build();
        
        given(evaluationMapper.findByMappingIdAndElementId(mappingId, elementIdExisting))
                .willReturn(java.util.Optional.of(existingEval));
        given(evaluationMapper.findByMappingIdAndElementId(mappingId, elementIdNew))
                .willReturn(java.util.Optional.empty());

        // when & then
        mockMvc.perform(post("/eval/performance/submit")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001"))
                .param("mappingId", mappingId.toString())
                .param("evalType", "PERFORMANCE")
                .param("score_10", "95.5")
                .param("comment_10", "업데이트된 코멘트")
                .param("score_20", "80")
                .param("comment_20", "신규 코멘트"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
        
        // then: verify calls
        org.mockito.Mockito.verify(evaluationMapper).update(org.mockito.ArgumentMatchers.any(Evaluation.class));
        org.mockito.Mockito.verify(evaluationMapper).insert(org.mockito.ArgumentMatchers.any(Evaluation.class));
    }

    /**
     * [GET] 평가 목록 조회 - 기간이 없는 경우(Gate N, P)를 검증합니다.
     */
    @Test
    @DisplayName("목록 - 등록된 기간이 없으면 selectedPeriod가 모델에 없어야 한다")
    void list_WithNoPeriods_ShouldHandleEmpty() throws Exception {
        given(periodService.getAllPeriods()).willReturn(Collections.emptyList());

        mockMvc.perform(get("/eval/performance")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("selectedPeriod"));
    }

    /**
     * [GET] 평가 목록 조회 - periodId가 전달된 경우(Gate M) 및 ACTIVE 기간이 없는 경우(Gate O)를 검증합니다.
     */
    @Test
    @DisplayName("목록 - periodId가 전달되면 해당 기간을 사용하고, 자동 선택 시 ACTIVE가 없으면 첫 번째를 사용해야 한다")
    void list_WithPeriodId_ShouldUseSpecifiedPeriod() throws Exception {
        EvaluationPeriodDTO p1 = EvaluationPeriodDTO.builder().periodId(1L).statusCode("INACTIVE").build();
        EvaluationPeriodDTO p2 = EvaluationPeriodDTO.builder().periodId(2L).statusCode("INACTIVE").build();
        
        given(periodService.getAllPeriods()).willReturn(List.of(p1, p2));
        given(periodService.getPeriodById(2L)).willReturn(p2);

        // 1. periodId 전달 (Gate M)
        mockMvc.perform(get("/eval/performance").param("periodId", "2")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedPeriod", p2));

        // 2. periodId 미전달 + ACTIVE 없음 (Gate O orElse)
        mockMvc.perform(get("/eval/performance")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedPeriod", p1)); // 첫 번째인 p1 선택 확인
    }

    /**
     * [GET] 평가 목록 조회 - selfTask가 없는 경우(Gate Q, R)를 검증합니다.
     */
    @Test
    @DisplayName("목록 - 본인 평가 과업이 없으면 selfTask가 null이고 제출 여부는 false여야 한다")
    void list_WithNoSelfTask_ShouldHandleNull() throws Exception {
        EvaluationPeriodDTO p1 = EvaluationPeriodDTO.builder().periodId(1L).statusCode("ACTIVE").build();
        given(periodService.getAllPeriods()).willReturn(List.of(p1));
        // 본인 과업(SELF) 없이 팀원 과업만 반환
        EvaluatorMappingDTO teamTask = EvaluatorMappingDTO.builder().mappingId(101L).relationTypeCode("MANAGER").build();
        given(mappingService.getMyEvaluationTasks(anyLong(), anyLong())).willReturn(List.of(teamTask));

        mockMvc.perform(get("/eval/performance")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selfTask", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("selfPerfSubmitted", false))
                .andExpect(model().attribute("selfCompSubmitted", false));
    }

    @Test
    @DisplayName("목록 - EXECUTIVE 관계 유형도 tasks에 포함되어야 한다 (Gate 71 || 브랜치 커버)")
    void list_WithExecutiveTask_ShouldCoverOrBranch() throws Exception {
        EvaluationPeriodDTO p1 = EvaluationPeriodDTO.builder().periodId(1L).statusCode("ACTIVE").build();
        given(periodService.getAllPeriods()).willReturn(List.of(p1));
        
        EvaluatorMappingDTO task1 = EvaluatorMappingDTO.builder().mappingId(101L).relationTypeCode("MANAGER").build();
        EvaluatorMappingDTO task2 = EvaluatorMappingDTO.builder().mappingId(102L).relationTypeCode("EXECUTIVE").build();
        given(mappingService.getMyEvaluationTasks(anyLong(), anyLong())).willReturn(List.of(task1, task2));

        mockMvc.perform(get("/eval/performance")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tasks", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    @DisplayName("입력폼 - 삭제된 매핑은 SELF 매핑 필터링에서 제외되어야 한다 (Gate 199 && 브랜치 커버)")
    void getForm_WithDeletedMapping_ShouldFilterOut() throws Exception {
        
        EvaluatorMapping m1 = new EvaluatorMapping();
        m1.setMappingId(100L);
        m1.setRelationTypeCode("SELF");
        m1.setIsDeleted("y"); // 삭제됨
        
        EvaluatorMapping m2 = new EvaluatorMapping();
        m2.setMappingId(101L);
        m2.setRelationTypeCode("SELF");
        m2.setIsDeleted("n"); // 정상
        
        EvaluatorMappingDTO dto = EvaluatorMappingDTO.builder()
                .mappingId(999L).periodId(1L).evaluateeId(1001L).relationTypeCode("MANAGER").build();
        given(mappingService.getMappingById(anyLong())).willReturn(dto);
        given(elementService.getElementsByPeriodId(anyLong(), any())).willReturn(Collections.emptyList());

        given(evaluatorMappingMapper.findByEvaluateeId(anyLong(), anyLong())).willReturn(List.of(m1, m2));

        mockMvc.perform(get("/eval/performance/form")
                .param("mappingId", "999")
                .param("evalType", "PERFORMANCE")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("selfEvalMap"));
        
        // 999L(메인 과업)과 101L(자가평가 참고) 각각 한 번씩 호출됨을 확인
        org.mockito.Mockito.verify(evaluationMapper, org.mockito.Mockito.times(1)).findByMappingId(999L);
        org.mockito.Mockito.verify(evaluationMapper, org.mockito.Mockito.times(1)).findByMappingId(101L);
        org.mockito.Mockito.verify(evaluationMapper, org.mockito.Mockito.never()).findByMappingId(100L);
    }

    @Test
    @DisplayName("목록 - ACTIVE 상태인 기간이 있으면 우선 선택되어야 한다 (Gate 55 findFirst 브랜치 커버)")
    void list_WithActivePeriod_ShouldCoverFindFirst() throws Exception {
        EvaluationPeriodDTO p1 = EvaluationPeriodDTO.builder().periodId(1L).statusCode("INACTIVE").build();
        EvaluationPeriodDTO p2 = EvaluationPeriodDTO.builder().periodId(2L).statusCode("ACTIVE").build();
        
        given(periodService.getAllPeriods()).willReturn(List.of(p1, p2));

        mockMvc.perform(get("/eval/performance")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedPeriod", p2));
    }

    @Test
    @DisplayName("목록 - 제출된 평가 요소가 없으면 submitted 플래그가 false여야 한다 (Gate 87 브랜치 커버)")
    void list_WithNoSubmittedElements_ShouldCoverGateS() throws Exception {
        EvaluationPeriodDTO p1 = EvaluationPeriodDTO.builder().periodId(1L).statusCode("ACTIVE").build();
        given(periodService.getAllPeriods()).willReturn(List.of(p1));
        
        EvaluatorMappingDTO selfTask = EvaluatorMappingDTO.builder().mappingId(100L).relationTypeCode("SELF").build();
        given(mappingService.getMyEvaluationTasks(anyLong(), anyLong())).willReturn(List.of(selfTask));
        
        // 제출된 항목이 없는 경우 (confirmStatusCode가 다른 값인 경우)
        Evaluation e1 = new Evaluation();
        e1.setConfirmStatusCode("SAVED");
        given(evaluationMapper.findByMappingId(100L)).willReturn(List.of(e1));

        mockMvc.perform(get("/eval/performance")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selfPerfSubmitted", false));
    }

    @Test
    @DisplayName("목록 - 피평가자의 SELF 매핑이 없으면 evaluateeSelfSubmitted가 false여야 한다 (Gate 137 orElse 브랜치 커버)")
    void list_WithNoEvaluateeSelfMapping_ShouldCoverGateT() throws Exception {
        EvaluationPeriodDTO p1 = EvaluationPeriodDTO.builder().periodId(1L).statusCode("ACTIVE").build();
        given(periodService.getAllPeriods()).willReturn(List.of(p1));
        
        EvaluatorMappingDTO teamTask = EvaluatorMappingDTO.builder().mappingId(101L).evaluateeId(2001L).relationTypeCode("MANAGER").build();
        given(mappingService.getMyEvaluationTasks(anyLong(), anyLong())).willReturn(List.of(teamTask));
        given(elementService.getElementsByPeriodId(anyLong(), any())).willReturn(Collections.emptyList());

        // 피평가자의 매핑이 아예 없는 경우
        given(evaluatorMappingMapper.findByEvaluateeId(anyLong(), anyLong())).willReturn(Collections.emptyList());

        mockMvc.perform(get("/eval/performance")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("evaluateeSelfSubmittedMap", hasEntry(101L, false)));
    }

    @Test
    @DisplayName("목록 - 역량 평가이면서 요소가 없으면 selfSubmitted가 false여야 한다")
    void list_WithCompetencyAndNoElements() throws Exception {
        EvaluationPeriodDTO p1 = EvaluationPeriodDTO.builder().periodId(1L).statusCode("ACTIVE").build();
        given(periodService.getAllPeriods()).willReturn(List.of(p1));
        given(elementService.getElementsByPeriodId(anyLong(), any())).willReturn(Collections.emptyList());

        mockMvc.perform(get("/eval/performance")
                .param("evalType", "COMPETENCY")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selfCompSubmitted", false));
    }

    @Test
    @DisplayName("입력폼 - 역량 평가 폼 요청을 처리해야 한다")
    void getForm_WithCompetency() throws Exception {
        EvaluatorMappingDTO dto = EvaluatorMappingDTO.builder()
                .mappingId(101L).periodId(1L).evaluateeId(1001L).relationTypeCode("SELF").build();
        given(mappingService.getMappingById(anyLong())).willReturn(dto);
        given(elementService.getElementsByPeriodId(anyLong(), any())).willReturn(Collections.emptyList());

        mockMvc.perform(get("/eval/performance/form")
                .param("mappingId", "101")
                .param("evalType", "COMPETENCY")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("evalType", "COMPETENCY"));
    }

    @Test
    @DisplayName("입력폼 - SELF/MANAGER가 아닌 관계(PEER)인 경우 selfEvalMap이 없어야 한다")
    void getForm_WithPeerRelation() throws Exception {
        EvaluatorMappingDTO dto = EvaluatorMappingDTO.builder()
                .mappingId(101L).periodId(1L).evaluateeId(1001L).relationTypeCode("PEER").build();
        given(mappingService.getMappingById(anyLong())).willReturn(dto);
        given(elementService.getElementsByPeriodId(anyLong(), any())).willReturn(Collections.emptyList());

        mockMvc.perform(get("/eval/performance/form")
                .param("mappingId", "101")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("selfEvalMap"));
    }

    @Test
    @DisplayName("목록 - SELF 매핑은 있지만 평가 데이터가 없으면 evaluateeSelfSubmitted가 false여야 한다")
    void list_WithSelfMappingButNoEvaluations() throws Exception {
        EvaluationPeriodDTO p1 = EvaluationPeriodDTO.builder().periodId(1L).statusCode("ACTIVE").build();
        given(periodService.getAllPeriods()).willReturn(List.of(p1));
        
        EvaluatorMappingDTO teamTask = EvaluatorMappingDTO.builder().mappingId(101L).evaluateeId(2001L).relationTypeCode("MANAGER").build();
        given(mappingService.getMyEvaluationTasks(anyLong(), anyLong())).willReturn(List.of(teamTask));

        EvaluatorMapping m = new EvaluatorMapping();
        m.setMappingId(202L);
        m.setRelationTypeCode("SELF");
        m.setIsDeleted("n");
        given(evaluatorMappingMapper.findByEvaluateeId(anyLong(), anyLong())).willReturn(List.of(m));
        given(evaluationMapper.findByMappingId(anyLong())).willReturn(Collections.emptyList());

        mockMvc.perform(get("/eval/performance")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("1001")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("evaluateeSelfSubmittedMap", hasEntry(101L, false)));
    }
}
