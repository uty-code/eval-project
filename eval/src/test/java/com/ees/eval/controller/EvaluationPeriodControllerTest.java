package com.ees.eval.controller;

import com.ees.eval.dto.EvaluationPeriodDTO;
import com.ees.eval.service.EvaluationPeriodService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * EvaluationPeriodController의 Standalone 테스트 클래스입니다.
 * 평가 차수의 생명주기(생성, 수정, 상태 전환, 삭제) 및 유효성 검사를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationPeriodControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EvaluationPeriodService periodService;

    @InjectMocks
    private EvaluationPeriodController periodController;

    @BeforeEach
    void setUp() {
        // 독립 실행형 설정으로 MockMvc 초기화
        mockMvc = MockMvcBuilders.standaloneSetup(periodController).build();
    }

    @Test
    @DisplayName("차수 목록 조회 - 정상 호출 시 목록 뷰와 데이터를 반환한다")
    void listPeriods_ShouldReturnListView() throws Exception {
        // given
        List<EvaluationPeriodDTO> mockPeriods = List.of(
                EvaluationPeriodDTO.builder().periodId(1L).periodName("2024 상반기").build()
        );
        given(periodService.getAllPeriods()).willReturn(mockPeriods);

        // when & then
        mockMvc.perform(get("/eval/periods"))
                .andExpect(status().isOk())
                .andExpect(view().name("eval/periods/list"))
                .andExpect(model().attribute("periods", mockPeriods));
    }

    @Test
    @DisplayName("신규 차수 저장 실패 - 종료일이 시작일보다 빠른 경우 에러 메시지를 반환한다")
    void createPeriod_Fail_InvalidDates_ShouldShowError() throws Exception {
        // when & then
        mockMvc.perform(post("/eval/periods")
                        .param("periodYear", "2024")
                        .param("periodName", "검증실패차수")
                        .param("startDate", "2024-12-31")
                        .param("endDate", "2024-01-01")) // 종료일이 앞섬
                .andExpect(status().isOk())
                .andExpect(view().name("eval/periods/form"))
                .andExpect(model().attribute("errorMessage", "종료일은 시작일보다 이후여야 합니다."));
    }

    @Test
    @DisplayName("차수 정보 수정 실패 - 종료일이 시작일보다 빠른 경우 에러 메시지를 반환한다")
    void updatePeriod_Fail_InvalidDates_ShouldShowError() throws Exception {
        // when & then
        mockMvc.perform(post("/eval/periods/1")
                        .param("periodYear", "2024")
                        .param("periodName", "수정실패차수")
                        .param("startDate", "2024-12-31")
                        .param("endDate", "2024-01-01")
                        .param("version", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("eval/periods/form"))
                .andExpect(model().attribute("errorMessage", "종료일은 시작일보다 이후여야 합니다."))
                .andExpect(model().attribute("isNew", false));
    }

    @Test
    @DisplayName("차수 정보 수정 성공 - 유효한 데이터 입력 시 목록으로 리다이렉트된다")
    void updatePeriod_Success_ShouldRedirect() throws Exception {
        // when & then
        mockMvc.perform(post("/eval/periods/1")
                        .param("periodYear", "2024")
                        .param("periodName", "수정된차수")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-06-30")
                        .param("version", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/eval/periods"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(periodService).updatePeriod(any(EvaluationPeriodDTO.class));
    }

    @Test
    @DisplayName("상태 전이(진행중) - 호출 시 한글 상태명을 포함한 성공 메시지를 반환한다")
    void transitionStatus_ToInProgress_ShouldShowSuccessMessage() throws Exception {
        // given
        EvaluationPeriodDTO updatedDto = EvaluationPeriodDTO.builder()
                .periodId(1L)
                .periodName("2024 정기")
                .statusCode("IN_PROGRESS")
                .build();
        given(periodService.transitionStatus(anyLong(), anyString())).willReturn(updatedDto);

        // when & then
        mockMvc.perform(post("/eval/periods/1/transition")
                        .param("newStatus", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/eval/periods"))
                .andExpect(flash().attribute("successMessage", "'2024 정기' 차수 상태가 '진행 중'(으)로 변경되었습니다."));
    }

    @Test
    @DisplayName("상태 전이(완료) - 호출 시 정해진 한글 상태명을 반환한다")
    void transitionStatus_ToCompleted_ShouldShowSuccessMessage() throws Exception {
        // given
        EvaluationPeriodDTO updatedDto = EvaluationPeriodDTO.builder()
                .periodId(1L)
                .periodName("2024 정기")
                .statusCode("COMPLETED")
                .build();
        given(periodService.transitionStatus(anyLong(), anyString())).willReturn(updatedDto);

        // when & then
        mockMvc.perform(post("/eval/periods/1/transition")
                        .param("newStatus", "COMPLETED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", "'2024 정기' 차수 상태가 '완료'(으)로 변경되었습니다."));
    }

    @Test
    @DisplayName("상태 전이(기타) - 정의되지 않은 상태 코드 유입 시 코드값을 그대로 반환한다")
    void transitionStatus_OtherStatus_ShouldReturnCodeAsIs() throws Exception {
        // given
        EvaluationPeriodDTO updatedDto = EvaluationPeriodDTO.builder()
                .periodId(1L)
                .periodName("2024 정기")
                .statusCode("UNKNOWN_STATUS")
                .build();
        given(periodService.transitionStatus(anyLong(), anyString())).willReturn(updatedDto);

        // when & then
        mockMvc.perform(post("/eval/periods/1/transition")
                        .param("newStatus", "UNKNOWN_STATUS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", "'2024 정기' 차수 상태가 'UNKNOWN_STATUS'(으)로 변경되었습니다."));
    }

    @Test
    @DisplayName("차수 삭제 - 정상 호출 시 성공 메시지와 함께 리다이렉트된다")
    void deletePeriod_ShouldRedirectWithSuccessMessage() throws Exception {
        // when & then
        mockMvc.perform(post("/eval/periods/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/eval/periods"))
                .andExpect(flash().attribute("successMessage", "차수가 삭제되었습니다."));

        verify(periodService).deletePeriod(1L);
    }
}
