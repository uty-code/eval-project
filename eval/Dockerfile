# Build Stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Dependency 캐시를 위해 pom.xml 먼저 복사
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 소스 코드 복사 및 빌드
COPY src ./src
# --enable-preview 설정이 pom.xml에 있으므로 일반적인 package 명령 수행
RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

# 빌드 스테이지에서 생성된 jar 파일 복사
# artifactId=eval, version=0.0.1-SNAPSHOT 이므로 eval-0.0.1-SNAPSHOT.jar
COPY --from=build /app/target/eval-0.0.1-SNAPSHOT.jar app.jar

# Spring Boot 포트 설정 (기본 8080)
EXPOSE 8080

# Java 21 Preview 기능 활성화 및 애플리케이션 실행
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
