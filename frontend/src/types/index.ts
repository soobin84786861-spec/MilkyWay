export type RiskLevel = 'SAFE' | 'CAUTION' | 'DANGER' | 'CRITICAL';

export type DateFilterType = '오늘' | '내일' | '이번 주';
export type RiskFilterType = '전체' | '주의' | '위험' | '매우위험';

export interface ActionGuide {
  severity: 'danger' | 'warning' | 'info';
  message: string;
}

export interface District {
  id: string;
  districtCode: string;    // SeoulDistrict enum 이름 (예: EUNPYEONG)
  name: string;
  riskLevel: RiskLevel;
  riskScore: number;       // 0~100
  probability: number;     // 0~100 (%)
  temperature: number;     // °C
  humidity: number;        // %
  sky: number;             // 하늘상태: 1=맑음, 3=구름많음, 4=흐림
  precipitationType: number; // 강수형태: 0=없음, 1=비, 2=비/눈, 3=눈, 5=빗방울, 6=빗방울+눈날림, 7=눈날림
  windSpeed: number;       // 풍속 (m/s)
  actionGuides: ActionGuide[];
  aiAnalysis: string;
  lat: number;
  lng: number;
  instaCnt: number;        // 인스타그램 언급 횟수
}