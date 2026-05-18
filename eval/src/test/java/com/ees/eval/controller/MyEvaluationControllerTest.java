package com.ees.eval.controller;

import com.ees.eval.service.MyEvaluationFacadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MyEvaluationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MyEvaluationFacadeService myEvaluationFacadeService;

    @InjectMocks
    private MyEvaluationController myEvaluationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(myEvaluationController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        // SecurityContext 설정 (UserDetails.getUsername()이 "1001"을 반환하도록 설정)
        UserDetails userDetails = new User("1001", "password", Collections.emptyList());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("평가 차수가 PLANNED 상태일 때 자가평가 폼(마법사) 진입 차단 테스트")
    void blockAccessWhenPeriodIsPlanned() throws Exception {
        // Given
        Long mappingId = 1L;
        Long empId = 1001L;

        given(myEvaluationFacadeService.getWizardData(eq(mappingId), eq(empId)))
                .willThrow(new IllegalArgumentException("평가 시작 전입니다. 평가 기간에 다시 접속해 주세요."));

        // When & Then
        mockMvc.perform(get("/eval/my-evaluation/wizard")
                        .param("mappingId", mappingId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/eval/my-evaluation"))
                .andExpect(flash().attribute("errorMessage", "평가 시작 전입니다. 평가 기간에 다시 접속해 주세요."));
    }

    @Test
    @DisplayName("평가 차수가 PLANNED 상태일 때 목록 페이지 정상 렌더링 테스트")
    void showInfoMessageWhenPeriodIsPlanned() throws Exception {
        // Given
        Long periodId = 10L;
        Long empId = 1001L;
        
        Map<String, Object> mockDashboardData = new HashMap<>();
        mockDashboardData.put("periods", Collections.emptyList());
        mockDashboardData.put("selectedPeriodId", periodId);

        given(myEvaluationFacadeService.getDashboardData(eq(empId), eq(periodId), any(), any(), anyInt(), anyInt()))
                .willReturn(mockDashboardData);

        // When & Then
        mockMvc.perform(get("/eval/my-evaluation")
                        .param("periodId", periodId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("eval/my-evaluation/list"))
                .andExpect(model().attribute("selectedPeriodId", periodId));
    }
}
