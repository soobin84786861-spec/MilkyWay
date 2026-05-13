import { RiskLevel, RiskFilterType } from '../types';

export interface RegionRiskResponse {
  districtCode: string;
  regionName: string;
  latitude: number;
  longitude: number;
  riskLevel: RiskLevel;
  riskPercent: number;
  instaCnt: number;
  temperature: number;
  humidity: number;
  sky: number;
  precipitationType: number;
  windSpeed: number;
}

export interface AiRiskAnalysisResponse {
  summary: string;
  comfortMessage: string;
  timeAdvice: string;
  actionGuides: string[];
  riskFactors: string[];
}

export interface DefaultRegionRiskAnalysisResponse {
  districtCode: string;
  regionName: string;
  riskLevel: RiskLevel;
  riskPercent: number;
  evidenceData: {
    temperature: number;
    humidity: number;
    illumination: number;
    sky: number;
    precipitationType: number;
    windSpeed: number;
  };
  summary: string;
  comfortMessage: string;
  timeAdvice: string;
}

const FILTER_PARAM: Partial<Record<RiskFilterType, RiskLevel>> = {
  '\uC8FC\uC758': 'CAUTION',
  '\uC704\uD5D8': 'DANGER',
  '\uB9E4\uC6B0\uC704\uD5D8': 'CRITICAL',
};

export async function fetchRegions(riskFilter: RiskFilterType): Promise<RegionRiskResponse[]> {
  const level = FILTER_PARAM[riskFilter];
  const query = level ? `?riskLevel=${level}` : '';
  const res = await fetch(`/api/risk/regions${query}`);
  if (!res.ok) throw new Error(`API ${res.status}`);
  return res.json();
}

export async function fetchAiRiskAnalysis(districtCode: string): Promise<AiRiskAnalysisResponse> {
  const res = await fetch(`/api/risk/regions/${districtCode}`);
  if (!res.ok) throw new Error(`Risk Analysis API ${res.status}`);
  return res.json();
}

export async function fetchDefaultRegionRiskAnalysis(
  districtCode: string
): Promise<DefaultRegionRiskAnalysisResponse> {
  const res = await fetch(`/api/risk/regions/${districtCode}/default`);
  if (!res.ok) throw new Error(`Default Risk Analysis API ${res.status}`);
  return res.json();
}
