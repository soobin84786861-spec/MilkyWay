export interface PublicCctvResponse {
  name: string;
  latitude: number;
  longitude: number;
  cctvId: string;
  streamUrl: string;
}

export async function fetchCctvs(): Promise<PublicCctvResponse[]> {
  const res = await fetch('/api/cctv');
  if (!res.ok) throw new Error(`CCTV API ${res.status}`);
  return res.json();
}
