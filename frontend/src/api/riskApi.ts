import { RiskLevel, RiskFilterType } from '../types';

export interface RegionRiskResponse {
  districtCode: string;
  regionName: string;
  latitude: number;
  longitude: number;
  riskLevel: RiskLevel;
  riskPercent: number;
  instaCnt: number;
}

export interface AiRiskAnalysisResponse {
  description: string;
  actionGuides: string[];
}

const FILTER_PARAM: Partial<Record<RiskFilterType, RiskLevel>> = {
  '주의': 'CAUTION',
  '위험': 'DANGER',
  '매우위험': 'CRITICAL',
};

export async function fetchRegions(riskFilter: RiskFilterType): Promise<RegionRiskResponse[]> {
  const level = FILTER_PARAM[riskFilter];
  const query = level ? `?riskLevel=${level}` : '';
  const res = await fetch(`/api/risk/regions${query}`);
  if (!res.ok) throw new Error(`API ${res.status}`);
  return res.json();
}

export async function fetchAiRiskAnalysis(districtCode: string): Promise<AiRiskAnalysisResponse> {
  const res = await fetch(`/api/ai/risk-analysis?district=${districtCode}`);
  if (!res.ok) throw new Error(`AI API ${res.status}`);
  return res.json();
}