import { RiskFilterType } from '../types';

interface Props {
  riskFilter: RiskFilterType;
  onRiskFilterChange: (f: RiskFilterType) => void;
  cctvEnabled: boolean;
  onCctvToggle: () => void;
}

const RISK_OPTIONS: RiskFilterType[] = [
  '\uC804\uCCB4',
  '\uC8FC\uC758',
  '\uC704\uD5D8',
  '\uB9E4\uC6B0\uC704\uD5D8',
];

const RISK_ACTIVE_CLASS: Record<RiskFilterType, string> = {
  '\uC804\uCCB4': 'bg-slate-800 text-white',
  '\uC8FC\uC758': 'bg-yellow-500 text-white',
  '\uC704\uD5D8': 'bg-orange-500 text-white',
  '\uB9E4\uC6B0\uC704\uD5D8': 'bg-red-500 text-white',
};

export default function TopNav({
  riskFilter,
  onRiskFilterChange,
  cctvEnabled,
  onCctvToggle,
}: Props) {
  return (
    <header className="h-16 bg-white border-b border-slate-100 shadow-sm flex items-center justify-between px-6 z-10 flex-shrink-0">
      <div className="flex items-center gap-2.5">
        <span className="text-red-500 text-xl">•</span>
        <h1 className="text-[17px] font-bold text-slate-800 tracking-tight whitespace-nowrap">
          {'\uC11C\uC6B8 \uB7EC\uBE0C\uBC84\uADF8 \uC704\uD5D8 \uC9C0\uB3C4'}
        </h1>
      </div>

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onCctvToggle}
          className={`flex items-center gap-2 rounded-full border px-3 py-1.5 text-sm font-medium transition-colors ${
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
