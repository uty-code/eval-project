package com.ees.eval.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 브라우저의 뒤로가기 캐시(bfcache)를 방지하기 위한 인터셉터입니다.
 * 로그아웃 후 뒤로가기 시 이전 사용자의 정보가 노출되는 것을 방지합니다.
 */
@Component
public class CacheControlInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // HTTP 1.1.
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        // HTTP 1.0.
        response.setHeader("Pragma", "no-cache");
        // Proxies.
        response.setDateHeader("Expires", 0);
        
        return true;
    }
}
