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
  actionGuides: ActionGuide[];
  aiAnalysis: string;
  lat: number;
  lng: number;
  instaCnt: number;        // 인스타그램 언급 횟수
}