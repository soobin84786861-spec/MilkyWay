import { useEffect, useState } from 'react';
import { District } from '../types';
import { RISK_COLOR, RISK_TEXT_CLASS } from '../utils/riskUtils';

interface Props {
  districts: District[];
  onDistrictClick: (d: District) => void;
  visible: boolean;
}

export default function Top5Panel({ districts, onDistrictClick, visible }: Props) {
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    if (!visible) {
      setExpanded(false);
    }
  }, [visible]);

  if (districts.length === 0) {
    return null;
  }

  function handleDistrictSelect(district: District) {
    onDistrictClick(district);
    setExpanded(false);
  }

  return (
    <>
      <div
        className={`absolute bottom-6 right-6 z-20 hidden w-60 rounded-2xl border border-slate-100 bg-white/92 p-4 shadow-lg backdrop-blur-sm transition-all duration-300 md:block ${
          visible ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-4 opacity-0'
        }`}
      >
        <div className="mb-3 flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-full bg-red-500" />
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-700">위험 지역 TOP 5</p>
        </div>

        <div className="flex flex-col gap-2.5">
          {districts.map((district, index) => (
            <button
              key={district.id}
              onClick={() => onDistrictClick(district)}
              className="group flex w-full items-center gap-2.5 rounded-lg px-1 py-0.5 text-left transition-colors hover:bg-slate-50"
            >
              <span className="w-4 flex-shrink-0 text-xs font-bold text-slate-400">{index + 1}</span>
              <span
                className="h-2 w-2 flex-shrink-0 rounded-full"
                style={{ backgroundColor: RISK_COLOR[district.riskLevel] }}
              />
              <span className="flex-1 text-sm font-medium text-slate-700 group-hover:text-slate-900">
                {district.name}
              </span>
              <span className={`text-sm font-bold ${RISK_TEXT_CLASS[district.riskLevel]}`}>
                {district.probability}%
              </span>
            </button>
          ))}
        </div>
      </div>

      <div
        className={`absolute bottom-32 left-3 z-30 md:hidden ${
          visible ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-4 opacity-0'
        } transition-all duration-300`}
      >
        <div className="relative">
          <button
            type="button"
            onClick={() => setExpanded((prev) => !prev)}
            className="inline-flex max-w-[calc(100vw-1.5rem)] items-center gap-2 rounded-full border border-slate-200 bg-white/92 px-3 py-2 shadow-lg backdrop-blur-sm"
          >
            <span className="h-2.5 w-2.5 flex-shrink-0 rounded-full bg-red-500" />
            <span className="truncate text-xs font-semibold text-slate-800">위험 지역 TOP 5</span>
          </button>

          <div
            className={`absolute bottom-[calc(100%+0.5rem)] left-0 w-[min(20rem,calc(100vw-1.5rem))] rounded-2xl border border-slate-200 bg-white/95 p-3 shadow-xl backdrop-blur-sm transition-all duration-300 ${
              visible && expanded
                ? 'translate-y-0 opacity-100'
                : 'pointer-events-none translate-y-3 opacity-0'
            }`}
          >
            <div className="mb-2 flex items-center justify-between gap-2">
              <div className="min-w-0">
                <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400">TOP 5</p>
                <p className="truncate text-sm font-semibold text-slate-800">
                  {districts[0] ? `${districts[0].name} ${districts[0].probability}%` : '데이터 없음'}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setExpanded(false)}
                className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-semibold text-slate-500"
              >
                닫기
              </button>
            </div>

            <div className="flex flex-col gap-2">
              {districts.map((district, index) => (
                <button
                  key={district.id}
                  onClick={() => handleDistrictSelect(district)}
                  className="flex items-center gap-2.5 rounded-xl px-2 py-2 text-left transition-colors hover:bg-slate-50"
                >
                  <span className="w-4 flex-shrink-0 text-xs font-bold text-slate-400">{index + 1}</span>
                  <span
                    className="h-2.5 w-2.5 flex-shrink-0 rounded-full"
                    style={{ backgroundColor: RISK_COLOR[district.riskLevel] }}
                  />
                  <span className="min-w-0 flex-1 truncate text-sm font-medium text-slate-700">
                    {district.name}
                  </span>
                  <span className={`text-sm font-bold ${RISK_TEXT_CLASS[district.riskLevel]}`}>
                    {district.probability}%
                  </span>
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
