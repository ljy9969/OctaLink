// One-off: members/{SOURCE}/attendance → members/{TARGET}/attendance 복제
// 실행: node scripts/copy-attendance.js
// 인증: ADC 필요 — `gcloud auth application-default login` 선행
//
// 복제 규칙:
//  - doc ID = classDate (ISO 문자열). 양쪽 동일 — idempotent set() 으로 덮어쓰기.
//  - 모든 필드 그대로 복사하되 `memberId` 만 TARGET uid 로 교체.
//    (collectionGroup 쿼리가 memberId 필드로 회원 식별 — 잘못 두면 SOURCE 회원 doc 처럼 보임)
//  - 기타 필드(classDefId / checkInAt / verified / lat / lng) 는 원본 유지.

const path = require("path");
const admin = require(path.resolve(__dirname, "..", "functions", "node_modules", "firebase-admin"));

admin.initializeApp({ projectId: "octalink-28088" });

const db = admin.firestore();

const SOURCE_MEMBER_ID = "kakao:4891520650"; // 운영앱 계정
const TARGET_MEMBER_ID = "kakao:4893329137"; // 개발앱 계정

async function main() {
  const sourceCol = db
    .collection("members")
    .doc(SOURCE_MEMBER_ID)
    .collection("attendance");
  const targetCol = db
    .collection("members")
    .doc(TARGET_MEMBER_ID)
    .collection("attendance");

  const snap = await sourceCol.get();
  if (snap.empty) {
    console.log(`SOURCE ${SOURCE_MEMBER_ID}/attendance 가 비어있음. 종료.`);
    return;
  }
  console.log(`SOURCE ${SOURCE_MEMBER_ID}/attendance : ${snap.size} docs`);

  let copied = 0;
  for (const doc of snap.docs) {
    const src = doc.data();
    const data = {
      ...src,
      memberId: TARGET_MEMBER_ID, // 핵심: 본인 식별 필드 교체
    };
    // doc ID = classDate. 양쪽 동일하게 set() 으로 덮어쓰기 (멱등).
    await targetCol.doc(doc.id).set(data);
    console.log(`  ok  ${doc.id}  checkInAt=${src.checkInAt?.toDate?.().toISOString?.() ?? "?"}`);
    copied++;
  }
  console.log(`\nDone. ${copied} attendance docs copied: ${SOURCE_MEMBER_ID} → ${TARGET_MEMBER_ID}`);
}

main().catch((e) => {
  console.error("FAILED:", e);
  process.exit(1);
});
