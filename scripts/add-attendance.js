// One-off: members/kakao:4891520650/attendance 백필
// 실행: node scripts/add-attendance.js
// 인증: ADC 필요 — `gcloud auth application-default login` 선행
//
// classDate ISO 문자열이 doc ID. 한국 시간(KST, UTC+9) 기준 checkInAt → UTC Timestamp 로 저장.
// 기존 doc 이 있어도 set() 으로 덮어쓴다 (자연 키 = classDate 라 idempotent).

const path = require("path");
const admin = require(path.resolve(__dirname, "..", "functions", "node_modules", "firebase-admin"));

admin.initializeApp({ projectId: "octalink-28088" });

const db = admin.firestore();

const MEMBER_ID = "kakao:4891520650";
const CLASS_DEF_ID = "default";
const YEAR = 2026;

// [month, day, hour, minute] — KST 기준
const entries = [
  [4, 13, 19, 0],
  [4, 15, 19, 0],
  [4, 17, 19, 0],
  [4, 20, 19, 0],
  [4, 21, 19, 0],
  [4, 24, 19, 0],
  [4, 27, 19, 0],
  [4, 29, 19, 0],
  [4, 30, 19, 0],
  [5, 4, 11, 30],
  [5, 6, 19, 0],
  [5, 8, 19, 0],
  [5, 11, 19, 0],
  [5, 12, 19, 0],
  [5, 13, 19, 0],
  [5, 18, 19, 0],
];

const pad = (n) => String(n).padStart(2, "0");

async function main() {
  for (const [m, d, h, min] of entries) {
    const classDate = `${YEAR}-${pad(m)}-${pad(d)}`;
    // KST = UTC + 9. ISO 문자열에 +09:00 붙이면 JS Date 가 알아서 UTC 로 변환.
    const isoKst = `${YEAR}-${pad(m)}-${pad(d)}T${pad(h)}:${pad(min)}:00+09:00`;
    const checkInAt = admin.firestore.Timestamp.fromDate(new Date(isoKst));
    const docRef = db
      .collection("members")
      .doc(MEMBER_ID)
      .collection("attendance")
      .doc(classDate);
    await docRef.set({
      id: classDate,
      memberId: MEMBER_ID,
      classDefId: CLASS_DEF_ID,
      classDate,
      checkInAt,
      verified: false,
    });
    console.log(`  ok  ${classDate}  checkInAt=${isoKst}`);
  }
  console.log(`\nDone. ${entries.length} attendance docs written for ${MEMBER_ID}.`);
}

main().catch((e) => {
  console.error("FAILED:", e);
  process.exit(1);
});
