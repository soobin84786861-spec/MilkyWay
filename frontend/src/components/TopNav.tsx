import { RiskFilterType } from '../types';

interface Props {
  riskFilter: RiskFilterType;
  onRiskFilterChange: (f: RiskFilterType) => void;
}

const RISK_OPTIONS: RiskFilterType[] = ['전체', '주의', '위험', '매우위험'];

const RISK_ACTIVE_CLASS: Record<RiskFilterType, string> = {
  '전체': 'bg-slate-800 text-white',
  '주의': 'bg-yellow-500 text-white',
  '위험': 'bg-orange-500 text-white',
  '매우위험': 'bg-red-500 text-white',
};

export default function TopNav({ riskFilter, onRiskFilterChange }: Props) {
  return (
    <header className="h-16 bg-white border-b border-slate-100 shadow-sm flex items-center justify-between px-6 z-10 flex-shrink-0">
      {/* 좌측: 로고 */}
      <div className="flex items-center gap-2.5">
        <span className="text-red-500 text-xl">📍</span>
        <h1 className="text-[17px] font-bold text-slate-800 tracking-tight whitespace-nowrap">
          서울 러브버그 위험 지도
        </h1>
      </div>

      {/* 우측: 위험도 필터 */}
      <div className="flex items-center gap-1.5">
        <span className="text-slate-400 text-sm mr-0.5">▼</span>
        <div className="flex bg-slate-100 rounded-lg p-0.5 gap-0.5">
          {RISK_OPTIONS.map((opt) => (
            <button
              key={opt}
              onClick={() => onRiskFilterChange(opt)}
              className={`px-3.5 py-1.5 rounded-md text-sm font-medium transition-all duration-150 ${
                riskFilter === opt
                  ? RISK_ACTIVE_CLASS[opt]
                  : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              {opt}
            </button>
          ))}
        </div>
      </div>
    </header>
  );
}
