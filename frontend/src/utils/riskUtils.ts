import { RiskLevel, RiskFilterType } from '../types';

export const RISK_COLOR: Record<RiskLevel, string> = {
  SAFE: '#22c55e',
  CAUTION: '#eab308',
  DANGER: '#f97316',
  CRITICAL: '#ef4444',
};

export const RISK_BG_CLASS: Record<RiskLevel, string> = {
  SAFE: 'bg-green-100 text-green-700',
  CAUTION: 'bg-yellow-100 text-yellow-700',
  DANGER: 'bg-orange-100 text-orange-700',
  CRITICAL: 'bg-red-100 text-red-700',
};

export const RISK_BORDER_CLASS: Record<RiskLevel, string> = {
  SAFE: 'border-green-300',
  CAUTION: 'border-yellow-300',
  DANGER: 'border-orange-300',
  CRITICAL: 'border-red-300',
};

export const RISK_LABEL: Record<RiskLevel, string> = {
  SAFE: '안전',
  CAUTION: '주의',
  DANGER: '위험',
  CRITICAL: '매우 위험',
};

export const RISK_DOT_CLASS: Record<RiskLevel, string> = {
  SAFE: 'bg-green-500',
  CAUTION: 'bg-yellow-500',
  DANGER: 'bg-orange-500',
  CRITICAL: 'bg-red-500',
};

export const RISK_SCORE_BAR_CLASS: Record<RiskLevel, string> = {
  SAFE: 'bg-green-500',
  CAUTION: 'bg-yellow-500',
  DANGER: 'bg-orange-500',
  CRITICAL: 'bg-red-500',
};

export const RISK_TEXT_CLASS: Record<RiskLevel, string> = {
  SAFE: 'text-green-600',
  CAUTION: 'text-yellow-600',
  DANGER: 'text-orange-500',
  CRITICAL: 'text-red-500',
};

export const FILTER_TO_LEVEL: Record<RiskFilterType, RiskLevel | null> = {
  '전체': null,
  '주의': 'CAUTION',
  '위험': 'DANGER',
  '매우위험': 'CRITICAL',
};

/** 위험 점수 기반 퍼센트 진행 막대 색상 (인라인 style용) */
export function getRiskBarColor(level: RiskLevel): string {
  return RISK_COLOR[level];
}