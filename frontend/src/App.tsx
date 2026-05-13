import { useEffect, useMemo, useState } from 'react';
import { fetchCctvs } from './api/cctvApi';
import { fetchRegions, RegionRiskResponse } from './api/riskApi';
import KakaoMap from './components/KakaoMap';
import DetailPanel from './components/DetailPanel';
import Legend from './components/Legend';
import Top5Panel from './components/Top5Panel';
import TopNav from './components/TopNav';
import { mockDistricts } from './data/mockData';
import { District, PublicCctv, RiskFilterType } from './types';

type CctvViewerState = {
  cctv: PublicCctv;
  streamUrl: string;
};

function mergeWithMock(api: RegionRiskResponse): District {
  const mock = mockDistricts.find((m) => m.name === api.regionName);
  return {
    ...(mock ?? mockDistricts[0]),
    districtCode: api.districtCode,
    name: api.regionName,
    riskLevel: api.riskLevel,
    riskScore: api.riskPercent,
    probability: api.riskPercent,
    lat: api.latitude,
    lng: api.longitude,
    instaCnt: api.instaCnt,
    temperature: api.temperature,
    humidity: api.humidity,
    sky: api.sky,
    precipitationType: api.precipitationType,
    windSpeed: api.windSpeed,
  };
}

const CCTV_SCALE_DEFAULT = 2.5;

export default function App() {
  const [districts, setDistricts] = useState<District[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedDistrict, setSelectedDistrict] = useState<District | null>(null);
  const [riskFilter, setRiskFilter] = useState<RiskFilterType>('전체');
  const [riskRefreshKey, setRiskRefreshKey] = useState(0);
  const [cctvEnabled, setCctvEnabled] = useState(false);
  const [cctvs, setCctvs] = useState<PublicCctv[]>([]);
  const [cctvLoaded, setCctvLoaded] = useState(false);
  const [cctvViewer, setCctvViewer] = useState<CctvViewerState | null>(null);
  const [cctvScale, setCctvScale] = useState(CCTV_SCALE_DEFAULT);

  useEffect(() => {
    setLoading(true);
    setError(null);
    fetchRegions(riskFilter)
      .then((data) => setDistricts(data.map(mergeWithMock)))
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [riskFilter, riskRefreshKey]);

  useEffect(() => {
    if (selectedDistrict && !districts.find((district) => district.id === selectedDistrict.id)) {
      setSelectedDistrict(null);
    }
  }, [districts, selectedDistrict]);

  useEffect(() => {
    if (!cctvEnabled || cctvLoaded) return;

    fetchCctvs()
      .then((data) => {
        setCctvs(data);
        setCctvLoaded(true);
      })
      .catch((e: Error) => setError(e.message));
  }, [cctvEnabled, cctvLoaded]);

  const top5Districts = useMemo(
    () =>
      districts
        .filter((district) => district.riskLevel !== 'SAFE')
        .sort((a, b) => b.probability - a.probability)
        .slice(0, 5),
    [districts]
  );

  function handleDistrictClick(district: District) {
    setSelectedDistrict((prev) => (prev?.id === district.id ? null : district));
  }

  function handleCctvStreamOpen(cctv: PublicCctv, streamUrl: string) {
    setCctvViewer({ cctv, streamUrl });
    setCctvScale(CCTV_SCALE_DEFAULT);
  }

  return (
    <div className="flex h-dvh min-h-0 flex-col overflow-hidden bg-slate-100">
      <header className="sr-only">
        <h1>서울 러브버그 위험도 지도</h1>
        <p>
          서울 자치구별 러브버그 출몰 위험도를 실시간으로 보여주고 CCTV, 기상 정보, AI 요약을 함께
          제공하는 서비스입니다.
        </p>
      </header>

      <TopNav
        riskFilter={riskFilter}
        onRiskFilterChange={setRiskFilter}
        cctvEnabled={cctvEnabled}
        onCctvToggle={() => setCctvEnabled((prev) => !prev)}
      />

      <div className="relative min-h-0 flex-1 overflow-hidden">
        {loading && (
          <div className="absolute inset-0 z-40 flex items-center justify-center bg-slate-100/70 backdrop-blur-sm">
            <div className="flex flex-col items-center gap-3">
              <div className="h-9 w-9 animate-spin rounded-full border-4 border-slate-200 border-t-red-500" />
              <p className="text-sm font-medium text-slate-500">데이터 불러오는 중...</p>
            </div>
          </div>
        )}

        {error && !loading && (
          <div className="absolute left-3 right-3 top-4 z-40 mx-auto flex max-w-xl items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 shadow-md sm:left-1/2 sm:right-auto sm:w-max sm:min-w-[340px] sm:-translate-x-1/2">
            <span>오류</span>
            <span className="min-w-0 flex-1 truncate">API 연결 실패: {error}</span>
            <button
              onClick={() => setRiskRefreshKey((prev) => prev + 1)}
              className="underline text-red-600 hover:text-red-800"
            >
              다시 시도
            </button>
          </div>
        )}

        <KakaoMap
          districts={districts}
          cctvs={cctvs}
          cctvEnabled={cctvEnabled}
          selectedDistrict={selectedDistrict}
          onDistrictClick={handleDistrictClick}
          onCctvStreamOpen={handleCctvStreamOpen}
        />

        <Legend />

        <Top5Panel
          districts={top5Districts}
          onDistrictClick={handleDistrictClick}
          visible={!selectedDistrict}
        />

        <DetailPanel district={selectedDistrict} onClose={() => setSelectedDistrict(null)} />

        {cctvViewer && (
          <div className="absolute inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-3 backdrop-blur-[2px] sm:p-6">
            <div className="flex h-[44dvh] w-full max-w-md flex-col overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-2xl sm:h-[72vh] sm:max-w-4xl sm:rounded-[24px]">
              <div className="flex shrink-0 items-center justify-between border-b border-slate-100 bg-white px-4 py-3 sm:px-5">
                <div className="min-w-0">
                  <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-400 sm:text-xs">
                    CCTV Viewer
                  </p>
                  <h2 className="truncate text-[15px] font-bold text-slate-800 sm:text-lg">
                    {cctvViewer.cctv.name}
                  </h2>
                </div>

                <button
                  type="button"
                  onClick={() => setCctvViewer(null)}
                  className="rounded-full bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-slate-700 sm:px-4 sm:py-2 sm:text-sm"
                >
                  닫기
                </button>
              </div>

              <div className="relative flex-1 overflow-hidden bg-black">
                <div
                  className="absolute inset-0"
                  style={{
                      transform: `translateY(-84px) scale(${cctvScale})`,
                      transformOrigin: 'top center',
                  }}
                >
                  <iframe
                    key={cctvViewer.streamUrl}
                    src={cctvViewer.streamUrl}
                    title={`${cctvViewer.cctv.name} CCTV`}
                    className="h-full w-full bg-black"
                    referrerPolicy="strict-origin-when-cross-origin"
                  />
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
