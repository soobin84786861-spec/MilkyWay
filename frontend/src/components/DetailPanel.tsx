import { District } from '../types';
import {
  RISK_LABEL,
  RISK_DOT_CLASS,
  RISK_TEXT_CLASS,
  RISK_SCORE_BAR_CLASS,
  getRiskBarColor,
} from '../utils/riskUtils';
import RiskBadge from './RiskBadge';

interface Props {
  district: District | null;
  onClose: () => void;
}

const SEVERITY_STYLE = {
  danger: { bg: 'bg-red-50 border-red-100', icon: '⚠️', text: 'text-red-700' },
  warning: { bg: 'bg-red-50 border-red-100', icon: '⚠️', text: 'text-red-700' },
  info: { bg: 'bg-amber-50 border-amber-100', icon: '💡', text: 'text-amber-700' },
};

function renderAiAnalysis(text: string) {
  return text.split('\n').map((line, i) => {
    if (line.startsWith('**') && line.endsWith('**')) {
      return (
        <p key={i} className="font-semibold text-slate-800 mt-3 mb-1">
          {line.replace(/\*\*/g, '')}
        </p>
      );
    }
    if (line.startsWith('• ')) {
      return (
        <p key={i} className="text-slate-600 text-sm pl-3 leading-relaxed">
          {line}
        </p>
      );
    }
    return (
      <p key={i} className="text-slate-600 text-sm leading-relaxed">
        {line}
      </p>
    );
  });
}

export default function DetailPanel({ district, onClose }: Props) {
  return (
    <div
      className={`absolute top-0 right-0 h-full w-[360px] bg-white shadow-2xl z-30 flex flex-col transition-transform duration-300 ease-out ${
        district ? 'translate-x-0' : 'translate-x-full'
      }`}
    >
      {!district ? null : (
        <>
          {/* 헤더 */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 flex-shrink-0">
            <div className="flex items-center gap-2">
              <span className="text-slate-500">📍</span>
              <h2 className="text-lg font-bold text-slate-800">{district.name}</h2>
            </div>
            <button
              onClick={onClose}
              className="w-8 h-8 flex items-center justify-center rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition-colors text-lg leading-none"
            >
              ×
            </button>
          </div>

          {/* 스크롤 영역 */}
          <div className="flex-1 overflow-y-auto px-6 py-5 space-y-5">
            {/* 발생 위험도 */}
            <section>
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-2">
                발생 위험도
              </p>
              <RiskBadge level={district.riskLevel} size="lg" />
            </section>

            {/* 러브버그 발생 확률 */}
            <section>
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-2">
                러브버그 발생 확률
              </p>
              <p className={`text-4xl font-extrabold ${RISK_TEXT_CLASS[district.riskLevel]}`}>
                {district.probability}%
              </p>
              <div className="mt-2 h-2 bg-slate-100 rounded-full overflow-hidden">
                <div
                  className="h-full rounded-full transition-all duration-700"
                  style={{
                    width: `${district.probability}%`,
                    backgroundColor: getRiskBarColor(district.riskLevel),
                  }}
                />
              </div>
            </section>

            {/* 근거 데이터 */}
            <section>
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-2.5">
                근거 데이터
              </p>
              <div className="space-y-2">
                {[
                  { icon: '🌡️', label: '온도', value: `${district.temperature}°C` },
                  { icon: '💧', label: '습도', value: `${district.humidity}%` },
                ].map(({ icon, label, value }) => (
                  <div
                    key={label}
                    className="flex items-center justify-between bg-slate-50 rounded-xl px-4 py-2.5"
                  >
                    <div className="flex items-center gap-2.5">
                      <span className="text-base">{icon}</span>
                      <span className="text-sm text-slate-600">{label}</span>
                    </div>
                    <span className="text-sm font-semibold text-slate-800">{value}</span>
                  </div>
                ))}
              </div>
            </section>

            {/* 행동 가이드 */}
            <section>
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-2.5">
                행동 가이드
              </p>
              <div className="space-y-2">
                {district.actionGuides.map((guide, i) => {
                  const style = SEVERITY_STYLE[guide.severity];
                  return (
                    <div
                      key={i}
                      className={`flex items-center gap-2.5 px-4 py-2.5 rounded-xl border ${style.bg}`}
                    >
                      <span className="text-sm">{style.icon}</span>
                      <span className={`text-sm font-medium ${style.text}`}>{guide.message}</span>
                    </div>
                  );
                })}
              </div>
            </section>

            {/* AI 위험 분석 */}
            <section>
              <div className="flex items-center gap-1.5 mb-2.5">
                <span className="text-amber-500">✨</span>
                <p className="text-xs font-semibold text-amber-600 uppercase tracking-wide">
                  AI 위험 분석
                </p>
              </div>
              <div className="bg-amber-50 rounded-xl p-4 border border-amber-100">
                {renderAiAnalysis(district.aiAnalysis)}
              </div>
            </section>

            {/* 하단 여백 */}
            <div className="h-2" />
          </div>
        </>
      )}
    </div>
  );
}