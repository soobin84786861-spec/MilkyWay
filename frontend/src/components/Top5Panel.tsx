import { District } from '../types';
import { RISK_COLOR, RISK_TEXT_CLASS } from '../utils/riskUtils';

interface Props {
  districts: District[];
  onDistrictClick: (d: District) => void;
  visible: boolean;
}

export default function Top5Panel({ districts, onDistrictClick, visible }: Props) {
  return (
    <div
      className={`absolute bottom-6 right-6 bg-white/90 backdrop-blur-sm rounded-2xl shadow-lg border border-slate-100 p-4 w-56 z-20 transition-all duration-300 ${
        visible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4 pointer-events-none'
      }`}
    >
      <div className="flex items-center gap-1.5 mb-3">
        <span className="text-red-500 text-sm">📈</span>
        <p className="text-xs font-semibold text-slate-700 uppercase tracking-wide">위험 지역 TOP 5</p>
      </div>
      <div className="flex flex-col gap-2.5">
        {districts.map((d, i) => (
          <button
            key={d.id}
            onClick={() => onDistrictClick(d)}
            className="flex items-center gap-2.5 w-full text-left hover:bg-slate-50 rounded-lg px-1 py-0.5 transition-colors group"
          >
            <span className="text-xs font-bold text-slate-400 w-4 flex-shrink-0">{i + 1}</span>
            <span
              className="w-2 h-2 rounded-full flex-shrink-0"
              style={{ backgroundColor: RISK_COLOR[d.riskLevel] }}
            />
            <span className="text-sm font-medium text-slate-700 flex-1 group-hover:text-slate-900">
              {d.name}
            </span>
            <span className={`text-sm font-bold ${RISK_TEXT_CLASS[d.riskLevel]}`}>
              {d.probability}%
            </span>
            {d.instaCnt > 0 && (
              <span className="text-xs text-slate-400 font-normal">
                📸{d.instaCnt}
              </span>
            )}
          </button>
        ))}
      </div>
    </div>
  );
}