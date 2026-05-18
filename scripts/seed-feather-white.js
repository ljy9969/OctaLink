// One-off: 페더급 화이트 벨트 회원 8명 시드 (대진표 8강 캡처용).
// 실행: node scripts/seed-feather-white.js
// 인증: ADC 필요 — `gcloud auth application-default login` 선행
//
// 멱등: doc ID 가 "test-f-white-{n}" 고정이라 재실행해도 같은 doc 덮어쓰기.
// 정리: 화면 캡처 후 Firebase Console 에서 6개 doc 수동 삭제하거나 별도 cleanup 스크립트로.

const path = require("path");
const admin = require(path.resolve(__dirname, "..", "functions", "node_modules", "firebase-admin"));

admin.initializeApp({ projectId: "octalink-28088" });

const db = admin.firestore();

const COUNT = 8;
const ID_PREFIX = "test-f-white-";
const NAME_PREFIX = "f-white ";

async function main() {
  const now = admin.firestore.FieldValue.serverTimestamp();
  const today = new Date().toISOString().slice(0, 10); // "YYYY-MM-DD"

  for (let i = 1; i <= COUNT; i++) {
    const id = `${ID_PREFIX}${i}`;
    const name = `${NAME_PREFIX}${i}`;
    const data = {
      id,
      name,
      belt: "WHITE",
      weightClass: "FEATHER",
      // BracketDrawScreen 등 다른 화면이 avatarFor(doc.gender, doc.weightClass) 로 렌더.
      // gender 가 null 이면 m_* fallback → f-white 이름인데 남자 캐릭터로 표시되는 버그 발생.
      avatarId: "f_feather",
      role: "MEMBER",
      status: "APPROVED",
      joinDate: today,
      // 카카오 OAuth 미사용 (admin seed) — authProviderId 는 doc id 와 동일하게 두면 collision 방지.
      authProviderId: id,
      // 카카오 동의 항목 — 테스트 데이터지만 gender 만 명시 (avatarFor 가 여자 캐릭터로 파생).
      phone: null,
      email: null,
      gender: "FEMALE",
      ageRange: null,
      birthday: null,
      birthyear: null,
      // 스킬 점수 미평가.
      skills: null,
      // 알림/FCM 미설정.
      fcmToken: null,
      notificationPrefs: {},
      classReminderSlots: [],
      createdAt: now,
      updatedAt: now,
    };
    await db.collection("members").doc(id).set(data);
    console.log(`  ok  ${id}  (${name})`);
  }
  console.log(`\nDone. ${COUNT} test members seeded: ${ID_PREFIX}1 ~ ${ID_PREFIX}${COUNT}`);
  console.log(`Cleanup tip: Firebase Console > Firestore > members > 위 8개 doc 수동 삭제, 또는 scripts/delete-feather-white.js 작성.`);
}

main().catch((e) => {
  console.error("FAILED:", e);
  process.exit(1);
});
