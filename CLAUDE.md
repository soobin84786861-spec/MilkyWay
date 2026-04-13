# MilkyWay

서울 자치구별 **러브버그(사랑벌레) 출몰 위험도**를 시각화하는 웹 서비스.
인스타그램 크롤링 데이터를 기반으로 위험 등급을 산출하고, 카카오맵 위에 오버레이로 표시한다.

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| Frontend | React 18, TypeScript, Vite, Tailwind CSS |
| Backend | Spring Boot 4.0.5, Java 17, Lombok |
| 지도 | Kakao Maps JavaScript SDK |
| 데이터 수집 | Instagram 크롤링 (1시간 스케줄) |
| Database | MySQL (예정) |
| Communication | REST API |
| AI Extension | Spring AI 기반 LLM 연동 예정 |

---

## 프로젝트 구조

```
MilkyWay/
├── src/main/java/com/skku/milkyway/
│   ├── MilkyWayApplication.java
│   ├── api/
│   │   ├── instagram/
│   │   │   ├── config/        InstagramProperties.java (세션/CSRF 토큰)
│   │   │   ├── dto/           InstagramPostDto.java
│   │   │   ├── scheduler/     InstagramCountScheduler.java (매 1시간 실행)
│   │   │   ├── service/       InstagramCrawlerService (인터페이스)
│   │   │   │                  DummyInstagramCrawlerService (현재 주입)
│   │   │   │                  RealInstagramCrawlerService (추후 교체)
│   │   │   │                  InstagramDistrictCountService
│   │   │   └── store/         InstagramCountStore.java (인메모리 캐시)
│   │   ├── main/
│   │   │   └── controller/    MainController.java (Thymeleaf "/" 라우팅)
│   │   └── risk/
│   │       ├── code/          RiskLevel.java, SeoulDistrict.java (enum)
│   │       ├── controller/    RiskController.java
│   │       ├── response/      RegionRiskResponse.java
│   │       └── service/       RiskService.java
│   └── config/
│       └── SchedulingConfig.java
├── src/main/resources/
│   ├── application.properties
│   └── templates/index.html   (Thymeleaf — 현재 미사용)
└── frontend/
    ├── src/
    │   ├── App.tsx             (메인 레이아웃, 상태 관리)
    │   ├── api/riskApi.ts      (백엔드 API 호출)
    │   ├── components/
    │   │   ├── KakaoMap.tsx    (카카오맵 + 커스텀 오버레이)
    │   │   ├── DetailPanel.tsx (선택 자치구 상세 패널)
    │   │   ├── Top5Panel.tsx   (위험도 상위 5개 목록)
    │   │   ├── TopNav.tsx      (필터 탭 — 전체/주의/위험/매우위험)
    │   │   ├── Legend.tsx      (범례)
    │   │   └── RiskBadge.tsx   (위험 등급 배지)
    │   ├── data/mockData.ts    (상세 필드 임시 데이터)
    │   ├── types/index.ts      (District, RiskLevel, RiskFilterType 등)
    │   └── utils/riskUtils.ts  (RISK_COLOR 등 유틸)
    ├── vite.config.ts
    ├── tailwind.config.js
    └── package.json
```

---

## 개발 환경 실행

### 백엔드 (포트 8080)
```bash
./gradlew bootRun
```

### 프론트엔드 (포트 5173)
```bash
cd frontend
npm install
npm run dev
```

Vite는 `/api/**` 요청을 `http://localhost:8080`으로 프록시한다 (`vite.config.ts`).

### 환경 변수
`frontend/.env` 파일 (`.env.example` 참고):
```
VITE_KAKAO_MAP_KEY=<카카오 JavaScript 앱 키>
```
카카오 개발자 콘솔 → 앱 설정 → 플랫폼 → Web 에 `http://localhost:5173` 등록 필요.

`src/main/resources/application.properties`:
```
instagram.session-id=<Instagram sessionid 쿠키>
instagram.csrf-token=<Instagram csrftoken 쿠키>
```

---

## API

### `GET /api/risk/regions`
서울 자치구별 위험도 목록 반환.

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `riskLevel` | `SAFE \| CAUTION \| DANGER \| CRITICAL` | 선택 필터 (없으면 전체) |

응답 예시:
```json
[
  {
    "regionName": "강남구",
    "latitude": 37.517,
    "longitude": 127.047,
    "riskLevel": "CRITICAL",
    "riskPercent": 89,
    "instaCnt": 0
  }
]
```

---

## 핵심 도메인 개념

### 위험 등급 (RiskLevel)
| 등급 | 의미 | 프론트 필터 |
|------|------|-------------|
| `SAFE` | 안전 | 전체 |
| `CAUTION` | 주의 | 주의 |
| `DANGER` | 위험 | 위험 |
| `CRITICAL` | 매우 위험 | 매우위험 |

### Instagram 크롤링 흐름
```
InstagramCountScheduler (매 1시간, cron: "0 0 * * * *")
  → InstagramCrawlerService.fetchPosts("#러브버그")
      현재: DummyInstagramCrawlerService (더미 데이터)
      예정: RealInstagramCrawlerService (실제 크롤링)
  → InstagramDistrictCountService.countByDistrict()
  → InstagramCountStore.update()  ← 인메모리 캐시 갱신
  → RiskService.getRegions() 에서 읽어 API 응답에 포함
```

### 프론트엔드 데이터 흐름
```
App.tsx
  fetchRegions(riskFilter) → /api/risk/regions
  → mergeWithMock()  (API 응답 + mockData 병합: 상세 필드는 mock에서 채움)
  → districts state
  → KakaoMap (오버레이), Top5Panel (랭킹), DetailPanel (상세)
```

---

## 주요 결정 사항

- **현재 위험도 데이터는 하드코딩**: `RiskService.buildData()`에 자치구별 퍼센트가 정적으로 정의되어 있다. 추후 실제 알고리즘으로 대체 예정.
- **InstagramCrawlerService는 인터페이스**: 현재 `DummyInstagramCrawlerService`가 주입되며, `RealInstagramCrawlerService`로 교체할 때 `@Primary` 또는 `@Profile`을 활용한다.
- **District 타입은 mock + API 병합**: `mockData.ts`에 `temperature`, `humidity`, `actionGuides`, `aiAnalysis` 등 상세 필드가 임시로 존재한다. 백엔드 API가 이를 내려줄 수 있게 되면 mock 의존을 제거한다.
- **카카오맵은 `index.html`에서 SDK 스크립트로 로드**: `window.kakao`를 전역으로 사용하며, TypeScript에서는 `declare global`로 타입 선언.
- **MySQL은 아직 미연결**: `build.gradle`에 JPA/MySQL 의존성 없음. 연결 시 추가 필요.