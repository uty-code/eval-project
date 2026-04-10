package com.ees.eval.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 매퍼(Mapper) 인터페이스 스캐닝 및 데이터베이스 연동 설정을 담당하는 클래스입니다.
 * @MapperScan을 통해 지정된 패키지 내의 인터페이스를 영속성 계층으로 등록합니다.
 */
@Configuration
@MapperScan("com.ees.eval.mapper")
public class MyBatisConfig {
    // MyBatis 관련 세부 기술 설정이 필요할 경우 여기에 추가 정의합니다.
}
