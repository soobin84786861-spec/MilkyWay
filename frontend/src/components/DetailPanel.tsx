import { useState, useEffect } from 'react';
import { District } from '../types';
import {
  RISK_TEXT_CLASS,
  getRiskBarColor,
} from '../utils/riskUtils';
import RiskBadge from './RiskBadge';
import { fetchAiRiskAnalysis, AiRiskAnalysisResponse } from '../api/riskApi';

interface Props {
  district: District | null;
  onClose: () => void;
}

const SKY: Record<number, { label: string; icon: string }> = {
  1: { label: '맑음',    icon: '☀️' },
  3: { label: '구름많음', icon: '⛅' },
  4: { label: '흐림',    icon: '☁️' },
};

const PTY: Record<number, { label: string; icon: string }> = {
  0: { label: '없음',         icon: '—' },
  1: { label: '비',           icon: '🌧️' },
  2: { label: '비/눈',        icon: '🌨️' },
  3: { label: '눈',           icon: '❄️' },
  5: { label: '빗방울',       icon: '🌦️' },
  6: { label: '빗방울+눈날림', icon: '🌦️' },
  7: { label: '눈날림',       icon: '🌨️' },
};


export default function DetailPanel({ district, onClose }: Props) {
  const [aiData, setAiData] = useState<AiRiskAnalysisResponse | null>(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState(false);

  useEffect(() => {
    if (!district) return;
    setAiData(null);
    setAiError(false);
    setAiLoading(true);
    fetchAiRiskAnalysis(district.districtCode)
      .then(setAiData)
      .catch(() => setAiError(true))
      .finally(() => setAiLoading(false));
  }, [district?.name]);

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
                  { icon: '🌡️', label: '온도',   value: `${district.temperature}°C` },
                  { icon: '💧', label: '습도',   value: `${district.humidity}%` },
                  {
                    icon: (SKY[district.sky] ?? SKY[1]).icon,
                    label: '하늘상태',
                    value: (SKY[district.sky] ?? SKY[1]).label,
                  },
                  ...(district.precipitationType !== 0 ? [{
                    icon: (PTY[district.precipitationType] ?? PTY[0]).icon,
                    label: '강수형태',
                    value: (PTY[district.precipitationType] ?? PTY[0]).label,
                  }] : []),
                  { icon: '💨', label: '풍속',   value: `${district.windSpeed.toFixed(1)} m/s` },
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

            {/* AI 위험 분석 */}
            <section>
              <div className="flex items-center gap-1.5 mb-2.5">
                <span className="text-amber-500">✨</span>
                <p className="text-xs font-semibold text-amber-600 uppercase tracking-wide">
                  AI 위험 분석
                </p>
              </div>

              {aiLoading && (
                <div className="flex items-center gap-2 text-amber-600 text-sm px-1">
                  <div className="w-4 h-4 border-2 border-amber-300 border-t-amber-600 rounded-full animate-spin" />
                  <span>AI 분석 중...</span>
                </div>
              )}
              {aiError && !aiLoading && (
                <p className="text-sm text-slate-400 px-1">AI 분석을 불러오지 못했습니다.</p>
              )}
              {aiData && !aiLoading && (
                <div className="space-y-3">
                  {/* 본문 */}
                  <p className="text-slate-700 text-sm leading-relaxed">
                    {aiData.summary}
                  </p>

                  {/* 안심 메시지 */}
                  <div className="flex items-start gap-2.5 bg-emerald-50 border border-emerald-200 rounded-xl px-4 py-3">
                    <span className="text-base flex-shrink-0">😊</span>
                    <p className="text-sm font-medium text-emerald-800 leading-relaxed">
                      {aiData.comfortMessage}
                    </p>
                  </div>

                  {/* 시간대별 조언 */}
                  <div className="flex items-start gap-2.5 bg-amber-50 border border-amber-200 rounded-xl px-4 py-3">
                    <span className="text-base flex-shrink-0">⏰</span>
                    <p className="text-sm font-medium text-amber-800 leading-relaxed">
                      {aiData.timeAdvice}
                    </p>
                  </div>

                  {/* 행동 리스트 */}
                  <div className="space-y-2">
                    {aiData.actionGuides.map((guide, i) => (
                      <div
                        key={i}
                        className="flex items-start gap-2.5 px-4 py-2.5 rounded-xl border bg-slate-50 border-slate-100"
                      >
                        <span className="text-xs font-bold text-slate-400 flex-shrink-0 mt-0.5">
                          {i + 1}
                        </span>
                        <span className="text-sm text-slate-700">{guide}</span>
                      </div>
                    ))}
                  </div>

                  <div className="space-y-2">
                    <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide">
                      핵심 요인
                    </p>
                    {aiData.riskFactors.map((factor, i) => (
                      <div
                        key={i}
                        className="flex items-start gap-2.5 px-4 py-2.5 rounded-xl border bg-red-50 border-red-100"
                      >
                        <span className="text-xs font-bold text-red-400 flex-shrink-0 mt-0.5">
                          {i + 1}
                        </span>
                        <span className="text-sm text-red-800">{factor}</span>
                      </div>
                    ))}
                  </div>

                  <div className="space-y-2">
                    <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide">
                      분석 근거
                    </p>
                    <div className="grid grid-cols-2 gap-2">
                      {[
                        { label: '위험도', value: `${aiData.basedOn.riskPercent}%` },
                        { label: '온도', value: `${aiData.basedOn.temperature}°C` },
                        { label: '습도', value: `${aiData.basedOn.humidity}%` },
                        { label: '조도', value: `${aiData.basedOn.illumination} lux` },
                        { label: '풍속', value: `${aiData.basedOn.windSpeedMph} mph` },
                        { label: '기상지수', value: aiData.basedOn.weatherIndex },
                        { label: '서식지계수', value: aiData.basedOn.habitatFactor },
                        { label: '교통계수', value: aiData.basedOn.trafficFactor },
                        { label: 'LORR', value: aiData.basedOn.riskIndex },
                      ].map(({ label, value }) => (
                        <div
                          key={label}
                          className="bg-slate-50 rounded-xl px-3 py-2 border border-slate-100"
                        >
                          <p className="text-[11px] text-slate-400 mb-1">{label}</p>
                          <p className="text-sm font-semibold text-slate-800">{value}</p>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </section>

            {/* 하단 여백 */}
            <div className="h-2" />
          </div>
        </>
      )}
    </div>
  );
}
