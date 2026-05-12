import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import * as admin from "firebase-admin";

admin.initializeApp();

/**
 * Kakao 사용자 정보 응답 타입 (Kakao API v2 /v2/user/me).
 * 우리는 식별자(id)와 닉네임만 사용.
 */
interface KakaoUser {
  id: number;
  kakao_account?: {
    profile?: {
      nickname?: string;
      profile_image_url?: string;
    };
    email?: string;
    phone_number?: string;
  };
}

/**
 * 클라이언트가 카카오 OAuth 로 받은 accessToken 을 Firebase Custom Token 으로 교환.
 *
 * 흐름:
 *  1. 클라이언트 → kakao SDK 로그인 → accessToken 획득
 *  2. 클라이언트 → 이 함수 호출 (accessToken 전달)
 *  3. 이 함수 → Kakao API /v2/user/me 로 토큰 검증 + 사용자 ID 조회
 *  4. uid = "kakao:{kakaoUserId}" 형식으로 Firebase Auth 사용자 ensure
 *  5. Custom Token 발급해서 반환
 *  6. 클라이언트 → signInWithCustomToken(customToken)
 *
 * uid 형식이 MemberDoc.authProviderId 와 1:1 매칭됨 — Repository observe 가 그 키로 조회.
 */
export const kakaoSignIn = onCall(
  {
    region: "asia-northeast3",
    cors: false,
    // 클라이언트는 로그인 전이라 Firebase Auth 토큰 / App Check 토큰 둘 다 없음.
    // firebase-functions 가 자동으로 토큰 검증을 시도하지 않도록 명시적으로 끔.
    enforceAppCheck: false,
    consumeAppCheckToken: false,
  },
  async (request) => {
    logger.info("[1/5] kakaoSignIn entry");
    const accessToken = request.data?.accessToken as string | undefined;
    if (!accessToken) {
      logger.warn("[1/5] accessToken missing in request.data");
      throw new HttpsError("invalid-argument", "accessToken required");
    }
    logger.info("[1/5] accessToken received", { len: accessToken.length });

    // 2) Kakao API 로 토큰 검증 + 사용자 정보 조회
    logger.info("[2/5] fetching Kakao /v2/user/me");
    let res: Response;
    try {
      res = await fetch("https://kapi.kakao.com/v2/user/me", {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
    } catch (e) {
      logger.error("[2/5] fetch threw", e);
      throw new HttpsError("internal", `fetch error: ${e}`);
    }
    logger.info("[2/5] Kakao response", { status: res.status });
    if (!res.ok) {
      const body = await res.text();
      logger.error("[2/5] Kakao API not ok", { status: res.status, body });
      throw new HttpsError(
        "unauthenticated",
        `Kakao token verification failed: ${res.status}`,
      );
    }
    const kakaoUser = (await res.json()) as KakaoUser;
    const uid = `kakao:${kakaoUser.id}`;
    const nickname = kakaoUser.kakao_account?.profile?.nickname;
    logger.info("[3/5] Kakao user parsed", { uid, nickname });

    // 4) Firebase Auth 사용자 ensure — 없으면 생성, 있으면 displayName 만 동기화
    try {
      logger.info("[4/5] auth().getUser", { uid });
      await admin.auth().getUser(uid);
      logger.info("[4/5] user exists");
      if (nickname) {
        await admin.auth().updateUser(uid, { displayName: nickname });
        logger.info("[4/5] displayName updated");
      }
    } catch (e) {
      logger.info("[4/5] user not found, creating", { err: String(e) });
      try {
        await admin.auth().createUser({
          uid,
          displayName: nickname,
        });
        logger.info("[4/5] user created");
      } catch (e2) {
        logger.error("[4/5] createUser failed", e2);
        throw new HttpsError("internal", `createUser failed: ${e2}`);
      }
    }

    // 5) Custom Token 발급 (provider claim 부착해서 디버깅 식별 용이)
    logger.info("[5/5] createCustomToken");
    let customToken: string;
    try {
      customToken = await admin.auth().createCustomToken(uid, {
        provider: "kakao",
        kakaoId: kakaoUser.id,
      });
    } catch (e) {
      logger.error("[5/5] createCustomToken failed", e);
      throw new HttpsError("internal", `createCustomToken failed: ${e}`);
    }
    logger.info("[5/5] customToken issued", { len: customToken.length });

    return { customToken };
  },
);

/**
 * 가입 폼 제출 처리. 클라이언트 직접 members/{uid} create 는 Security Rules 가 차단하므로
 * 이 함수가 server-side 에서 권한 검증 + role 결정 후 문서 생성.
 *
 * RoleAllowlist 매칭: 카카오 닉네임 기반(MVP). Firebase Auth uid 매칭으로 강화 예정.
 */
export const completeSignup = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Login required");
    }
    const uid = request.auth.uid;
    const data = request.data ?? {};
    const name = (data.name as string | undefined)?.trim();
    if (!name) {
      throw new HttpsError("invalid-argument", "name required");
    }

    const role = matchRole(name);
    const status =
      ["CREATOR", "MASTER", "COACH"].includes(role) ? "APPROVED" : "PENDING";

    const docRef = admin.firestore().doc(`members/${uid}`);
    const existing = await docRef.get();
    if (existing.exists) {
      return { ok: true, alreadyExists: true };
    }

    await docRef.set({
      id: uid,
      name,
      belt: data.belt ?? "WHITE",
      weightClass: data.weightClass ?? "LIGHT",
      avatarId: data.avatarId ?? "ryu",
      role,
      status,
      joinDate: new Date().toISOString().slice(0, 10),
      phone: data.phone ?? null,
      // 카카오 비즈앱 동의 항목 — 권한 없거나 미동의 시 null
      email: data.email ?? null,
      gender: data.gender ?? null,
      ageRange: data.ageRange ?? null,
      birthday: data.birthday ?? null,
      birthyear: data.birthyear ?? null,
      authProviderId: uid,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { ok: true, role, status };
  },
);

/**
 * 본인 회원 탈퇴 — `members/{uid}.status = "LEFT"` 로 전환.
 *
 * 도장 명단(MemberDoc) 자체 삭제 / 카스케이드 삭제는 별도(CREATOR 권한).
 * 본 함수는 회원의 즉시 앱 이용 중단 + 운영자 명단 정리 시점까지의 표시만 담당.
 *
 * 출석/스킬/코멘트 등 과거 기록은 보존 — 도장 운영 통계/회원 복귀 시 재활용 가능.
 */
export const leaveMembership = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Login required");
    }
    const uid = request.auth.uid;
    const docRef = admin.firestore().doc(`members/${uid}`);
    const snap = await docRef.get();
    if (!snap.exists) {
      throw new HttpsError("not-found", "Member doc not found");
    }
    await docRef.update({
      status: "LEFT",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    return { ok: true };
  },
);

/**
 * 개발/시연용 — 테스트 회원 + 오늘 출석 시드.
 *
 * CREATOR (이지연) 만 호출 가능. 멱등 — 기존 doc 이 있으면 skip.
 * 호출 방법:
 *   1. Firebase Functions Shell: `firebase functions:shell` → `seedTestData({})`
 *   2. 또는 curl 로 직접: 클라이언트에서 호출 (CreatorScreen 임시 버튼 추가 가능)
 *
 * 시드되는 데이터:
 *   - 5명 테스트 회원 (다양한 체급/벨트, status=APPROVED, role=MEMBER)
 *   - 각자 오늘 날짜의 attendance 서브컬렉션 doc 1건
 *
 * 운영 환경 전엔 이 함수 제거 / 비활성화 권장.
 */
export const seedTestData = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Login required");
    }
    const callerUid = request.auth.uid;
    const callerSnap = await admin.firestore().doc(`members/${callerUid}`).get();
    const callerRole = callerSnap.exists ? callerSnap.get("role") : null;
    if (callerRole !== "CREATOR") {
      throw new HttpsError("permission-denied", "CREATOR only");
    }

    const today = new Date().toISOString().slice(0, 10);
    const now = admin.firestore.FieldValue.serverTimestamp();
    const seedMembers = [
      { id: "test-bak-jh", name: "박정호", belt: "BROWN", weightClass: "HEAVY", avatarId: "ken" },
      { id: "test-choi-ms", name: "최민서", belt: "BLACK", weightClass: "MIDDLE", avatarId: "guile" },
      { id: "test-kim-sh", name: "김상혁", belt: "PURPLE", weightClass: "WELTER", avatarId: "oni" },
      { id: "test-han-dy", name: "한도윤", belt: "BLUE", weightClass: "LIGHT", avatarId: "ryu" },
      { id: "test-shin-yr", name: "신예린", belt: "WHITE", weightClass: "FEATHER", avatarId: "cammy" },
    ];

    const db = admin.firestore();
    const batch = db.batch();
    let createdCount = 0;
    let attendanceCount = 0;

    for (let i = 0; i < seedMembers.length; i++) {
      const m = seedMembers[i];
      const memberRef = db.doc(`members/${m.id}`);
      const existing = await memberRef.get();
      if (!existing.exists) {
        batch.set(memberRef, {
          id: m.id,
          name: m.name,
          belt: m.belt,
          weightClass: m.weightClass,
          avatarId: m.avatarId,
          role: "MEMBER",
          status: "APPROVED",
          joinDate: today,
          phone: null,
          email: null,
          gender: null,
          ageRange: null,
          birthday: null,
          birthyear: null,
          authProviderId: `kakao:test-${m.id}`,
          createdAt: now,
          updatedAt: now,
        });
        createdCount++;
      }
      // 오늘 출석 시드 — 각자 시간 살짝 차이
      const attendanceRef = memberRef.collection("attendance").doc(today);
      const attExisting = await attendanceRef.get();
      if (!attExisting.exists) {
        const checkInAt = admin.firestore.Timestamp.fromDate(
          new Date(Date.now() - (5 - i) * 60_000)
        );
        batch.set(attendanceRef, {
          id: today,
          memberId: m.id,
          classDefId: "default",
          classDate: today,
          checkInAt,
          verified: false,
        });
        attendanceCount++;
      }
    }

    await batch.commit();
    return { ok: true, createdMembers: createdCount, createdAttendance: attendanceCount };
  },
);

/**
 * RoleAllowlist — Android 클라이언트의 [RoleAllowlist.kt] 와 동일한 명단을 server 가 보유.
 * 양쪽 동기화 필요. 추후 Firestore `roleAllowlist` 컬렉션으로 이전 검토.
 */
function matchRole(name: string): "CREATOR" | "MASTER" | "COACH" | "MEMBER" {
  const CREATORS = new Set(["이지연"]);
  const MASTERS = new Set(["김파시"]);
  const COACHES = new Set<string>([]);

  if (CREATORS.has(name)) return "CREATOR";
  if (MASTERS.has(name)) return "MASTER";
  if (COACHES.has(name)) return "COACH";
  return "MEMBER";
}
