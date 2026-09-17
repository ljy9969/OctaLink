// 1:1 문의(inquiries) 읽기/쓰기 — 답변 대사·게시용. firebase-admin lazy. 자격증명 없으면 no-op.
// 읽기: status=PENDING 문의. 쓰기: 답변 게시(status=ANSWERED) / 상태 변경.
async function adminApp(credentialJson, projectId) {
  const admin = (await import('firebase-admin')).default;
  const cred = typeof credentialJson === 'string' ? JSON.parse(credentialJson) : credentialJson;
  if (!admin.apps.length) admin.initializeApp({ credential: admin.credential.cert(cred), projectId: projectId || cred.project_id });
  return admin;
}

/** status=PENDING 문의 조회. readerFn 주입 시 그걸 사용(테스트). */
export async function fetchPending({ projectId, credentialJson, limit = 20, readerFn } = {}) {
  if (!credentialJson && !readerFn) return { ok: false, reason: 'FIREBASE 자격증명 없음 (조회 skip)', inquiries: [] };
  const read = readerFn || (async () => {
    const admin = await adminApp(credentialJson, projectId);
    const snap = await admin.firestore().collection('inquiries').where('status', '==', 'PENDING').limit(limit).get();
    return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
  });
  const rows = await read();
  const inquiries = rows.map((r) => ({
    id: r.id, gymId: r.gymId, category: r.category, text: r.text, authorId: r.authorId, authorName: r.authorName,
  }));
  return { ok: true, inquiries };
}

/** 문의 상태 변경(예: PENDING→DRAFTED). writerFn 주입 가능. */
export async function setStatus({ projectId, credentialJson, inquiryId, status, writerFn }) {
  if (!credentialJson && !writerFn) return { ok: false, reason: 'FIREBASE 자격증명 없음' };
  const write = writerFn || (async () => {
    const admin = await adminApp(credentialJson, projectId);
    await admin.firestore().collection('inquiries').doc(inquiryId).update({ status });
  });
  await write();
  return { ok: true };
}

/** 답변 게시 — answer 저장 + status=ANSWERED. writerFn 주입 가능. */
export async function postAnswer({ projectId, credentialJson, inquiryId, answer, answeredBy = 'ops', writerFn }) {
  if (!credentialJson && !writerFn) return { ok: false, reason: 'FIREBASE 자격증명 없음' };
  const write = writerFn || (async () => {
    const admin = await adminApp(credentialJson, projectId);
    await admin.firestore().collection('inquiries').doc(inquiryId).update({
      answer, answeredBy, status: 'ANSWERED', answeredAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  });
  await write();
  return { ok: true };
}
