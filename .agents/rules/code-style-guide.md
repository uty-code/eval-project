---
trigger: always_on
---

# 프로젝트 규칙: 사원 평가 시스템 (EES)

## 1. 아키텍처 및 기술 스택
- [cite_start]**구조**: Controller - Service - ServiceImpl - Mapper (MyBatis) 계층 구조를 엄격히 준수한다.
- [cite_start]**기술**: Java 21, Spring Boot 4.x, MyBatis, MSSQL, Spring Security, Thymeleaf.
- [cite_start]**인프라**: Docker (MSSQL 컨테이너), Jenkins (CI/CD).
- **언어**: Java 21 (LTS) 기능을 적극 활용한다.
- **가상 스레드**: 높은 동시성 처리를 위해 Spring Boot의 Virtual Threads 설정을 활성화한다.
- **문법**: 
  - 데이터 전달 객체(DTO)는 가급적 **Record** 타입을 사용한다.
  - Pattern Matching for switch 및 Sequenced Collections 기능을 활용하여 코드 가독성을 높인다
## 2. SQL 및 데이터베이스 규칙
- **대소문자 규정**: 모든 SQL 쿼리는 **소문자**로 작성한다. [cite_start]단, **Alias(별칭)만 대문자**로 작성한다.
  - [cite_start]예시: `select name as NAME from employees` 
- **소프트 델리트**: 실제 삭제 대신 `is_deleted` 컬럼(default 'n')을 사용하여 논리적 삭제를 수행한다.
- **Audit 컬럼**: 모든 테이블에 `created_at`, `created_by`, `updated_at`, `updated_by` 컬럼을 포함하고 자동으로 값이 입력되도록 한다.
- **버전 관리**: 낙관적 락을 위해 중요 테이블에 `version` (int) 컬럼을 포함한다.

## 3. 로직 및 보안 가이드라인
- **트랜잭션**: 모든 CUD(생성, 수정, 삭제) 작업이 포함된 Service 메소드에는 `@Transactional` 어노테이션을 필수 적용한다.
- **동시성 제어**: 
  - 다면 평가 점수 합산 로직: 행 단위 비관적 락(`with (updlock)`)을 적용한다.
  - 최종 등급 확정: 해당 그룹 전체에 비관적 락을 걸어 계산 중 데이터 수정을 방지한다.
- [cite_start]**보안**: 비밀번호는 `BCryptPasswordEncoder`로 암호화하며 [cite: 5][cite_start], API 및 메소드 접근 제어는 `@PreAuthorize`를 활용한다.

## 4. UI/UX 규칙
- [cite_start]**공통 레이아웃**: `thymeleaf-layout-dialect`를 활용하여 헤더, 푸터, 사이드바를 공통화한다.
- [cite_start]**프레임워크**: Bootstrap 5와 시각화를 위한 Chart.js를 사용한다.

## 5. 주석 및 문서화 규칙 (Essential)
- **언어**: 모든 주석은 **한국어**로 작성한다.
- **클래스/메소드**: 모든 주요 클래스와 메소드 상단에는 JavaDoc 형식(`/** ... */`)의 주석을 작성한다.
  - 메소드 주석에는 반드시 `@param`, `@return`, `@throws`를 포함하여 설명을 단다.
- **로직 설명**: 비즈니스 로직이 복잡한 `ServiceImpl`의 경우, 코드 블록 사이사이에 작업 단계를 설명하는 한 줄 주석(`//`)을 추가한다.
- **MyBatis**: XML Mapper 파일에서도 각 쿼리(`select`, `insert` 등)가 어떤 기능을 수행하는지 상단에 XML 주석(``)을 작성한다.
## 6. 프론트엔드 개발 워크플로우
UI 구조 설계: 화면 설계서를 바탕으로 공통 레이아웃과 분리할 조각(Fragment)을 정의한다.

데이터 명세 확정: HTML 작성 전, Controller가 Model에 담아 보낼 Record 구조와 변수명을 먼저 확정한다.

Mock 데이터 검증: 백엔드 로직 완성 전이라도, 더미 데이터를 넘겨 타임리프 화면의 데이터 출력 여부를 우선 확인한다.

인터랙션 구현: 구조 완성 후, 필요한 경우에만 JavaScript를 추가하여 동적 효과를 구현한다.