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

서울 전역의 러브버그 출몰 위험도를 지도에서 확인할 수 있는 웹 서비스입니다.  
자치구별 위험도, AI 요약, CCTV 확인, 과거 시즌 가중치까지 한 화면에서 볼 수 있도록 구성되어 있습니다.

- 배포 주소: [https://lovebugmap.co.kr](https://lovebugmap.co.kr)
- 프론트엔드: React 18, TypeScript, Vite, Tailwind CSS
- 백엔드: Spring Boot, Java 17

## 주요 기능

- 자치구별 러브버그 위험도 지도
- `전체 / 주의 / 위험 / 매우위험` 필터
- 위험 지역 TOP 5 위젯
- 자치구 상세 패널
  - 현재 위험도
  - 기온, 습도, 풍속, 조도
  - AI 요약
  - 행동 가이드
- CCTV 보기
  - UTIC CCTV 모달 뷰어
- 과거 시즌 보정
  - `A / B / C` 시즌 등급 기반 현재 위험도 보정
- 모바일 반응형 UI

## 위험도 계산 개요

현재 위험도는 아래 데이터들을 조합해 계산합니다.

- 기상청 단기예보/실황
- 서울시 S-DoT 조도 데이터
- 서울시 교통량 이력 데이터
- 자치구별 공원/녹지 기반 서식지 계수
- 과거 3개년 시즌 데이터

과거 시즌 데이터는 `src/main/resources/pastData/data.json`을 기준으로 날짜별 시즌 등급을 계산해 현재 위험도에 보정값으로 반영합니다.

- `A`: `finalRisk = baseRisk`
- `B`: `finalRisk = baseRisk * 0.8`
- `C`: `finalRisk = baseRisk * 0.2`

## 사용 데이터

### 서울 열린데이터광장

- 스마트서울 도시데이터 센서(S-DoT) 환경정보
- 서울시 교통량 이력 정보
- 서울시 자치구별 공원현황

### 공공데이터포털

- 기상청_단기예보 조회서비스

### 경찰청 도시교통정보센터(UTIC)

- CCTV 정보 / CCTV 스트림 조회

## 프로젝트 구조

```text
MilkyWay/
├─ src/main/java/com/skku/milkyway/api/
│  ├─ ai/
│  ├─ cctv/
│  ├─ forest/
│  ├─ illumination/
│  ├─ risk/
│  ├─ traffic/
│  └─ weather/
├─ src/main/resources/
│  ├─ cctv/
│  ├─ forest/
│  ├─ pastData/
│  └─ traffic/
└─ frontend/
   ├─ public/
   └─ src/
```

## 로컬 실행

### 1. 백엔드 설정 파일 준비

`src/main/resources/application.properties`는 `.gitignore`에 포함되어 있어 저장소에 올라가지 않습니다.  
팀원은 각자 로컬에 파일을 만들어서 아래 값을 채워야 합니다.

```properties
spring.application.name=MilkyWay
server.port=8080

# UTIC CCTV open data
utic.cctv-open-data.key=

# KMA weather API
kma.api-key=

# Seoul illumination API
seoul.illumination-api.api-key=
seoul.illumination-api.base-url=http://openapi.seoul.go.kr:8088
seoul.illumination-api.service-name=sDoTEnv
seoul.illumination-api.batch-size=1000
seoul.illumination-api.cache-ttl-ms=3600000

# Seoul traffic API
seoul.traffic-api.api-key=
seoul.traffic-api.base-url=http://openapi.seoul.go.kr:8088
seoul.traffic-api.history-service-name=VolInfo
seoul.traffic-api.batch-size=1000
seoul.traffic-api.cache-ttl-ms=3600000
seoul.traffic-api.mapping-file-path=classpath:traffic/spot-district-map.json

# Spring AI (optional)
spring.ai.google.genai.api-key=
spring.ai.google.genai.chat.options.model=gemini-2.5-flash
```

### 2. 프론트엔드 환경변수 준비

`frontend/.env` 파일을 만들고 카카오 지도 키를 넣습니다.

```env
VITE_KAKAO_MAP_KEY=
```

참고:

- 예시 파일: [frontend/.env.example](C:/Users/soobi/git/MilkyWay/frontend/.env.example)
- 카카오 지도 도메인 등록 시 `http://localhost:5173`를 포함해야 합니다.

### 3. 백엔드 실행

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

### 4. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

기본 개발 주소:

- 프론트엔드: `http://localhost:5173`
- 백엔드: `http://localhost:8080`

## 주요 API

### `GET /api/risk/regions`

자치구별 위험도 목록을 반환합니다.

Query parameter:

- `riskLevel=SAFE|CAUTION|DANGER|CRITICAL`

응답 예시:

```json
[
  {
    "regionName": "강남구",
    "latitude": 37.517,
    "longitude": 127.047,
    "riskLevel": "CRITICAL",
    "riskPercent": 89
  }
]
```

### `GET /api/cctv`

서울 CCTV 목록을 반환합니다.

### `GET /api/cctv/{cctvId}/stream`

선택한 CCTV 스트림 페이지를 반환합니다.

## 프론트엔드 메타/배포 파일

프론트엔드에는 공유 및 SEO용 정적 파일이 포함되어 있습니다.

- [frontend/index.html](C:/Users/soobi/git/MilkyWay/frontend/index.html)
  - title
  - description
  - canonical
  - Open Graph 메타태그
- [frontend/public/og-image.png](C:/Users/soobi/git/MilkyWay/frontend/public/og-image.png)
- [frontend/public/robots.txt](C:/Users/soobi/git/MilkyWay/frontend/public/robots.txt)
- [frontend/public/favicon.svg](C:/Users/soobi/git/MilkyWay/frontend/public/favicon.svg)

## 참고 사항

- 교통량 이력 API 서비스명은 `VolInfo`를 사용합니다.
- 교통량 지점-자치구 매핑은 `classpath:traffic/spot-district-map.json`을 사용합니다.
- UTIC CCTV는 신청된 IP 대역과 발급 키가 맞아야 정상 동작합니다.
- `frontend/dist`는 빌드 산출물이며 배포 시 생성됩니다.

## 팀 메모

- 실제 API 키는 git에 올리지 않습니다.
- `application.properties`는 팀 내부에서 별도로 공유합니다.
- README는 현재 구현 기준 문서이며, 데이터 소스나 실행 방식이 바뀌면 함께 갱신합니다.
