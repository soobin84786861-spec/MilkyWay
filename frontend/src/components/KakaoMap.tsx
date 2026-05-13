import { useEffect, useRef, useState } from 'react';
import seoulBoundaries from '../data/seoulBoundaries.json';
import { District, PublicCctv } from '../types';
import { RISK_COLOR } from '../utils/riskUtils';

declare global {
  interface Window {
    kakao: any;
  }
}

interface Props {
  districts: District[];
  cctvs: PublicCctv[];
  cctvEnabled: boolean;
  selectedDistrict: District | null;
  onDistrictClick: (d: District) => void;
  onCctvStreamOpen: (cctv: PublicCctv, streamUrl: string) => void;
}

type MapStatus = 'loading' | 'ready' | 'error';

type CctvMarkerEntry = {
  cctv: PublicCctv;
  overlay: any;
  element: HTMLDivElement;
};

const CCTV_ICON_SVG = encodeURIComponent(`
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120">
    <rect x="18" y="12" width="84" height="20" rx="6" fill="#f2da74" stroke="#1f1720" stroke-width="4"/>
    <path d="M26 32h68c0 0 3 12 3 24 0 28-18 47-37 47S23 84 23 56c0-12 3-24 3-24Z" fill="#8ea1aa" stroke="#1f1720" stroke-width="4"/>
    <path d="M34 40h52" stroke="#1f1720" stroke-width="4" stroke-linecap="round"/>
    <path d="M33 47h54" stroke="#1f1720" stroke-width="3" stroke-linecap="round" opacity="0.8"/>
    <rect x="47" y="71" width="26" height="28" rx="7" fill="#d9b8b0" stroke="#1f1720" stroke-width="4"/>
    <circle cx="60" cy="84" r="12" fill="#d8f6f3" stroke="#1f1720" stroke-width="4"/>
    <circle cx="63" cy="81" r="5" fill="#ffffff" opacity="0.95"/>
    <circle cx="61" cy="83" r="3" fill="#7cc8c3"/>
  </svg>
`);
const CCTV_ICON_URL = `data:image/svg+xml;charset=utf-8,${CCTV_ICON_SVG}`;

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

function ensureCctvMarkerStyles() {
  if (document.getElementById('cctv-marker-style')) return;

  const style = document.createElement('style');
  style.id = 'cctv-marker-style';
  style.textContent = `
    @keyframes cctv-float-a {
      0%, 100% { transform: translateY(0px) rotate(-2deg); }
      50% { transform: translateY(-5px) rotate(2deg); }
    }
    @keyframes cctv-float-b {
      0%, 100% { transform: translateY(-1px) rotate(1deg); }
      50% { transform: translateY(-7px) rotate(-2deg); }
    }
    @keyframes cctv-float-c {
      0%, 100% { transform: translateY(0px) rotate(2deg); }
      50% { transform: translateY(-6px) rotate(-1deg); }
    }
    .cctv-float-a { animation: cctv-float-a 2.8s ease-in-out infinite; }
    .cctv-float-b { animation: cctv-float-b 3.3s ease-in-out infinite; }
    .cctv-float-c { animation: cctv-float-c 3.0s ease-in-out infinite; }
  `;

  document.head.appendChild(style);
}

function getCctvMarkerSize(level: number): number {
  if (level <= 7) return 34;
  if (level === 8) return 28;
  return 24;
}

export default function KakaoMap({
  districts,
  cctvs,
  cctvEnabled,
  selectedDistrict,
  onDistrictClick,
  onCctvStreamOpen,
}: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<any>(null);
  const overlaysRef = useRef<Map<string, any>>(new Map());
  const polygonsRef = useRef<any[]>([]);
  const cctvMarkersRef = useRef<CctvMarkerEntry[]>([]);
  const cctvPopupRef = useRef<any>(null);
  const [status, setStatus] = useState<MapStatus>('loading');

  useEffect(() => {
    if (!window.kakao || !window.kakao.maps) {
      console.error('[KakaoMap] window.kakao is missing');
      setStatus('error');
      return;
    }

    window.kakao.maps.load(() => {
      const container = containerRef.current;
      if (!container) return;

      ensureCctvMarkerStyles();

      const seoulCenter = new window.kakao.maps.LatLng(37.5665, 126.978);
      const seoulBounds = new window.kakao.maps.LatLngBounds(
        new window.kakao.maps.LatLng(37.413, 126.734),
        new window.kakao.maps.LatLng(37.701, 127.269),
      );

      mapRef.current = new window.kakao.maps.Map(container, {
        center: seoulCenter,
        level: 8,
      });

      mapRef.current.setMinLevel(7);
      mapRef.current.setMaxLevel(9);
      scheduleMapRelayout();

      window.kakao.maps.event.addListener(mapRef.current, 'dragend', () => {
        const center = mapRef.current.getCenter();
        if (!seoulBounds.contain(center)) {
          mapRef.current.setCenter(seoulCenter);
        }
      });

      window.kakao.maps.event.addListener(mapRef.current, 'zoom_changed', () => {
        updateCctvMarkerSizes();
      });

      renderPolygons(districts);
      renderOverlays(districts);
      setStatus('ready');
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!mapRef.current || !containerRef.current) return;

    const handleResize = () => scheduleMapRelayout();
    window.addEventListener('resize', handleResize);

    const resizeObserver =
      typeof ResizeObserver !== 'undefined'
        ? new ResizeObserver(() => {
            scheduleMapRelayout();
          })
        : null;

    if (resizeObserver) {
      resizeObserver.observe(containerRef.current);
    }

    return () => {
      window.removeEventListener('resize', handleResize);
      resizeObserver?.disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  useEffect(() => {
    if (!mapRef.current) return;
    clearDistrictOverlays();
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
  }, [districts, selectedDistrict]);

  useEffect(() => {
    if (!mapRef.current) return;
    clearCctvMarkers();
    if (cctvEnabled) {
      renderCctvMarkers(cctvs);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cctvEnabled, cctvs]);

  function clearDistrictOverlays() {
    overlaysRef.current.forEach((overlay) => overlay.setMap(null));
    overlaysRef.current.clear();
    polygonsRef.current.forEach((polygon) => polygon.setMap(null));
    polygonsRef.current = [];
  }

  function clearCctvMarkers() {
    cctvMarkersRef.current.forEach(({ overlay }) => overlay.setMap(null));
    cctvMarkersRef.current = [];
    if (cctvPopupRef.current) {
      cctvPopupRef.current.setMap(null);
      cctvPopupRef.current = null;
    }
  }

  function renderPolygons(districtList: District[]) {
    if (!mapRef.current || !window.kakao) return;

    districtList.forEach((district) => {
      const coords = boundaries[district.districtCode];
      if (!coords) return;

      const path = coords.map(({ lat, lng }) => new window.kakao.maps.LatLng(lat, lng));
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
        const element = overlay.getContent();
        if (element && typeof element !== 'string') {
          (element as HTMLElement).addEventListener('click', () => onDistrictClick(district));
        }
      }, 0);

      overlaysRef.current.set(district.id, overlay);
    });
  }

  function createCctvMarkerContent(cctv: PublicCctv, level: number): string {
    const size = getCctvMarkerSize(level);
    const variant = ['a', 'b', 'c'][
      Math.abs(cctv.cctvId.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0)) % 3
    ];

    return `
      <div style="
        width:${size + 14}px;height:${size + 18}px;
        display:flex;align-items:flex-start;justify-content:center;
        cursor:pointer;
      ">
        <div class="cctv-float-${variant}" style="
          width:${size}px;height:${size}px;
          display:flex;align-items:center;justify-content:center;
          filter:drop-shadow(0 8px 12px rgba(15,23,42,0.18));
          transform-origin:center bottom;
        ">
          <img
            src="${CCTV_ICON_URL}"
            alt="CCTV"
            style="width:${size}px;height:${size}px;display:block;"
          />
        </div>
      </div>
    `;
  }

  function createCctvMarkerElement(cctv: PublicCctv, level: number): HTMLDivElement {
    const wrapper = document.createElement('div');
    wrapper.innerHTML = createCctvMarkerContent(cctv, level).trim();
    const element = wrapper.firstElementChild as HTMLDivElement;
    element.addEventListener('click', () => openCctvPopup(cctv));
    return element;
  }

  function createCctvPopupContent(cctv: PublicCctv): string {
    return `
      <div style="
        width:220px;background:#fff;border-radius:16px;padding:14px 14px 12px 14px;
        box-shadow:0 14px 36px rgba(15,23,42,0.25);border:1px solid rgba(148,163,184,0.2);
      ">
        <div style="font-size:14px;font-weight:700;color:#0f172a;line-height:1.45;">${cctv.name}</div>
        <div style="margin-top:10px;display:flex;gap:8px;">
          <button data-role="open-stream" style="
            flex:1;border:none;border-radius:10px;padding:9px 10px;background:#0f172a;color:#fff;
            font-size:12px;font-weight:700;cursor:pointer;
          ">\uC601\uC0C1 \uBCF4\uAE30</button>
          <button data-role="close-popup" style="
            border:none;border-radius:10px;padding:9px 10px;background:#e2e8f0;color:#334155;
            font-size:12px;font-weight:700;cursor:pointer;
          ">\uB2EB\uAE30</button>
        </div>
      </div>
    `;
  }

  function createCctvPopupElement(cctv: PublicCctv, popup: any): HTMLDivElement {
    const wrapper = document.createElement('div');
    wrapper.innerHTML = createCctvPopupContent(cctv).trim();
    const element = wrapper.firstElementChild as HTMLDivElement;

    element.querySelector('[data-role="open-stream"]')?.addEventListener('click', (event) => {
      event.stopPropagation();
      const streamUrl = buildCctvStreamUrl(cctv);
      onCctvStreamOpen(cctv, streamUrl);
    });

    element.querySelector('[data-role="close-popup"]')?.addEventListener('click', (event) => {
      event.stopPropagation();
      popup.setMap(null);
      if (cctvPopupRef.current === popup) {
        cctvPopupRef.current = null;
      }
    });

    return element;
  }

  function buildCctvStreamUrl(cctv: PublicCctv): string {
    if (!mapRef.current || !window.kakao) {
      return cctv.streamUrl;
    }

    const bounds = mapRef.current.getBounds();
    const southWest = bounds.getSouthWest();
    const northEast = bounds.getNorthEast();
    const url = new URL(cctv.streamUrl, window.location.origin);

    url.searchParams.set('minX', String(southWest.getLng()));
    url.searchParams.set('minY', String(southWest.getLat()));
    url.searchParams.set('maxX', String(northEast.getLng()));
    url.searchParams.set('maxY', String(northEast.getLat()));

    return url.toString();
  }

  function updateCctvMarkerSizes() {
    if (!mapRef.current) return;

    const level = mapRef.current.getLevel();
    cctvMarkersRef.current.forEach((entry) => {
      const element = createCctvMarkerElement(entry.cctv, level);
      entry.element = element;
      entry.overlay.setContent(element);
    });
  }

  function renderCctvMarkers(cctvList: PublicCctv[]) {
    if (!mapRef.current || !window.kakao) return;

    const level = mapRef.current.getLevel();

    cctvList.forEach((cctv) => {
      const element = createCctvMarkerElement(cctv, level);
      const overlay = new window.kakao.maps.CustomOverlay({
        position: new window.kakao.maps.LatLng(cctv.latitude, cctv.longitude),
        content: element,
        yAnchor: 0.9,
        xAnchor: 0.5,
      });
      overlay.setMap(mapRef.current);
      cctvMarkersRef.current.push({ cctv, overlay, element });
    });
  }

  function openCctvPopup(cctv: PublicCctv) {
    if (!mapRef.current || !window.kakao) return;

    if (cctvPopupRef.current) {
      cctvPopupRef.current.setMap(null);
    }

    const popup = new window.kakao.maps.CustomOverlay({
      position: new window.kakao.maps.LatLng(cctv.latitude, cctv.longitude),
      content: '',
      yAnchor: 1.15,
      xAnchor: 0.5,
    });
    popup.setContent(createCctvPopupElement(cctv, popup));
    popup.setMap(mapRef.current);
    cctvPopupRef.current = popup;
  }

  function scheduleMapRelayout() {
    if (!mapRef.current) return;

    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(() => {
        if (!mapRef.current) return;
        const center = mapRef.current.getCenter();
        mapRef.current.relayout();
        mapRef.current.setCenter(center);
      });
    });
  }

  return (
    <div className="relative w-full h-full bg-slate-100">
      <div ref={containerRef} id="kakao-map" className="w-full h-full" />

      {status === 'loading' && (
        <div className="absolute inset-0 flex items-center justify-center bg-slate-100/80">
          <div className="flex flex-col items-center gap-3">
            <div className="w-8 h-8 border-4 border-slate-300 border-t-red-500 rounded-full animate-spin" />
            <p className="text-sm text-slate-500 font-medium">
              {'\uC9C0\uB3C4 \uBD88\uB7EC\uC624\uB294 \uC911...'}
            </p>
          </div>
        </div>
      )}

      {status === 'error' && (
        <div className="absolute inset-0 flex items-center justify-center bg-slate-100">
          <div className="bg-white rounded-2xl shadow-lg p-8 text-center max-w-sm">
            <p className="text-3xl mb-3">!</p>
            <p className="font-bold text-slate-800 mb-2">
              {'\uCE74\uCE74\uC624\uB9F5 \uB85C\uB4DC \uC2E4\uD328'}
            </p>
            <div className="text-sm text-slate-500 text-left space-y-1.5">
              <p>{'\uCE74\uCE74\uC624 \uCF58\uC194\uC5D0\uC11C \uC571 \uC124\uC815\uC758 Web \uD50C\uB7AB\uD3FC\uC744 \uD655\uC778\uD574\uC8FC\uC138\uC694.'}</p>
              <p>{'\uB3C4\uBA54\uC778 \uB4F1\uB85D\uC5D0 \uB2E4\uC74C \uC8FC\uC18C\uAC00 \uD3EC\uD568\uB418\uC5B4\uC57C \uD569\uB2C8\uB2E4.'}</p>
              <code className="block bg-slate-100 rounded px-2 py-1 text-xs mt-1">
                http://localhost:5173
              </code>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
