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

# [운영 고도화] Docker Engine 수준의 정밀 헬스체크 가동 및 액츄에이터 우회 경로(/internal-monitor/health) 완벽 일치화
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/internal-monitor/health || exit 1

# [운영 고도화] sh -c를 활용하여 Docker Compose의 JAVA_OPTS 힙 제한 환경변수가 정상 유입 및 인지되도록 보장
ENTRYPOINT ["sh", "-c", "java --enable-preview $JAVA_OPTS -jar app.jar"]
