// OctaLink 가입 체육관(gyms/{gymId})에 위치 넣기 — 교류전 지도의 기준점(반경 검색 중심·체육관 간 거리).
// 카카오 장소 검색(--query)이면 장소 id 까지 저장해 주변 검색 결과에서 '가입 체육관'(파란 점)으로 알아본다.
// 카카오맵에 없는 체육관은 주소 검색(--address)으로 좌표·주소만 저장.
//
// 사용(ops 폴더에서):
//   node connectors/set-gym-location.mjs --gym teamposse --query "팀포세 스트라이킹 강남"        # 후보 미리보기
//   node connectors/set-gym-location.mjs --gym teamposse --query "팀포세 스트라이킹 강남" --pick 2 --write
//   node connectors/set-gym-location.mjs --gym octagon --address "서울 마포구 양화로 1" --write
// 키: KAKAO_REST_API_KEY · FIREBASE_SERVICE_ACCOUNT (ops/mcp/.env). --write 없으면 쓰지 않음.
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const KEYWORD_API = 'https://dapi.kakao.com/v2/local/search/keyword.json';
const ADDRESS_API = 'https://dapi.kakao.com/v2/local/search/address.json';

function coords(doc) {
  const lat = Number(doc.y), lng = Number(doc.x);
  if (doc.x === '' || doc.y === '' || !Number.isFinite(lat) || !Number.isFinite(lng)) throw new Error('좌표 없는 결과');
  return { lat, lng };
}

export function placeToLocation(doc) {
  const { lat, lng } = coords(doc);
  return { address: doc.road_address_name || doc.address_name || '', lat, lng, kakaoPlaceId: String(doc.id) };
}

export function addressToLocation(doc) {
  const { lat, lng } = coords(doc);
  return { address: doc.road_address?.address_name || doc.address_name || '', lat, lng, kakaoPlaceId: null };
}

export function parseArgs(argv) {
  const val = (name) => { const i = argv.indexOf(name); return i >= 0 ? argv[i + 1] ?? null : null; };
  const gym = val('--gym');
  const query = val('--query');
  const address = val('--address');
  if (!gym) throw new Error('--gym <gymId> 가 필요해요');
  if (!query && !address) throw new Error('--query 또는 --address 가 필요해요');
  const pick = Number(val('--pick') ?? 1);
  return { gym, query, address, pick: Number.isInteger(pick) && pick >= 1 ? pick : 1, write: argv.includes('--write') };
}

async function kakao(url, key) {
  const res = await fetch(url, { headers: { Authorization: `KakaoAK ${key}` } });
  if (!res.ok) throw new Error(`Kakao Local ${res.status}: ${(await res.text().catch(() => '')).slice(0, 200)}`);
  return (await res.json()).documents || [];
}

async function main() {
  const opsRoot = process.env.OPS_ROOT || join(dirname(fileURLToPath(import.meta.url)), '..');
  const { loadEnv } = await import('../engine/env.mjs'); loadEnv(opsRoot);
  const args = parseArgs(process.argv.slice(2));
  const key = process.env.KAKAO_REST_API_KEY;
  if (!key) throw new Error('KAKAO_REST_API_KEY 가 ops/mcp/.env 에 없어요');

  const docs = args.query
    ? await kakao(`${KEYWORD_API}?size=5&query=${encodeURIComponent(args.query)}`, key)
    : await kakao(`${ADDRESS_API}?size=5&query=${encodeURIComponent(args.address)}`, key);
  if (!docs.length) throw new Error('검색 결과가 없어요 — 검색어를 바꾸거나 --address 로 주소를 넣어 주세요');
  docs.forEach((d, i) => console.log(`${i + 1}. ${d.place_name || d.address_name} — ${d.road_address_name || d.road_address?.address_name || d.address_name}${d.category_name ? ` (${d.category_name})` : ''}`));
  const chosen = docs[args.pick - 1];
  if (!chosen) throw new Error(`--pick ${args.pick} 에 해당하는 후보가 없어요`);
  const loc = args.query ? placeToLocation(chosen) : addressToLocation(chosen);
  console.log(`\n→ gyms/${args.gym} 에 쓸 위치:`, loc);
  if (!args.write) { console.log('(미리보기 — 실제로 쓰려면 --write 를 붙여 주세요)'); return; }

  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) throw new Error('FIREBASE_SERVICE_ACCOUNT 가 ops/mcp/.env 에 없어요');
  const cred = JSON.parse(existsSync(raw) ? readFileSync(raw, 'utf8') : raw);
  const admin = (await import('firebase-admin')).default;
  if (!admin.apps.length) admin.initializeApp({ credential: admin.credential.cert(cred), projectId: cred.project_id });
  const ref = admin.firestore().doc(`gyms/${args.gym}`);
  if (!(await ref.get()).exists) throw new Error(`gyms/${args.gym} 문서가 없어요`);
  await ref.update(loc);
  console.log(`저장했어요: gyms/${args.gym}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((e) => { console.error(e.message); process.exit(1); });
}
