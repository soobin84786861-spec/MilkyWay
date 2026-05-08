import { useEffect, useState } from 'react';
import { District } from '../types';
import { RISK_TEXT_CLASS, getRiskBarColor } from '../utils/riskUtils';
import RiskBadge from './RiskBadge';
import { AiRiskAnalysisResponse, fetchAiRiskAnalysis } from '../api/riskApi';

interface Props {
  district: District | null;
  onClose: () => void;
}

const SKY: Record<number, { label: string; icon: string }> = {
  1: { label: '맑음', icon: '☀️' },
  3: { label: '구름많음', icon: '⛅' },
  4: { label: '흐림', icon: '☁️' },
};

const PTY: Record<number, { label: string; icon: string }> = {
  0: { label: '없음', icon: '🌤️' },
  1: { label: '비', icon: '🌧️' },
  2: { label: '비/눈', icon: '🌨️' },
  3: { label: '눈', icon: '❄️' },
  5: { label: '빗방울', icon: '🌦️' },
  6: { label: '빗방울/눈날림', icon: '🌨️' },
  7: { label: '눈날림', icon: '🌬️' },
};

export default function DetailPanel({ district, onClose }: Props) {
  const [aiData, setAiData] = useState<AiRiskAnalysisResponse | null>(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState(false);
  const showHighRiskSections =
    district?.riskLevel === 'DANGER' || district?.riskLevel === 'CRITICAL';

  useEffect(() => {
    if (!district) return;
    setAiData(null);
    setAiError(false);
    setAiLoading(true);
    fetchAiRiskAnalysis(district.districtCode)
      .then(setAiData)
      .catch(() => setAiError(true))
      .finally(() => setAiLoading(false));
  }, [district?.districtCode]);

  return (
    <div
      className={`absolute inset-0 z-30 ${district ? 'pointer-events-auto' : 'pointer-events-none'}`}
    >
      <div
        onClick={onClose}
        className={`absolute inset-0 bg-slate-950/30 backdrop-blur-[1px] transition-opacity duration-300 md:hidden ${
          district ? 'opacity-100' : 'opacity-0'
        }`}
      />

      <div
        className={`absolute inset-x-0 bottom-0 h-[78dvh] rounded-t-[28px] bg-white shadow-2xl transition-transform duration-300 ease-out md:inset-y-0 md:right-0 md:left-auto md:h-full md:w-[380px] md:rounded-none md:rounded-l-[28px] ${
          district ? 'translate-y-0 md:translate-x-0' : 'translate-y-full md:translate-x-full'
        }`}
      >
        {!district ? null : (
          <div className="flex h-full flex-col">
            <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4 md:px-6">
              <div className="flex min-w-0 items-center gap-2">
                <span className="text-slate-500">📍</span>
                <h2 className="truncate text-base font-bold text-slate-800 md:text-lg">
                  {district.name}
                </h2>
              </div>
              <button
                onClick={onClose}
                className="flex h-9 w-9 items-center justify-center rounded-full text-lg leading-none text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
              >
                ×
              </button>
            </div>

            <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5 md:px-6">
              <section>
                <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">
                  발생 위험도
                </p>
                <RiskBadge level={district.riskLevel} size="lg" />
              </section>

              <section>
                <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">
                  러브버그 발생 확률
                </p>
                <p className={`text-4xl font-extrabold ${RISK_TEXT_CLASS[district.riskLevel]}`}>
                  {district.probability}%
                </p>
                <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className="h-full rounded-full transition-all duration-700"
                    style={{
                      width: `${district.probability}%`,
                      backgroundColor: getRiskBarColor(district.riskLevel),
                    }}
                  />
                </div>
              </section>

              <section>
                <p className="mb-2.5 text-xs font-semibold uppercase tracking-wide text-slate-400">
                  근거 데이터
                </p>
                <div className="space-y-2">
                  {[
                    { icon: '🌡️', label: '온도', value: `${district.temperature}°C` },
                    { icon: '💧', label: '습도', value: `${district.humidity}%` },
                    {
                      icon: (SKY[district.sky] ?? SKY[1]).icon,
                      label: '하늘상태',
                      value: (SKY[district.sky] ?? SKY[1]).label,
                    },
                    ...(district.precipitationType !== 0
                      ? [
                          {
                            icon: (PTY[district.precipitationType] ?? PTY[0]).icon,
                            label: '강수형태',
                            value: (PTY[district.precipitationType] ?? PTY[0]).label,
                          },
                        ]
                      : []),
                    { icon: '🌬️', label: '풍속', value: `${district.windSpeed.toFixed(1)} m/s` },
                  ].map(({ icon, label, value }) => (
                    <div
                      key={label}
                      className="flex items-center justify-between rounded-xl bg-slate-50 px-4 py-2.5"
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

              <section>
                <div className="mb-2.5 flex items-center gap-1.5">
                  <span className="text-amber-500">🤖</span>
                  <p className="text-xs font-semibold uppercase tracking-wide text-amber-600">
                    AI 위험 분석
                  </p>
                </div>

                {aiLoading && (
                  <div className="flex items-center gap-2 px-1 text-sm text-amber-600">
                    <div className="h-4 w-4 animate-spin rounded-full border-2 border-amber-300 border-t-amber-600" />
                    <span>AI 분석 중...</span>
                  </div>
                )}

                {aiError && !aiLoading && (
                  <p className="px-1 text-sm text-slate-400">AI 분석을 불러오지 못했습니다.</p>
                )}

                {aiData && !aiLoading && (
                  <div className="space-y-3">
                    <p className="text-sm leading-relaxed text-slate-700">{aiData.summary}</p>

                    <div className="flex items-start gap-2.5 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3">
                      <span className="flex-shrink-0 text-base">💚</span>
                      <p className="text-sm font-medium leading-relaxed text-emerald-800">
                        {aiData.comfortMessage}
                      </p>
                    </div>

                    <div className="flex items-start gap-2.5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
                      <span className="flex-shrink-0 text-base">⏰</span>
                      <p className="text-sm font-medium leading-relaxed text-amber-800">
                        {aiData.timeAdvice}
                      </p>
                    </div>

                    {showHighRiskSections && (
                      <>
                        <div className="space-y-2">
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                            행동 가이드
                          </p>
                          {aiData.actionGuides.map((guide, i) => (
                            <div
                              key={i}
                              className="flex items-start gap-2.5 rounded-xl border border-slate-100 bg-slate-50 px-4 py-2.5"
                            >
                              <span className="mt-0.5 flex-shrink-0 text-xs font-bold text-slate-400">
                                {i + 1}
                              </span>
                              <span className="text-sm text-slate-700">{guide}</span>
                            </div>
                          ))}
                        </div>

                        <div className="space-y-2">
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                            핵심 요인
                          </p>
                          {aiData.riskFactors.map((factor, i) => (
                            <div
                              key={i}
                              className="flex items-start gap-2.5 rounded-xl border border-red-100 bg-red-50 px-4 py-2.5"
                            >
                              <span className="mt-0.5 flex-shrink-0 text-xs font-bold text-red-400">
                                {i + 1}
                              </span>
                              <span className="text-sm text-red-800">{factor}</span>
                            </div>
                          ))}
                        </div>
                      </>
                    )}
                  </div>
                )}
              </section>

              <div className="h-2" />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
