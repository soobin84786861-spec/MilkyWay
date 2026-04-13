<div align="center">

# 🪲 MilkyWay

### 서울 자치구별 러브버그 출몰 위험도 실시간 시각화 플랫폼

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://reactjs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev/)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)

> 2026 서울시 빅데이터 활용 경진대회 출품작

</div>

---

## 🌌 프로젝트 소개

**MilkyWay**는 인스타그램 크롤링 데이터를 기반으로 서울 25개 자치구의 **러브버그(사랑벌레) 출몰 위험도**를 실시간으로 산출하고, **카카오맵 위에 직관적인 오버레이**로 시각화하는 웹 서비스입니다.

> *"사랑벌레가 어디에 있는지, 지도 위에서 한눈에."*

---

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| 🗺️ **실시간 위험도 지도** | 카카오맵 위에 자치구별 위험 등급을 색상 오버레이로 표시 |
| 📊 **위험도 TOP 5 랭킹** | 현재 가장 위험한 자치구 순위를 실시간 제공 |
| 🔍 **필터 탭** | 전체 / 주의 / 위험 / 매우위험 등급별 필터링 |
| 📋 **자치구 상세 패널** | 기온, 습도, 행동 요령, AI 분석 결과 표시 |
| ⏱️ **1시간 주기 자동 갱신** | Instagram 크롤러가 매 시간 최신 데이터 수집 |

---

## 🎨 위험 등급 체계

```
  SAFE      CAUTION      DANGER      CRITICAL
  🟢 안전    🟡 주의      🔴 위험     💀 매우위험
```

| 등급 | 색상 | 설명 |
|------|------|------|
| `SAFE` | 🟢 초록 | 출몰 없음 — 안심 |
| `CAUTION` | 🟡 노랑 | 소수 목격 — 주의 권고 |
| `DANGER` | 🔴 빨강 | 다수 출몰 — 야외 활동 자제 |
| `CRITICAL` | ⬛ 검정 | 대규모 출몰 — 외출 최소화 |

---

## 🏗️ 기술 스택

```
┌─────────────────────────────────────────────────────────┐
│                       Frontend                          │
│     React 18 · TypeScript · Vite · Tailwind CSS        │
│                   Kakao Maps SDK                        │
├─────────────────────────────────────────────────────────┤
│                       Backend                           │
│        Spring Boot 4.0.5 · Java 17 · Lombok            │
│             Spring AI (LLM 연동 예정)                   │
├─────────────────────────────────────────────────────────┤
│                    Data Pipeline                        │
│       Instagram Crawler → InMemory Store → REST API    │
├─────────────────────────────────────────────────────────┤
│                     Database                            │
│                   MySQL (연동 예정)                      │
└─────────────────────────────────────────────────────────┘
```

---

## 📁 프로젝트 구조

```
MilkyWay/
├── src/main/java/com/skku/milkyway/
│   ├── MilkyWayApplication.java
│   ├── api/
│   │   ├── instagram/
│   │   │   ├── config/       InstagramProperties.java
│   │   │   ├── dto/          InstagramPostDto.java
│   │   │   ├── scheduler/    InstagramCountScheduler.java  ← 매 1시간
│   │   │   ├── service/      InstagramCrawlerService (인터페이스)
│   │   │   │                 DummyInstagramCrawlerService  ← 현재
│   │   │   │                 RealInstagramCrawlerService   ← 예정
│   │   │   │                 InstagramDistrictCountService
│   │   │   └── store/        InstagramCountStore.java
│   │   └── risk/
│   │       ├── code/         RiskLevel.java · SeoulDistrict.java
│   │       ├── controller/   RiskController.java
│   │       ├── response/     RegionRiskResponse.java
│   │       └── service/      RiskService.java
│   └── config/
│       └── SchedulingConfig.java
│
└── frontend/
    └── src/
        ├── App.tsx               ← 메인 레이아웃 · 상태 관리
        ├── api/riskApi.ts        ← 백엔드 API 호출
        ├── components/
        │   ├── KakaoMap.tsx      ← 지도 + 커스텀 오버레이
        │   ├── DetailPanel.tsx   ← 자치구 상세
        │   ├── Top5Panel.tsx     ← 위험도 랭킹
        │   ├── TopNav.tsx        ← 필터 탭
        │   ├── Legend.tsx        ← 범례
        │   └── RiskBadge.tsx     ← 등급 배지
        ├── data/mockData.ts
        ├── types/index.ts
        └── utils/riskUtils.ts
```

---

## 🔄 데이터 파이프라인

```
Instagram (#러브버그)
        │
        ▼  매 1시간 (cron: "0 0 * * * *")
InstagramCountScheduler
        │
        ▼
InstagramCrawlerService
  ├── DummyInstagramCrawlerService  (현재 — 더미 데이터)
  └── RealInstagramCrawlerService   (예정 — 실제 크롤링)
        │
        ▼
InstagramDistrictCountService
  → 자치구별 게시물 수 집계
        │
        ▼
InstagramCountStore  (인메모리 캐시)
        │
        ▼
RiskService.getRegions()
        │
        ▼
GET /api/risk/regions  →  Frontend
```

---

## 🚀 로컬 실행 방법

### 1. 환경 변수 설정

**`src/main/resources/application.properties`**
```properties
instagram.session-id=<Instagram sessionid 쿠키>
instagram.csrf-token=<Instagram csrftoken 쿠키>
```

**`frontend/.env`** (`.env.example` 참고)
```env
VITE_KAKAO_MAP_KEY=<카카오 JavaScript 앱 키>
```

> 카카오 개발자 콘솔 → 앱 설정 → 플랫폼 → Web에 `http://localhost:5173` 등록 필요

### 2. 백엔드 실행 (포트 8080)

```bash
./gradlew bootRun
```

### 3. 프론트엔드 실행 (포트 5173)

```bash
cd frontend
npm install
npm run dev
```

> Vite가 `/api/**` 요청을 자동으로 `http://localhost:8080`에 프록시합니다.

---

## 📡 REST API

### `GET /api/risk/regions`

서울 자치구별 위험도 목록을 반환합니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `riskLevel` | `SAFE \| CAUTION \| DANGER \| CRITICAL` | 선택 | 등급 필터 (없으면 전체 반환) |

**Response Example**

```json
[
  {
    "regionName": "강남구",
    "latitude": 37.517,
    "longitude": 127.047,
    "riskLevel": "CRITICAL",
    "riskPercent": 89,
    "instaCnt": 142
  }
]
```

---

## 🛣️ 로드맵

- [x] 더미 데이터 기반 위험도 시각화
- [x] 카카오맵 자치구 오버레이
- [x] 위험도 필터 & TOP 5 랭킹
- [ ] Instagram 실제 크롤러 연동
- [ ] MySQL 데이터베이스 연결
- [ ] Spring AI 기반 LLM 분석 코멘트
- [ ] 시간대별 위험도 추이 그래프

---

## 👥 팀

**SKKU MilkyWay Team** — 성균관대학교

---

<div align="center">

Made with ❤️ and a lot of ☕ for the 2026 서울시 빅데이터 활용 경진대회

</div>
