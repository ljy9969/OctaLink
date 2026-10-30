// 교류전 '교류 요청'(Firestore gymInterests — 앱 회원이 아직 OctaLink 전인 체육관에 남김) → sales 리드 우선순위.
// 회원이 교류전을 원한다는 신호라 영업 연락 1순위. 요청한 회원 uid 는 읽어도 내보내지 않고
// 장소별 요청 수·요청한 체육관 수만 쓴다. firebase-admin 은 이 폴더(connectors)에 설치돼 있음.

/** gymInterests 문서들 → Map(placeId → { placeId, name, count, gyms }). 요청 0건은 제외. */
export function summarizeInterests(docs) {
  const out = new Map();
  for (const d of docs) {
    const reqs = Object.values(d.requesters || {});
    if (!reqs.length) continue;
    out.set(String(d.id), {
      placeId: String(d.id),
      name: d.name || '',
      count: reqs.length,
      gyms: new Set(reqs.map((r) => r.gymId)).size,
    });
  }
  return out;
}

async function defaultReader({ credentialJson, projectId }) {
  const admin = (await import('firebase-admin')).default;
  if (!admin.apps.length) {
    admin.initializeApp({ credential: admin.credential.cert(JSON.parse(credentialJson)), projectId });
  }
  const snap = await admin.firestore().collection('gymInterests').get();
  return snap.docs.map((s) => ({ id: s.id, ...s.data() }));
}

export async function loadGymInterests({ credentialJson, projectId, readerFn = defaultReader }) {
  return summarizeInterests(await readerFn({ credentialJson, projectId }));
}
