<div align="center">
  
# 사원 평가 시스템 (EES)
**대규모 트래픽 환경의 동시성 제어와 CI/CD 무중단 배포를 적용한 엔터프라이즈 백엔드 프로젝트**

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![MSSQL](https://img.shields.io/badge/MSSQL-CC292B?style=for-the-badge&logo=microsoft-sql-server&logoColor=white)
![Jenkins](https://img.shields.io/badge/Jenkins-D24939?style=for-the-badge&logo=jenkins&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Azure](https://img.shields.io/badge/Azure-0089D6?style=for-the-badge&logo=microsoft-azure&logoColor=white)

<br>

[**운영 서버 접속 (ees-eval.com)**](https://ees-eval.com/) &nbsp; | &nbsp; [**WBS (일정 관리)**](https://docs.google.com/spreadsheets/d/1ZLVCVKlmdRchz_vIjrUE8szmNvLjTK5t4vgLfzbg3JQ/edit?usp=sharing)

<br>
</div>
<br>
관리자 계정 ID: 1000 PW: admin123
<br>
사원 계정 ID:1001~1041 PW: 1234
<br>
---

## 프로젝트 개요
사원 평가 과정에서 발생하는 **동시성 이슈와 쿼리 지연을 개선**한 백엔드 개발 프로젝트입니다. 평상시에는 트래픽이 적지만, **평가 마감일 직전에 전사 직원이 동시에 몰려 평가서를 제출하는 특성**을 고려하여 아키텍처를 설계했습니다.

## 주요 기능 

### 1. 인사 관리자 전용 프로세스 제어
- **평가 차수 라이프사이클 관리**: 매년 진행되는 정기/수시 평가를 '계획(PLAN) -> 진행(IN_PROGRESS) -> 마감(CLOSED)' 상태로 통제하여 프로세스의 안정성을 보장합니다.
- **다단계 다면 평가 자동 매핑**: 개별 사원의 부서 및 직책 계층 구조를 시스템이 분석하여, 본인 평가, 1차 평가(팀장), 2차 평가(본부장/임원) 관계를 대량으로 자동 생성합니다.
- **실시간 통계 및 등급 산정 대시보드**: 전사 직원들의 평가 참여율을 실시간으로 추적하며, 직책별 가중치를 합산하여 최종 상대평가 등급(S, A, B, C, D)을 산출하고 확정합니다.

### 2. 일반 임직원 다면 평가 인터페이스
- **직관적인 평가 대상자 관리**: 본인이 작성해야 할 피평가자 목록을 한눈에 확인하고, 정량적 점수 및 서술형 피드백을 안전하게 임시저장하거나 최종 제출할 수 있습니다.
- **마감 직전 대규모 트래픽 방어**: 평가 마감일 직전에 전 직원이 동시에 접속하여 점수를 제출하더라도, 데이터 덮어쓰기나 유실이 발생하지 않도록 견고한 동시성 제어 구조로 보호됩니다.
- **투명한 결과 및 피드백 조회**: 인사팀에서 최종 확정한 본인의 평가 등급과 피드백을 투명하게 열람하여 인사 고과의 신뢰성을 높입니다.

---

## 시스템 아키텍처

### 1. 무중단 배포 및 CI/CD 인프라 아키텍처
```mermaid
flowchart TD
    A[GitHub Repository] -->|Webhook Trigger| B[Jenkins CI/CD <br> Azure VM]
    
    B -->|1. Docker Build & Push| C[(Docker Hub Registry)]
    B -->|2. SSH Remote Deploy| D[KT Cloud VM <br> Docker Compose]
    
    C -->|3. Docker Image Pull| D
    
    D -->|4. Container Run| E[Nginx Container <br> Port 80/443]
    E -->|5. Reverse Proxy| F[Spring Boot Container <br> Port 8080]
    F -->|6. JDBC Connection| G[(Database: MSSQL)]
    
    D -.->|배포 검증 실패 시| H[rollback.sh 자동 롤백]
    D -.->|시스템 자원 임계치 초과 시| I[Discord 실시간 경보]
```

### 2. 백엔드 논리적 레이어
```mermaid
graph LR
    A[Controller Layer] -->|DTO| B[Service Layer]
    B -->|트랜잭션/동시성 제어| C[Mapper Layer]
    C -->|최적화된 SQL Query| D[(MSSQL 2022)]
```

---

## 인프라 자동화 및 DevOps

### 장애 없는 CI/CD 파이프라인 
- **정밀 헬스체크**: Nginx 리다이렉트(301/302) 및 Spring Boot Actuator 내부 상태(`"status":"UP"`)를 교차 검증합니다.
- **자동 롤백**: 배포 실패 또는 헬스체크 응답 타임아웃 발생 시, 스크립트(`rollback.sh`)가 이전 안정 버전 컨테이너로 즉각 원상 복구하여 가용성을 보장합니다.

### 실시간 관측성 및 알람 생략 로직
- 서버 CPU, 메모리 자원 고갈 시 Discord Webhook을 통해 즉각적인 장애 경보를 발송하는 크론 데몬(`ees_monitor.sh`)을 자체 구축했습니다.
- 배포가 진행 중인 짧은(10~20초) 다운타임 구간에는 서버 장애로 오탐지하지 않도록 `.deploying` 플래그를 활용한 Mute 로직을 구현했습니다.

---
> *ERD 등 상세한 데이터베이스 DDL 스크립트는 최상단 `ERD.sql` 파일을 참고해 주세요.*
