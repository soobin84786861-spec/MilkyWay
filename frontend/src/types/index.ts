export type RiskLevel = 'SAFE' | 'CAUTION' | 'DANGER' | 'CRITICAL';

export type DateFilterType = '\uC624\uB298' | '\uB0B4\uC77C' | '\uC774\uBC88 \uC8FC';
export type RiskFilterType = '\uC804\uCCB4' | '\uC8FC\uC758' | '\uC704\uD5D8' | '\uB9E4\uC6B0\uC704\uD5D8';

export interface ActionGuide {
  severity: 'danger' | 'warning' | 'info';
  message: string;
}

export interface District {
  id: string;
  districtCode: string;
  name: string;
  riskLevel: RiskLevel;
  riskScore: number;
  probability: number;
  temperature: number;
  humidity: number;
  sky: number;
  precipitationType: number;
  windSpeed: number;
  actionGuides: ActionGuide[];
  aiAnalysis: string;
  lat: number;
  lng: number;
  instaCnt: number;
}

export interface PublicCctv {
  name: string;
  latitude: number;
  longitude: number;
  cctvId: string;
  streamUrl: string;
}
