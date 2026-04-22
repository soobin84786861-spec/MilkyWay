import { useEffect, useRef, useState } from 'react';
import { District } from '../types';
import { RISK_COLOR } from '../utils/riskUtils';
import seoulBoundaries from '../data/seoulBoundaries.json';

declare global {
  interface Window {
    kakao: any;
  }
}

interface Props {
  districts: District[];
  selectedDistrict: District | null;
  onDistrictClick: (d: District) => void;
}

type MapStatus = 'loading' | 'ready' | 'error';

function createMarkerContent(district: District, isSelected: boolean): string {
  const bg = RISK_COLOR[district.riskLevel];
  const selectedStyle = isSelected
    ? 'transform:scale(1.15);box-shadow:0 0 0 4px rgba(255,255,255,0.5),0 8px 24px rgba(0,0,0,0.4);'
    : '';
  return `
    <div style="
      display:flex;flex-direction:column;align-items:center;justify-content:center;
      width:72px;height:72px;border-radius:50%;
      background-color:${bg};
      border:3px solid rgba(255,255,255,0.85);
      box-shadow:0 4px 14px rgba(0,0,0,0.25);
      cursor:pointer;
      transition:transform 0.15s ease,box-shadow 0.15s ease;
      ${selectedStyle}
    ">
      <span style="font-size:11px;font-weight:700;color:#fff;text-shadow:0 1px 2px rgba(0,0,0,0.3);line-height:1.3;">${district.name}</span>
      <span style="font-size:13px;font-weight:800;color:#fff;text-shadow:0 1px 2px rgba(0,0,0,0.3);">${district.probability}%</span>
    </div>
  `;
}

const boundaries = seoulBoundaries as Record<string, { lat: number; lng: number }[]>;

export default function KakaoMap({ districts, selectedDistrict, onDistrictClick }: Props) {
  const mapRef = useRef<any>(null);
  const overlaysRef = useRef<Map<string, any>>(new Map());
  const polygonsRef = useRef<any[]>([]);
  const [status, setStatus] = useState<MapStatus>('loading');

  useEffect(() => {
    if (!window.kakao || !window.kakao.maps) {
      console.error('[KakaoMap] window.kakao 없음 — index.html 스크립트 로드 실패 또는 403/401');
      setStatus('error');
      return;
    }

    window.kakao.maps.load(() => {
      const container = document.getElementById('kakao-map');
      if (!container) return;

      const SEOUL_CENTER = new window.kakao.maps.LatLng(37.5665, 126.978);
      const SEOUL_BOUNDS = new window.kakao.maps.LatLngBounds(
        new window.kakao.maps.LatLng(37.413, 126.734),
        new window.kakao.maps.LatLng(37.701, 127.269),
      );

      mapRef.current = new window.kakao.maps.Map(container, {
        center: SEOUL_CENTER,
        level: 8,
      });

      mapRef.current.setMinLevel(7);
      mapRef.current.setMaxLevel(9);

      window.kakao.maps.event.addListener(mapRef.current, 'dragend', () => {
        const center = mapRef.current.getCenter();
        if (!SEOUL_BOUNDS.contain(center)) {
          mapRef.current.setCenter(SEOUL_CENTER);
        }
      });

      renderPolygons(districts);
      renderOverlays(districts);
      setStatus('ready');
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!mapRef.current) return;
    clearOverlays();
    renderPolygons(districts);
    renderOverlays(districts);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [districts]);

  useEffect(() => {
    if (!mapRef.current) return;
    overlaysRef.current.forEach((overlay, id) => {
      const district = districts.find((d) => d.id === id);
      if (!district) return;
      overlay.setContent(createMarkerContent(district, selectedDistrict?.id === id));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedDistrict]);

  function clearOverlays() {
    overlaysRef.current.forEach((o) => o.setMap(null));
    overlaysRef.current.clear();
    polygonsRef.current.forEach((p) => p.setMap(null));
    polygonsRef.current = [];
  }

  function renderPolygons(districtList: District[]) {
    if (!mapRef.current || !window.kakao) return;

    districtList.forEach((district) => {
      const coords = boundaries[district.districtCode];
      if (!coords) return;

      const path = coords.map(
        ({ lat, lng }) => new window.kakao.maps.LatLng(lat, lng)
      );
      const color = RISK_COLOR[district.riskLevel];

      const polygon = new window.kakao.maps.Polygon({
        map: mapRef.current,
        path,
        strokeWeight: 1.5,
        strokeColor: color,
        strokeOpacity: 0.7,
        fillColor: color,
        fillOpacity: 0.12,
      });

      polygonsRef.current.push(polygon);
    });
  }

  function renderOverlays(districtList: District[]) {
    if (!mapRef.current || !window.kakao) return;

    districtList.forEach((district) => {
      const overlay = new window.kakao.maps.CustomOverlay({
        position: new window.kakao.maps.LatLng(district.lat, district.lng),
        content: createMarkerContent(district, false),
        yAnchor: 0.5,
        xAnchor: 0.5,
      });
      overlay.setMap(mapRef.current);

      setTimeout(() => {
        const el = overlay.getContent();
        if (el && typeof el !== 'string') {
          (el as HTMLElement).addEventListener('click', () => onDistrictClick(district));
        }
      }, 0);

      overlaysRef.current.set(district.id, overlay);
    });
  }

  return (
    <div className="relative w-full h-full bg-slate-100">
      <div id="kakao-map" className="w-full h-full" />

      {status === 'loading' && (
        <div className="absolute inset-0 flex items-center justify-center bg-slate-100/80">
          <div className="flex flex-col items-center gap-3">
            <div className="w-8 h-8 border-4 border-slate-300 border-t-red-500 rounded-full animate-spin" />
            <p className="text-sm text-slate-500 font-medium">지도 불러오는 중...</p>
          </div>
        </div>
      )}

      {status === 'error' && (
        <div className="absolute inset-0 flex items-center justify-center bg-slate-100">
          <div className="bg-white rounded-2xl shadow-lg p-8 text-center max-w-sm">
            <p className="text-3xl mb-3">⚠️</p>
            <p className="font-bold text-slate-800 mb-2">카카오맵 로드 실패</p>
            <div className="text-sm text-slate-500 text-left space-y-1.5">
              <p>• 카카오 콘솔 → 앱 설정 → 플랫폼 → Web</p>
              <p>• 사이트 도메인에 추가 후 저장:</p>
              <code className="block bg-slate-100 rounded px-2 py-1 text-xs mt-1">
                http://localhost:5173
              </code>
              <p className="mt-2">• <strong>JavaScript 앱 키</strong> 사용 여부 확인</p>
              <p>• 저장 후 Vite 재시작 필요</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
