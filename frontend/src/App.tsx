import { useState, useMemo, useEffect } from 'react';
import { District, RiskFilterType } from './types';
import { mockDistricts } from './data/mockData';
import { fetchRegions, RegionRiskResponse } from './api/riskApi';
import TopNav from './components/TopNav';
import KakaoMap from './components/KakaoMap';
import DetailPanel from './components/DetailPanel';
import Top5Panel from './components/Top5Panel';
import Legend from './components/Legend';

/** API 응답 + mockData 를 병합해 District 생성 */
function mergeWithMock(api: RegionRiskResponse): District {
  const mock = mockDistricts.find((m) => m.name === api.regionName);
  return {
    ...(mock ?? mockDistricts[0]),   // 상세 필드는 mock 에서 채움
    name: api.regionName,
    riskLevel: api.riskLevel,
    riskScore: api.riskPercent,
    probability: api.riskPercent,
    lat: api.latitude,
    lng: api.longitude,
    instaCnt: api.instaCnt,
  };
}

export default function App() {
  const [districts, setDistricts] = useState<District[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedDistrict, setSelectedDistrict] = useState<District | null>(null);
  const [riskFilter, setRiskFilter] = useState<RiskFilterType>('전체');

  // riskFilter 변경 시 API 재호출
  useEffect(() => {
    setLoading(true);
    setError(null);
    fetchRegions(riskFilter)
      .then((data) => setDistricts(data.map(mergeWithMock)))
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [riskFilter]);

  // 선택된 지역이 필터로 사라지면 패널 닫기
  useEffect(() => {
    if (selectedDistrict && !districts.find((d) => d.id === selectedDistrict.id)) {
      setSelectedDistrict(null);
    }
  }, [districts, selectedDistrict]);

  const top5Districts = useMemo(
    () => [...districts].sort((a, b) => b.probability - a.probability).slice(0, 5),
    [districts]
  );

  function handleDistrictClick(district: District) {
    setSelectedDistrict((prev) => (prev?.id === district.id ? null : district));
  }

  return (
    <div className="h-screen flex flex-col bg-slate-100 overflow-hidden">
      <TopNav
        riskFilter={riskFilter}
        onRiskFilterChange={setRiskFilter}
      />

      <div className="flex-1 relative overflow-hidden">
        {/* 로딩 스피너 (지도 위에 오버레이) */}
        {loading && (
          <div className="absolute inset-0 z-40 flex items-center justify-center bg-slate-100/70 backdrop-blur-sm">
            <div className="flex flex-col items-center gap-3">
              <div className="w-9 h-9 border-4 border-slate-200 border-t-red-500 rounded-full animate-spin" />
              <p className="text-sm text-slate-500 font-medium">데이터 불러오는 중...</p>
            </div>
          </div>
        )}

        {/* API 에러 배너 */}
        {error && !loading && (
          <div className="absolute top-4 left-1/2 -translate-x-1/2 z-40 bg-red-50 border border-red-200 text-red-700 text-sm font-medium px-5 py-2.5 rounded-xl shadow-md flex items-center gap-2">
            <span>⚠️</span>
            <span>API 연결 실패: {error}</span>
            <button
              onClick={() => setRiskFilter(riskFilter)} // 재시도 트리거
              className="ml-2 underline text-red-600 hover:text-red-800"
            >
              재시도
            </button>
          </div>
        )}

        <KakaoMap
          districts={districts}
          selectedDistrict={selectedDistrict}
          onDistrictClick={handleDistrictClick}
        />

        <Legend />

        <Top5Panel
          districts={top5Districts}
          onDistrictClick={handleDistrictClick}
          visible={!selectedDistrict}
        />

        <DetailPanel district={selectedDistrict} onClose={() => setSelectedDistrict(null)} />
      </div>
    </div>
  );
}