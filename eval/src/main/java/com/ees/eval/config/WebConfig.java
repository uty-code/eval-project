package com.ees.eval.config;

import com.ees.eval.interceptor.PasswordChangeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // application.yml에서 add-mappings: false로 설정했으므로 수동으로 정적 리소스 경로를 매핑합니다.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/", "classpath:/resources/", "classpath:/META-INF/resources/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 뒤로가기 캐시(bfcache) 방지 인터셉터: 인증이 필요한 서비스 영역(/eval/**) 및 설정 영역에 적용
        registry.addInterceptor(new com.ees.eval.interceptor.CacheControlInterceptor())
                .addPathPatterns("/eval/**", "/settings/**", "/dashboard/**")
                .excludePathPatterns(
                        "/css/**", "/js/**", "/images/**", "/favicon.ico", "/webjars/**"
                );

        registry.addInterceptor(new PasswordChangeInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login", "/logout", "/error/**",
                        "/css/**", "/js/**", "/img/**", "/lib/**",
                        "/favicon.ico",
                        "/settings/profile/**",
                        "/register/**"
                );
    }
}
