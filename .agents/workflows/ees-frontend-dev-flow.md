---
description: 사원 평가 시스템(EES)의 프론트엔드 개발 표준 워크플로우를 정의합니다.
---

Step 1: 레이아웃 구조 분석 및 파편화 (UI Structure & Fragments)
- **공통 레이아웃**: `thymeleaf-layout-dialect`를 활용하여 헤더, 푸터, 사이드바를 포함한 기본 틀(`layout.html`)을 정의하거나 기존 레이아웃과의 정합성을 확인합니다.
- **프래그먼트 정의**: 페이지 내에서 재사용 가능한 컴포넌트(버튼, 테이블, 모달 등)를 `th:fragment`로 분리할지 결정합니다.

Step 2: 데이터 명세 및 인터페이스 확정 (Data Contract)
- **DTO 설계**: 화면에 전달할 데이터를 담는 **Record** 타입의 DTO를 먼저 정의합니다.
- **Model 변수명**: Controller에서 `model.addAttribute()`로 넘길 변수명을 명확히 하고, 해당 변수가 리스트인지 단일 객체인지 확정합니다.

Step 3: Mock 데이터를 활용한 화면 검증 (Mocking & Template)
- **더미 데이터 주입**: 백엔드 로직이 완성되기 전이라도, Controller에서 임시 데이터를 생성하여 `Model`에 담아 보냅니다.
- **타임리프 구현**: `th:each`, `th:if`, `th:text` 등을 사용하여 화면에 데이터가 의도대로 출력되는지 Bootstrap 5 스타일을 적용하며 확인합니다.

Step 4: 백엔드 통합 및 실데이터 연동 (Integration)
- **Mapper/Service 연동**: Mock 데이터를 실제 DB 조회 데이터로 교체합니다.
- **권한 제어**: `@PreAuthorize` 및 Thymeleaf Extras Spring Security를 사용하여 로그인 유저의 권한(`ADMIN`, `MANAGER`, `USER`)에 따른 메뉴/버튼 노출 여부를 처리합니다.

Step 5: 인터랙션 및 시각화 구현 (Interactions & visualization)
- **JavaScript 추가**: 필요한 경우에만 JS를 작성하며, Bootstrap의 모달/툴팁 등을 활성화합니다.
- **Chart.js 연동**: [결산형 패턴]의 경우, Chart.js를 사용하여 부서별 분포도나 개인 역량 차트 등의 시각화를 구현합니다.
- **유효성 검사**: HTML5 기본 유효성 검사와 더불어 서버 사이드(`@Valid`) 검증 결과를 화면에 적절히 표시합니다.
