package com.ees.eval.service;

import com.ees.eval.domain.LoginLog;
import com.ees.eval.mapper.LoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 로그인 이력(Audit Log) 저장을 담당하는 서비스입니다.
 *
 * ⚠️ 주의: HttpServletRequest는 Tomcat이 응답 완료 후 재활용(recycle)합니다.
 *   @Async 메서드에 그대로 넘기면 비동기 스레드에서 헤더가 비어있을 수 있습니다.
 *   따라서 공개 API에서는 호출 시점에 IP/UA를 동기적으로 추출하고,
 *   비동기 저장 메서드에는 String으로만 넣습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final LoginLogMapper loginLogMapper;

    /**
     * 로그인 성공 이력을 기록합니다.
     * IP/UA를 현재 스레드(동기 영역)에서 먼저 추출한 뒤 비동기 저장합니다.
     *
     * @param empId   로그인한 사원 ID
     * @param request HTTP 요청 객체
     */
    public void recordSuccess(Long empId, HttpServletRequest request) {
        String ip = extractIp(request);
        String ua = truncate(request.getHeader("User-Agent"), 500);
        saveAsync(empId, String.valueOf(empId), "SUCCESS", ip, ua);
    }

    /**
     * 로그인 실패 이력을 기록합니다.
     * IP/UA를 현재 스레드(동기 영역)에서 먼저 추출한 뒤 비동기 저장합니다.
     *
     * @param loginInput  입력된 사번 원문
     * @param resultCode  실패 원인 코드 (FAIL_INVALID, FAIL_LOCKED, FAIL_RETIRED, FAIL_PENDING)
     * @param request     HTTP 요청 객체
     */
    public void recordFailure(String loginInput, String resultCode, HttpServletRequest request) {
        String ip = extractIp(request);
        String ua = truncate(request.getHeader("User-Agent"), 500);
        Long empId = null;
        try {
            empId = Long.parseLong(loginInput);
        } catch (NumberFormatException ignored) {
            // 숫자가 아닌 입력은 empId를 null로 유지
        }
        saveAsync(empId, loginInput, resultCode, ip, ua);
    }

    /**
     * 전체 로그인 이력을 조회합니다. (관리자용)
     */
    public List<LoginLog> findAll() {
        return loginLogMapper.findAll();
    }

    /**
     * 특정 사원의 로그인 이력을 조회합니다.
     */
    public List<LoginLog> findByEmpId(Long empId) {
        return loginLogMapper.findByEmpId(empId);
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    /**
     * 실제 DB 저장을 담당하는 비동기 메서드입니다.
     * HttpServletRequest 없이 순수 String 값만 받으므로 Tomcat request recycling에 안전합니다.
     */
    @Async("virtualThreadExecutor")
    protected void saveAsync(Long empId, String loginInput, String resultCode, String ipAddress, String userAgent) {
        try {
            LoginLog loginLog = LoginLog.builder()
                    .empId(empId)
                    .loginInput(loginInput)
                    .resultCode(resultCode)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();
            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.error("[LoginLog] 이력 저장 실패: empId={}, resultCode={}", empId, resultCode, e);
        }
    }

    /**
     * 프록시/로드밸런서 환경에서도 실제 클라이언트 IP를 추출합니다.
     * ※ 반드시 동기 영역(요청 스레드)에서 호출되어야 합니다.
     */
    private String extractIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 는 "실제IP, 프록시IP, ..." 형태일 수 있음
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}
