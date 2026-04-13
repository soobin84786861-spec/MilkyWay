import { RiskLevel } from '../types';
import { RISK_BG_CLASS, RISK_LABEL, RISK_DOT_CLASS } from '../utils/riskUtils';

interface Props {
  level: RiskLevel;
  size?: 'sm' | 'md' | 'lg';
  showDot?: boolean;
}

export default function RiskBadge({ level, size = 'md', showDot = true }: Props) {
  const sizeClass = {
    sm: 'text-xs px-2 py-0.5',
    md: 'text-sm px-3 py-1',
    lg: 'text-base px-4 py-1.5 font-semibold',
  }[size];

  const dotSize = {
    sm: 'w-1.5 h-1.5',
    md: 'w-2 h-2',
    lg: 'w-2.5 h-2.5',
  }[size];

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full font-medium ${sizeClass} ${RISK_BG_CLASS[level]}`}
    >
      {showDot && (
        <span className={`rounded-full flex-shrink-0 ${dotSize} ${RISK_DOT_CLASS[level]}`} />
      )}
      {RISK_LABEL[level]}
    </span>
  );
}
