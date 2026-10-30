/**
 * 교류전 지도 — 우리 체육관 주변 격투기 체육관 검색(카카오 로컬 키워드 검색, 반경 1·2·3km).
 *
 * 카카오 운영정책: 받은 데이터는 사용자 경험 개선 목적으로만 캐시하고 최신으로 유지해야 하며,
 * 사전 승낙 없이 복제·제공할 수 없다 → Firestore 에 목록을 저장하지 않고 요청 때마다 조회,
 * 인스턴스 메모리에만 짧게 캐시([TtlCache]).
 *
 * 격투기 판정·대표 종목 규칙은 ops/connectors/kakao-local.mjs(isCombatGym) ·
 * ops/engine/leads-map.mjs(sportGroup) 와 같다 — 한쪽을 바꾸면 같이 바꿀 것.
 */

const KEYWORD_API = "https://dapi.kakao.com/v2/local/search/keyword.json";
/** 카카오 API 한계: 검색 1건당 최대 3페이지 × 15 = 45곳. */
const MAX_PAGES = 3;

export const NEARBY_KEYWORDS = ["주짓수", "종합격투기", "MMA", "복싱", "킥복싱", "무에타이", "격투기"];
export const ALLOWED_RADIUS_KM = [1, 2, 3];

/** 카카오 로컬 키워드 검색 응답의 장소 한 건(쓰는 필드만). */
export interface KakaoPlaceDoc {
  id: string;
  place_name: string;
  category_name?: string;
  road_address_name?: string;
  address_name?: string;
  x: string;
  y: string;
  distance?: string;
}

export interface NearbyPlace {
  placeId: string;
  name: string;
  /** 업종 끝 두 단계 (예: "복싱,권투 > 복싱,권투장"). */
  category: string;
  /** 대표 종목 — 0 복싱, 1 주짓수·유도, 2 MMA·킥복싱·무에타이. */
  sportGroup: number;
  district: string;
  address: string;
  /** 우리 체육관(검색 중심)에서의 거리(m). */
  distanceM: number;
  lat: number;
  lng: number;
}

export type FetchFn = (url: string, init: { headers: Record<string, string> }) => Promise<{
  ok: boolean;
  status: number;
  json: () => Promise<unknown>;
  text: () => Promise<string>;
}>;

const COMBAT_CATEGORY = /격투기|복싱|권투|주짓수|무에타이|킥복싱|유도|레슬링|종합격투/;
const EXCLUDED_CATEGORY = /태권도|합기도|검도/;
const COMBAT_NAME = /주짓수|주지츠|MMA|엠엠에이|종합격투|격투|복싱|킥복싱|무에타이|레슬링|유도|BJJ|파이트|fight|탑팀|top ?team/i;

/**
 * 격투기 체육관 판정: 업종이 격투 계열이면 통과, 일반 체육관·헬스·학원은 이름에 격투 단어가 있을 때만.
 * 태권도·합기도·검도 업종은 이름에 MMA 가 있어도 제외.
 */
export function isCombatGym(doc: KakaoPlaceDoc): boolean {
  const cat = doc.category_name || "";
  const name = doc.place_name || "";
  if (EXCLUDED_CATEGORY.test(cat)) return false;
  if (COMBAT_CATEGORY.test(cat)) return true;
  return /^(스포츠,레저|교육,학문)/.test(cat) && COMBAT_NAME.test(name);
}

/** 대표 종목 하나(이름 우선): MMA·종합격투 → 주짓수·유도 → 킥복싱·무에타이 → 복싱 → 그 외 격투기. */
export function sportGroup(name: string, category: string): number {
  if (/MMA|엠엠에이|종합격투|파이트|fight/i.test(name)) return 2;
  if (/주짓수|주지츠|BJJ|jiu|유도/i.test(name) || /주짓수|유도/.test(category)) return 1;
  if (/킥복싱|무에타이|muay|kick/i.test(name) || /무에타이/.test(category)) return 2;
  if (/복싱|권투/.test(category) || /복싱|boxing/i.test(name)) return 0;
  return 2;
}

function toNearbyPlace(doc: KakaoPlaceDoc): NearbyPlace | null {
  const lat = Number(doc.y);
  const lng = Number(doc.x);
  if (doc.x === "" || doc.y === "" || !Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  const address = doc.road_address_name || doc.address_name || "";
  const category = (doc.category_name || "").split(" > ").slice(-2).join(" > ");
  return {
    placeId: String(doc.id),
    name: doc.place_name,
    category,
    sportGroup: sportGroup(doc.place_name, category),
    district: (doc.address_name || address).split(" ")[1] || "",
    address,
    distanceM: Math.round(Number(doc.distance) || 0),
    lat,
    lng,
  };
}

/** 키워드별 결과 합치기 — 격투기만, 장소 id 중복 제거, [excludePlaceId](우리 체육관) 제외, 가까운 순. */
export function mergePlaces(docs: KakaoPlaceDoc[], excludePlaceId?: string | null): NearbyPlace[] {
  const byId = new Map<string, NearbyPlace>();
  for (const d of docs) {
    const id = String(d.id);
    if (byId.has(id) || id === excludePlaceId || !isCombatGym(d)) continue;
    const p = toNearbyPlace(d);
    if (p) byId.set(id, p);
  }
  return [...byId.values()].sort((a, b) => a.distanceM - b.distanceM);
}

async function searchKeyword(args: {
  query: string; lat: number; lng: number; radiusM: number; key: string; fetchFn: FetchFn;
}): Promise<KakaoPlaceDoc[]> {
  const docs: KakaoPlaceDoc[] = [];
  for (let page = 1; page <= MAX_PAGES; page++) {
    const url = `${KEYWORD_API}?query=${encodeURIComponent(args.query)}&x=${args.lng}&y=${args.lat}` +
      `&radius=${args.radiusM}&sort=distance&size=15&page=${page}`;
    const res = await args.fetchFn(url, { headers: { Authorization: `KakaoAK ${args.key}` } });
    if (!res.ok) {
      const body = (await res.text().catch(() => "")).slice(0, 200);
      throw new Error(`Kakao Local ${res.status}: ${body}`);
    }
    const json = (await res.json()) as { documents?: KakaoPlaceDoc[]; meta?: { is_end?: boolean } };
    docs.push(...(json.documents || []));
    if (json.meta?.is_end !== false) break;
  }
  return docs;
}

/** 대표 종목 0 = 복싱만 하는 곳(이름·업종에 MMA·주짓수·킥복싱 등 다른 종목 단서 없음). */
const BOXING_ONLY = 0;

/**
 * (lat,lng) 반경 [radiusKm] 안의 격투기 체육관 — 교류전 상대 후보. 키워드 7개를 동시에 검색(키워드당 최대 3회 호출).
 * OctaLink 는 MMA 체육관용이라 복싱만 하는 체육관은 뺀다('복싱' 키워드는 킥복싱 체육관을 찾는 데 필요해 유지).
 * 한 키워드 결과가 45곳을 넘으면 먼 곳부터 빠질 수 있음(API 한계, 3km·강남 수준에서만 해당).
 */
export async function searchNearbyGyms(args: {
  lat: number; lng: number; radiusKm: number; key: string; fetchFn: FetchFn;
  excludePlaceId?: string | null; keywords?: string[];
}): Promise<NearbyPlace[]> {
  if (!ALLOWED_RADIUS_KM.includes(args.radiusKm)) throw new Error(`반경은 ${ALLOWED_RADIUS_KM.join("·")}km 만 가능`);
  const radiusM = args.radiusKm * 1000;
  const lists = await Promise.all((args.keywords || NEARBY_KEYWORDS).map((query) =>
    searchKeyword({ query, lat: args.lat, lng: args.lng, radiusM, key: args.key, fetchFn: args.fetchFn })));
  return mergePlaces(lists.flat(), args.excludePlaceId).filter((p) => p.sportGroup !== BOXING_ONLY);
}

/** 인스턴스 메모리 캐시(사용자 경험용 짧은 캐시 — 카카오 정책상 영구 저장 금지). */
export class TtlCache<T> {
  private readonly map = new Map<string, { at: number; value: T }>();
  constructor(private readonly ttlMs: number, private readonly now: () => number = Date.now) {}

  get(key: string): T | undefined {
    const hit = this.map.get(key);
    if (!hit) return undefined;
    if (this.now() - hit.at >= this.ttlMs) {
      this.map.delete(key);
      return undefined;
    }
    return hit.value;
  }

  set(key: string, value: T): void {
    this.map.set(key, { at: this.now(), value });
  }
}

/**
 * `gymInterests/{placeId}.requesters` — 교류 요청한 회원 uid → { gymId, at }.
 * 회원 목록은 서버만 읽고(Firestore rules 로 클라이언트 차단) 앱에는 숫자만 내려준다.
 */
export type Requesters = Record<string, { gymId: string; at: number }>;

export function interestSummary(requesters: Requesters | undefined, uid: string, gymId: string) {
  const entries = Object.entries(requesters || {});
  return {
    count: entries.length,
    myGymCount: entries.filter(([, v]) => v.gymId === gymId).length,
    requestedByMe: entries.some(([k]) => k === uid),
  };
}

/** 교류 요청 토글 — 이미 요청했으면 취소, 아니면 추가. */
export function toggleRequester(requesters: Requesters | undefined, uid: string, gymId: string, at: number) {
  const next: Requesters = { ...(requesters || {}) };
  const requested = !(uid in next);
  if (requested) next[uid] = { gymId, at };
  else delete next[uid];
  return { requesters: next, requested };
}
