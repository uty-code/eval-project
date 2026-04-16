package com.ees.eval.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 비동기 작업에 사용할 Executor 설정 클래스입니다.
 * Java 21의 Virtual Thread를 활용하여 I/O 바운드 작업(DB 조회 등)의 병렬 처리를 최적화합니다.
 */
@Configuration
public class AsyncConfig {

    /**
     * Virtual Thread 기반 Executor 빈을 생성합니다.
     * 작업마다 새로운 가상 스레드를 생성하며, 가상 스레드는 블로킹 I/O에서도
     * 캐리어 스레드를 점유하지 않아 높은 동시성을 제공합니다.
     *
     * @return Virtual Thread per task Executor
     */
    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        // Java 21: newVirtualThreadPerTaskExecutor - 작업마다 가상 스레드 생성
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
