프로젝트 가이드라인: 사원 평가 시스템 
1. 프로젝트 개요 (Overview)
프로젝트명: 통합 사원 평가 시스템 (Employee Evaluation System)


목적: 조직 관리, 평가 기준 설정, 다면 평가, 최종 등급 산출 및 시각화


핵심 가치: 프로세스 투명성, 데이터 무결성 보장, 변경 이력 추적 가능성


2. 기술 스택 (Tech Stack)
Backend: Java 21, Spring Boot 4.x


Security: Spring Security (BCryptPasswordEncoder, @PreAuthorize)


Persistence: MyBatis (XML 기반), MSSQL


Frontend: Thymeleaf (layout-dialect), Bootstrap 5, Chart.js


Environment: Docker (MSSQL Server), Git (코드 관리), Deployment (선택 사항)


3. 협업 및 미팅 일정 (Collaboration)
- 클라우드/백엔드팀: 매주 월/목 09:00 ~ 10:00
- AI/빅데이터팀: 매주 월/목 10:00 ~ 11:00


4. 개발 및 SQL 작성 규칙 (Critical Rules)
AI 수행 지침: 아래 규칙을 위반하는 코드를 생성하지 마시오.
SQL 작성 규정: 모든 쿼리는 소문자로 작성하되, 별칭(Alias)만 대문자로 작성한다.


예: select name as NAME from employees


아키텍처 구조: Controller - Service - ServiceImpl - Mapper (MyBatis) 구조를 엄격히 준수한다.


데이터 삭제: 실제 삭제(Hard Delete) 대신 is_deleted 컬럼을 사용하는 **소프트 델리트(Soft Delete)**를 적용한다.
이력 관리 (Audit): 모든 테이블에 created_at, created_by, updated_at, updated_by 컬럼을 포함한다.
트랜잭션: 모든 CUD 작업은 @Transactional 어노테이션을 통해 원자성을 보장한다.
4. 상세 구현 로직 및 DB 설계 전략
[업무 흐름도 요약]
- 부서원: 본인 평가 ⇒ 부서장 평가 ⇒ 평가 확정 (최종 평가자 확정)
- 부서장: 본인 평가 ⇒ 다면 평가 ⇒ 평가 확정 (최종 평가자 확정)


[관리자용 페이지 - 4종]
부서 관리: 조직 구조(생성, 수정, 삭제) 관리.


Note: 부서 이동 시 연쇄 업데이트를 위한 트랜잭션 처리.
사원 관리: 사원 등록 및 권한 부여.


Note: 사원 정보와 Security 권한 매핑을 하나의 트랜잭션으로 처리.
평가자 생성: 평가 대상자와 평가자 매핑 설정.


Note: 대량 Delete-Insert 작업 시 트랜잭션 적용 및 성능을 위한 인덱스 설계.
평가 요소 관리: 성과/역량 평가 항목 및 배점 기준 설정.


Locking: 관리자 간 충돌 방지를 위한 낙관적 락(Version 컬럼) 적용.
[평가 및 결과 페이지 - 6종]
성과 평가: 목표 달성도 기반 점수 입력.


Audit: 점수 수정 시 evaluation_history 테이블에 변경 이력 기록.
역량 평가: 보유 기술 및 행동 지표 평가.


Validation: 배점 기준 내 점수 입력 여부 서버 사이드 검증.
다면 평가: 동료 및 상하급자 간 상호 평가.


Locking: 다수 평가자의 동시 점수 합산을 위해 비관적 락(Row Lock) 적용.
면담 평가: 평가자와 피평가자 간 면담 기록 및 의견 반영.


Transaction: 기록 저장과 상태(Status) 변경을 원자적으로 수행.
최종 등급 확정: 점수 합산 및 등급(S~D) 산출.


Locking: 산출 로직 시작 시 해당 그룹 전체에 비관적 락을 걸어 데이터 수정 차단.
결과 시각화: 부서별 분포도 및 개인별 점수 대시보드.


Performance: @Transactional(readOnly = true) 적용 및 대량 조회 인덱스 최적화.
5. 실무 운영 고도화 포인트 (추가 설계 필수)
평가 차수 관리 (Periods): 모든 평가는 연도 및 차수(period_id)를 기준으로 관리하며, 이전 차수 데이터를 덮어쓰지 않도록 설계한다.
증빙 자료 관리 (Evidence): 평가 증빙을 위한 파일 업로드 기능 및 메타데이터 저장 테이블을 구축한다.
배치 처리 (Batch): 전 사원 등급 일괄 확정 등 헤비한 로직은 비동기 처리 또는 부서별 분할 처리를 고려한다.
예외 처리 (Global Exception): @RestControllerAdvice를 통해 락 충돌 및 유효성 검사 실패 시 명확한 에러 메시지를 반환한다.
6. 개발 로드맵
- 1주차 (2026.04.13 ~ 2026.04.17): 요구사항 분석 및 ERD 설계 (락/이력/차수 관리 컬럼 반영).


- 2~7주차 (2026.04.20 ~ 2026.05.29): 10개 주요 기능 구현 및 MVC 패턴 적용.


- 8~9주차 (2026.06.01 ~ 2026.06.12): 통합 테스트, 동시성 테스트(락 검증), 배포 및 고도화.
