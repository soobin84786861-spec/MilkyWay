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
  }, [riskFilter]);

  useEffect(() => {
    if (selectedDistrict && !districts.find((d) => d.id === selectedDistrict.id)) {
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
    () => [...districts].sort((a, b) => b.probability - a.probability).slice(0, 5),
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
    <div className="h-screen flex flex-col bg-slate-100 overflow-hidden">
      <TopNav
        riskFilter={riskFilter}
        onRiskFilterChange={setRiskFilter}
        cctvEnabled={cctvEnabled}
        onCctvToggle={() => setCctvEnabled((prev) => !prev)}
      />

      <div className="flex-1 relative overflow-hidden">
        {loading && (
          <div className="absolute inset-0 z-40 flex items-center justify-center bg-slate-100/70 backdrop-blur-sm">
            <div className="flex flex-col items-center gap-3">
              <div className="w-9 h-9 border-4 border-slate-200 border-t-red-500 rounded-full animate-spin" />
              <p className="text-sm text-slate-500 font-medium">
                {'데이터 불러오는 중...'}
              </p>
            </div>
          </div>
        )}

        {error && !loading && (
          <div className="absolute top-4 left-1/2 -translate-x-1/2 z-40 bg-red-50 border border-red-200 text-red-700 text-sm font-medium px-5 py-2.5 rounded-xl shadow-md flex items-center gap-2">
            <span>{'오류'}</span>
            <span>{`API 연결 실패: ${error}`}</span>
            <button
              onClick={() => setRiskFilter((prev) => prev)}
              className="ml-2 underline text-red-600 hover:text-red-800"
            >
              {'다시 시도'}
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
          <div className="absolute inset-0 z-50 flex items-center justify-center bg-slate-950/45 backdrop-blur-[2px] p-6">
            <div className="w-full max-w-4xl h-[80vh] flex flex-col rounded-[24px] bg-white shadow-2xl border border-slate-200 overflow-hidden">
              <div className="flex shrink-0 items-center justify-between px-5 py-3 border-b border-slate-100 bg-white">
                <div className="min-w-0">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">
                    CCTV Viewer
                  </p>
                  <h2 className="truncate text-lg font-bold text-slate-800">
                    {cctvViewer.cctv.name}
                  </h2>
                </div>

                <button
                  type="button"
                  onClick={() => setCctvViewer(null)}
                  className="rounded-full bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-700"
                >
                  {'닫기'}
                </button>
              </div>

              <div className="relative flex-1 overflow-hidden bg-black">
                <div
                  className="absolute inset-0"
                  style={{
                    transform: `scale(${cctvScale})`,
                    transformOrigin: 'top center',
                  }}
                >
                  <iframe
                    key={cctvViewer.streamUrl}
                    src={cctvViewer.streamUrl}
                    title={`${cctvViewer.cctv.name} CCTV`}
                    className="w-full h-full bg-black"
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
