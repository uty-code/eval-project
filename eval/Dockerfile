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

# [보안/운영] curl 설치 (Actuator Healthcheck 감사용)
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# 빌드 스테이지에서 생성된 jar 파일 복사
COPY --from=build /app/target/eval-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# [운영 고도화] Docker Engine 수준의 정밀 헬스체크 가동 (healthy/unhealthy 실시간 마킹)
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Java 21 Preview 기능 활성화 및 애플리케이션 실행
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
