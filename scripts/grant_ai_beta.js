/**
 * AI 코치의 맞춤 루틴 베타 — 기존 실사용자 2명 백필.
 *
 *  - 테스트 계정(test-bak-jh, test-f-white-1 등) 제외.
 *  - kakao:4892939648 / kakao:4908533282 만 명시적으로 aiRoutineBeta=true 로 set.
 *  - CREATOR 는 역할 기반으로 통과하므로 백필 불필요.
 *
 * 신규 가입자는 `completeSignup` Cloud Function 이 grants 수 < 12 일 때 자동 부여
 * → 이 스크립트로 시드된 2명을 포함해 선착순 12명까지 채워짐.
 *
 * 실행:
 *   cd OctaLink/scripts
 *   node grant_ai_beta.js
 *
 * 인증은 `gcloud auth application-default login` 또는 GOOGLE_APPLICATION_CREDENTIALS
 * 환경변수로 처리. firebase CLI 로 로그인되어 있으면 그대로 사용 가능.
 */

const admin = require("../functions/node_modules/firebase-admin");

const REAL_USER_UIDS = [
  "kakao:4892939648",
  "kakao:4908533282",
];

async function main() {
  admin.initializeApp({
    projectId: "octalink-28088",
  });
  const db = admin.firestore();

  for (const uid of REAL_USER_UIDS) {
    const ref = db.doc(`members/${uid}`);
    const snap = await ref.get();
    if (!snap.exists) {
      console.warn(`[skip] members/${uid} not found`);
      continue;
    }
    const before = snap.data().aiRoutineBeta;
    if (before === true) {
      console.log(`[ok] ${uid} already aiRoutineBeta=true`);
      continue;
    }
    await ref.update({
      aiRoutineBeta: true,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    console.log(`[granted] ${uid} (was ${before === undefined ? "<missing>" : before})`);
  }

  const grantsSnap = await db.collection("members")
    .where("aiRoutineBeta", "==", true)
    .count()
    .get();
  console.log(`[total] aiRoutineBeta=true grants: ${grantsSnap.data().count}/12`);
}

main()
  .then(() => process.exit(0))
  .catch((e) => { console.error(e); process.exit(1); });
