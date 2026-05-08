import { RiskFilterType } from '../types';

interface Props {
  riskFilter: RiskFilterType;
  onRiskFilterChange: (f: RiskFilterType) => void;
  cctvEnabled: boolean;
  onCctvToggle: () => void;
}

const RISK_OPTIONS: RiskFilterType[] = ['전체', '주의', '위험', '매우위험'];

const RISK_ACTIVE_CLASS: Record<RiskFilterType, string> = {
  전체: 'bg-slate-800 text-white',
  주의: 'bg-yellow-500 text-white',
  위험: 'bg-orange-500 text-white',
  매우위험: 'bg-red-500 text-white',
};

export default function TopNav({
  riskFilter,
  onRiskFilterChange,
  cctvEnabled,
  onCctvToggle,
}: Props) {
  return (
    <header className="z-10 flex-shrink-0 border-b border-slate-100 bg-white/95 shadow-sm backdrop-blur-sm">
      <div className="flex flex-col gap-3 px-3 py-3 sm:px-6 sm:py-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 items-center gap-2.5">
            <span className="text-lg text-red-500 sm:text-xl">•</span>
            <h1 className="truncate text-base font-bold tracking-tight text-slate-800 sm:text-[17px]">
              서울 러브버그 위험 지도
            </h1>
          </div>

          <button
            type="button"
            onClick={onCctvToggle}
            className={`flex h-8 items-center justify-center gap-1.5 rounded-full border px-2.5 text-xs font-semibold transition-colors sm:hidden ${
              cctvEnabled
                ? 'border-emerald-500 bg-emerald-50 text-emerald-700'
                : 'border-slate-200 bg-white text-slate-500 hover:text-slate-700'
            }`}
          >
            <span
              className={`h-2 w-2 rounded-full ${
                cctvEnabled ? 'bg-emerald-500' : 'bg-slate-300'
              }`}
            />
            <span>CCTV</span>
          </button>
        </div>

        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between lg:justify-end lg:gap-3">
          <button
            type="button"
            onClick={onCctvToggle}
            className={`hidden items-center justify-center gap-2 rounded-full border px-3 py-1.5 text-sm font-medium transition-colors sm:flex ${
              cctvEnabled
                ? 'border-emerald-500 bg-emerald-50 text-emerald-700'
                : 'border-slate-200 bg-white text-slate-500 hover:text-slate-700'
            }`}
          >
            <span
              className={`h-2.5 w-2.5 rounded-full ${
                cctvEnabled ? 'bg-emerald-500' : 'bg-slate-300'
              }`}
            />
            <span>CCTV</span>
          </button>

          <div className="w-full sm:w-auto">
            <div className="grid w-full grid-cols-4 gap-1 rounded-xl bg-slate-100 p-1 sm:flex sm:min-w-max">
              {RISK_OPTIONS.map((option) => (
                <button
                  key={option}
                  type="button"
                  onClick={() => onRiskFilterChange(option)}
                  className={`rounded-lg px-1 py-2 text-xs font-medium transition-all duration-150 sm:whitespace-nowrap sm:px-3.5 sm:py-1.5 sm:text-sm ${
                    riskFilter === option
                      ? RISK_ACTIVE_CLASS[option]
                      : 'text-slate-500 hover:text-slate-700'
                  }`}
                >
                  {option}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}
