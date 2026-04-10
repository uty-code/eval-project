package com.ees.eval.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 로그인 시스템의 화면 매핑을 담당하는 컨트롤러입니다.
 * 보안 설정을 통해 지정된 커스텀 로그인 경로를 제공합니다.
 */
@Controller
public class LoginController {

    /**
     * 커스텀 로그인 페이지를 반환합니다.
     *
     * @return login.html 템플릿 경로
     */
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }
}
