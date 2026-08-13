import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentWritten,
} from "firebase-functions/v2/firestore";
import { defineSecret } from "firebase-functions/params";
import { logger } from "firebase-functions/v2";
import * as admin from "firebase-admin";
import { GoogleGenAI } from "@google/genai";

/**
 * YouTube Data API v3 key — `search.list` 호출용. Secret Manager 에 미리 저장 필요:
 *   firebase functions:secrets:set YOUTUBE_API_KEY
 * 키는 GCP 콘솔 > API 및 서비스 > 사용자 인증 정보 > API 키 생성 → YouTube Data API v3 만 허용.
 */
const YOUTUBE_API_KEY = defineSecret("YOUTUBE_API_KEY");

admin.initializeApp();

/**
 * Google Gen AI SDK (Vertex 모드) — Gemini 3.1 Flash Lite 로 주간 보강 루틴 생성.
 *
 * 권역 global — Gemini 3.x GA 모델은 us-central1/asia-northeast3 등 리전 엔드포인트엔
 * 게시되지 않고 global 엔드포인트에서만 서빙됨(2026-08 확인). 리전 지정 시 404 NOT_FOUND.
 * Firebase 프로젝트 ID 는 GCLOUD_PROJECT 환경변수에서 자동 주입.
 *
 * 2026-07: 폐기된 @google-cloud/vertexai(VertexAI) 를 @google/genai 로 이관.
 * 모델 gemini-3.1-flash-lite (GA 경량 등급, 비용 최적 — 2.5 Flash Lite 의 GA 후속). 단발
 * (single-turn) generateContent 호출이라 Gemini 3 의 thought-signature 순환은 불필요.
 */
const ai = new GoogleGenAI({
  vertexai: true,
  project: process.env.GCLOUD_PROJECT ?? "",
  location: "global",
});

const GEMINI_MODEL = "gemini-3.1-flash-lite";
// thinkingBudget=0 : thinking 토큰이 maxOutputTokens 를 먼저 소비해 응답이 잘리는 것 방지.
// maxOutputTokens=8192 : 한국어 6일×3드릴 + feedback ≒ 4~5k 토큰 예상, 여유 잡음.
const GEMINI_CONFIG = {
  temperature: 0.7,
  maxOutputTokens: 8192,
  responseMimeType: "application/json",
  thinkingConfig: { thinkingBudget: 0 },
};

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
    logger.info("[3/5] Kakao user parsed", { uid: maskId(uid), nickname: maskName(nickname) });

    // 4) Firebase Auth 사용자 ensure — 없으면 생성, 있으면 displayName 만 동기화
    try {
      logger.info("[4/5] auth().getUser", { uid: maskId(uid) });
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

    // 소속 체육관(멀티테넌트) — 클라이언트가 드롭다운에서 고른 gymId. 서버가 실재 여부 재검증.
    // 빈 값은 레거시/전환기 호환으로 허용(gymId="") — 마이그레이션 후 신규 가입은 항상 유효 gymId.
    const gymId = ((data.gymId as string | undefined) ?? "").trim();
    if (gymId) {
      const gymSnap = await admin.firestore().doc(`gyms/${gymId}`).get();
      if (!gymSnap.exists) {
        throw new HttpsError("invalid-argument", "존재하지 않는 체육관입니다.");
      }
    }

    const role = matchRole(name);
    const status =
      ["CREATOR", "MASTER", "COACH"].includes(role) ? "APPROVED" : "PENDING";

    const rawJoinDate = data.joinDate as string | undefined;
    logger.info("[completeSignup] entry", {
      uid: maskId(uid),
      name: maskName(name),
      joinDate: rawJoinDate ?? "<missing>",
    });

    const docRef = admin.firestore().doc(`members/${uid}`);
    const existing = await docRef.get();
    if (existing.exists) {
      // 이미 가입된 회원이 다시 가입 폼 제출 — joinDate 같은 폼 값이 무시됨에 유의.
      // (재가입 / 정보 수정은 별도 함수에서 처리)
      logger.warn(
        `[completeSignup] alreadyExists — ignoring submitted joinDate=${rawJoinDate ?? "<missing>"} for uid=${maskId(uid)}`,
      );
      return { ok: true, alreadyExists: true };
    }

    // joinDate: 사용자가 폼에서 입력한 도장 입관일 (앱 가입일과 별개).
    // 클라이언트가 안 보내면 오늘 날짜로 폴백 — 옛 클라이언트 호환.
    const joinDate = rawJoinDate ?? new Date().toISOString().slice(0, 10);

    // AI 코치의 맞춤 루틴 베타 자동 부여 (선착순 12명).
    //
    // **"선착순" 기준은 createdAt(앱 가입 = 본 함수 실행 시점) 이지 joinDate(도장 입관일) 가 아님.**
    // joinDate 는 사용자가 폼에서 직접 입력하므로 과거로 백데이트 가능. 반면 createdAt 은
    // 본 함수가 서버 타임스탬프로 기록 → 위조 불가. 카운트 자체는 grants 수만 보지만
    // grants 는 본 함수 실행 순서대로(즉 createdAt 순서대로) 쌓이므로 정렬이 일관.
    //
    // CREATOR 는 역할 기반으로 항상 통과하므로 count 에 안 셈. 12 도달 후 신규 회원 = false.
    const grantsSnap = await admin
      .firestore()
      .collection("members")
      .where("aiRoutineBeta", "==", true)
      .count()
      .get();
    const currentGrants = grantsSnap.data().count;
    const aiRoutineBeta = currentGrants < 12;
    logger.info("[completeSignup] aiRoutineBeta grant", { currentGrants, granted: aiRoutineBeta });

    await docRef.set({
      id: uid,
      gymId,
      name,
      belt: data.belt ?? "WHITE",
      weightClass: data.weightClass ?? "LIGHT",
      avatarId: data.avatarId ?? "ryu",
      role,
      status,
      joinDate,
      careerStartYm: (data.careerStartYm as string | undefined) ?? null,
      exchangeWins: 0,
      exchangeLosses: 0,
      exchangeDraws: 0,
      phone: data.phone ?? null,
      // 카카오 비즈앱 동의 항목 — 권한 없거나 미동의 시 null
      email: data.email ?? null,
      gender: data.gender ?? null,
      ageRange: data.ageRange ?? null,
      birthday: data.birthday ?? null,
      birthyear: data.birthyear ?? null,
      authProviderId: uid,
      aiRoutineBeta,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { ok: true, role, status, aiRoutineBeta };
  },
);

/**
 * 크로스짐 공개 프로필 투영 — members/{uid} 변경 시 publicProfiles/{uid} 유지.
 *
 * 교류전(다른 체육관 관원 조회)은 members 를 같은 체육관으로 스코프하는 규칙 때문에 직접 못 읽음.
 * PII(전화/이메일/생일 등) 를 뺀 제한 필드만 별도 컬렉션에 투영해 **모든 APPROVED 회원이 크로스짐
 * 조회** 가능하게 한다(rules: publicProfiles read=isApproved, write=서버만).
 * 미승인/삭제 회원은 공개 프로필 제거.
 */
export const syncPublicProfile = onDocumentWritten(
  { region: "asia-northeast3", document: "members/{uid}" },
  async (event) => {
    const uid = event.params.uid;
    const after = event.data?.after?.data();
    const ppRef = admin.firestore().doc(`publicProfiles/${uid}`);
    if (!after || after.status !== "APPROVED") {
      await ppRef.delete().catch(() => undefined);
      return;
    }
    await ppRef.set({
      id: uid,
      gymId: after.gymId ?? "",
      name: after.name ?? "",
      gender: after.gender ?? null,
      weightClass: after.weightClass ?? "LIGHT",
      belt: after.belt ?? "WHITE",
      avatarId: after.avatarId ?? "ryu",
      careerStartYm: after.careerStartYm ?? null,
      exchangeWins: after.exchangeWins ?? 0,
      exchangeLosses: after.exchangeLosses ?? 0,
      exchangeDraws: after.exchangeDraws ?? 0,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  },
);

/**
 * 스킬 점수 승인 → member.skills 동기화 — members/{memberId}/skillScores/{scoreId} 변경 시
 * 최신 APPROVED 점수를 member 문서의 skills 임베드 필드로 반영.
 *
 * 프로필 화면은 skillScores 하위컬렉션(코치 승인 점수)을, AI 루틴 화면은 member.skills 를
 * 읽어 두 소스가 어긋나던 문제 해결. 승인 doc 작성/전이/삭제 어느 경우든 최신 APPROVED 로
 * 재계산한다. 저장 스케일(0..1)이 skillScores 와 동일하므로 직접 복사.
 * 승인 점수가 하나도 없으면 self-rated skills 보존을 위해 건드리지 않는다.
 * (member 문서만 갱신 — skillScores 를 다시 쓰지 않아 트리거 재귀 없음)
 */
export const syncMemberSkillsOnScore = onDocumentWritten(
  { region: "asia-northeast3", document: "members/{memberId}/skillScores/{scoreId}" },
  async (event) => {
    const memberId = event.params.memberId;
    const db = admin.firestore();
    const snap = await db
      .collection("members").doc(memberId).collection("skillScores")
      .where("status", "==", "APPROVED")
      .orderBy("evaluatedAt", "desc")
      .limit(1)
      .get();
    if (snap.empty) return;
    const s = snap.docs[0].data();
    await db.doc(`members/${memberId}`).update({
      skills: {
        striking: s.striking ?? 0,
        grappling: s.grappling ?? 0,
        stamina: s.stamina ?? 0,
        technique: s.technique ?? 0,
        mental: s.mental ?? 0,
        speed: s.speed ?? 0,
      },
    });
  },
);

// ══════════════════════════════════════════════════════════════
// 교류전(결투) — requestDuel / approveDuel / rejectDuel / scheduleDuel / recordDuelResult
// 생성·전이는 모두 서버 전용(rules: exchangeMatches write=false). 상태머신:
//   REQUESTED → (상대+양측 운영진 3자 승인) APPROVED → (운영진 일정) SCHEDULED →
//   (운영진 결과) COMPLETED. 거부/취소 = REJECTED/CANCELLED.
// ══════════════════════════════════════════════════════════════
type MemberData = admin.firestore.DocumentData;

async function loadApprovedCaller(uid: string): Promise<MemberData> {
  const snap = await admin.firestore().doc(`members/${uid}`).get();
  const m = snap.data();
  if (!m || m.status !== "APPROVED") {
    throw new HttpsError("permission-denied", "승인된 회원만 이용할 수 있어요.");
  }
  return m;
}

/** 호출자가 특정 체육관의 운영진인지 (CREATOR 는 전역). */
function isGymStaff(member: MemberData, gymId: string): boolean {
  if (member.role === "CREATOR") return true;
  return member.gymId === gymId && (member.role === "MASTER" || member.role === "COACH");
}

export const requestDuel = onCall({ region: "asia-northeast3" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Login required");
  const uid = request.auth.uid;
  const opponentId = (request.data?.opponentMemberId as string | undefined)?.trim();
  if (!opponentId) throw new HttpsError("invalid-argument", "opponentMemberId required");
  if (opponentId === uid) throw new HttpsError("invalid-argument", "본인에게는 신청할 수 없어요.");

  const caller = await loadApprovedCaller(uid);
  const oppSnap = await admin.firestore().doc(`members/${opponentId}`).get();
  const opp = oppSnap.data();
  if (!opp || opp.status !== "APPROVED") {
    throw new HttpsError("not-found", "상대 회원을 찾을 수 없어요.");
  }
  if ((opp.gymId ?? "") === (caller.gymId ?? "")) {
    throw new HttpsError("failed-precondition", "다른 체육관 관원에게만 신청할 수 있어요.");
  }

  const ref = admin.firestore().collection("exchangeMatches").doc();
  await ref.set({
    id: ref.id,
    requesterMemberId: uid,
    requesterGymId: caller.gymId ?? "",
    requesterName: caller.name ?? "",
    opponentMemberId: opponentId,
    opponentGymId: opp.gymId ?? "",
    opponentName: opp.name ?? "",
    // array-contains 쿼리용: 내 교류전(participantIds), 체육관 교류전(gymIds).
    participantIds: [uid, opponentId],
    gymIds: [caller.gymId ?? "", opp.gymId ?? ""],
    weightClass: caller.weightClass ?? null,
    status: "REQUESTED",
    opponentApproved: false,
    requesterGymApproved: false,
    opponentGymApproved: false,
    scheduledDate: null,
    scheduledTime: null,
    place: null,
    winnerMemberId: null,
    isDraw: false,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  logger.info("[requestDuel] created", { id: ref.id, requester: maskId(uid), opponent: maskId(opponentId) });

  // 알림 — 상대 본인 + 양측 운영진(해당 gym 의 MASTER/COACH + 전역 CREATOR) 에게 승인 요청 push.
  // 발송 실패는 신청 자체를 막지 않도록 try/catch 로 감싼다.
  try {
    const reqGym = caller.gymId ?? "";
    const oppGym = opp.gymId ?? "";
    const staffSnap = await admin.firestore()
      .collection("members")
      .where("status", "==", "APPROVED")
      .where("role", "in", ["MASTER", "COACH", "CREATOR"])
      .get();
    const recipients = new Set<string>([opponentId]);
    staffSnap.docs.forEach((d) => {
      const m = d.data();
      if (m.role === "CREATOR" || m.gymId === reqGym || m.gymId === oppGym) recipients.add(d.id);
    });
    recipients.delete(uid); // 신청자 본인 제외
    await sendNotificationTo(
      Array.from(recipients),
      "DUEL_REQUESTED",
      "새 교류전 신청",
      `${caller.name ?? "상대"}님이 ${opp.name ?? ""}님에게 결투를 신청했어요. 승인이 필요해요.`,
    );
  } catch (e) {
    logger.warn("[requestDuel] 알림 발송 실패(무시)", e);
  }

  return { ok: true, id: ref.id };
});

export const approveDuel = onCall({ region: "asia-northeast3" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Login required");
  const uid = request.auth.uid;
  const matchId = (request.data?.matchId as string | undefined)?.trim();
  if (!matchId) throw new HttpsError("invalid-argument", "matchId required");
  const caller = await loadApprovedCaller(uid);
  const ref = admin.firestore().doc(`exchangeMatches/${matchId}`);

  await admin.firestore().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const d = snap.data();
    if (!d) throw new HttpsError("not-found", "교류전을 찾을 수 없어요.");
    if (d.status !== "REQUESTED") throw new HttpsError("failed-precondition", "이미 처리된 요청이에요.");

    const updates: admin.firestore.DocumentData = {
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };
    if (uid === d.opponentMemberId) updates.opponentApproved = true;
    else if (isGymStaff(caller, d.requesterGymId)) updates.requesterGymApproved = true;
    else if (isGymStaff(caller, d.opponentGymId)) updates.opponentGymApproved = true;
    else throw new HttpsError("permission-denied", "승인 권한이 없어요.");

    const opp = updates.opponentApproved ?? d.opponentApproved;
    const rg = updates.requesterGymApproved ?? d.requesterGymApproved;
    const og = updates.opponentGymApproved ?? d.opponentGymApproved;
    if (opp && rg && og) updates.status = "APPROVED";
    tx.update(ref, updates);
  });
  return { ok: true };
});

export const rejectDuel = onCall({ region: "asia-northeast3" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Login required");
  const uid = request.auth.uid;
  const matchId = (request.data?.matchId as string | undefined)?.trim();
  if (!matchId) throw new HttpsError("invalid-argument", "matchId required");
  const caller = await loadApprovedCaller(uid);
  const ref = admin.firestore().doc(`exchangeMatches/${matchId}`);
  const snap = await ref.get();
  const d = snap.data();
  if (!d) throw new HttpsError("not-found", "교류전을 찾을 수 없어요.");
  const canAct = uid === d.opponentMemberId || uid === d.requesterMemberId
    || isGymStaff(caller, d.requesterGymId) || isGymStaff(caller, d.opponentGymId);
  if (!canAct) throw new HttpsError("permission-denied", "권한이 없어요.");
  if (d.status === "COMPLETED") throw new HttpsError("failed-precondition", "이미 종료된 교류전이에요.");
  await ref.update({
    status: uid === d.requesterMemberId ? "CANCELLED" : "REJECTED",
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  return { ok: true };
});

export const scheduleDuel = onCall({ region: "asia-northeast3" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Login required");
  const uid = request.auth.uid;
  const data = request.data ?? {};
  const matchId = (data.matchId as string | undefined)?.trim();
  const date = (data.date as string | undefined)?.trim();
  const time = (data.time as string | undefined)?.trim();
  const place = (data.place as string | undefined)?.trim();
  if (!matchId || !date || !time || !place) {
    throw new HttpsError("invalid-argument", "matchId/date/time/place required");
  }
  const caller = await loadApprovedCaller(uid);
  const ref = admin.firestore().doc(`exchangeMatches/${matchId}`);
  const snap = await ref.get();
  const d = snap.data();
  if (!d) throw new HttpsError("not-found", "교류전을 찾을 수 없어요.");
  if (!isGymStaff(caller, d.requesterGymId) && !isGymStaff(caller, d.opponentGymId)) {
    throw new HttpsError("permission-denied", "운영진만 일정을 정할 수 있어요.");
  }
  if (d.status !== "APPROVED" && d.status !== "SCHEDULED") {
    throw new HttpsError("failed-precondition", "승인 완료된 교류전만 일정을 정할 수 있어요.");
  }
  await ref.update({
    scheduledDate: date,
    scheduledTime: time,
    place,
    status: "SCHEDULED",
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  return { ok: true };
});

export const recordDuelResult = onCall({ region: "asia-northeast3" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Login required");
  const uid = request.auth.uid;
  const data = request.data ?? {};
  const matchId = (data.matchId as string | undefined)?.trim();
  const isDraw = data.isDraw === true;
  const winnerMemberId = (data.winnerMemberId as string | undefined)?.trim();
  if (!matchId) throw new HttpsError("invalid-argument", "matchId required");
  if (!isDraw && !winnerMemberId) throw new HttpsError("invalid-argument", "winnerMemberId or isDraw required");
  const caller = await loadApprovedCaller(uid);
  const db = admin.firestore();
  const ref = db.doc(`exchangeMatches/${matchId}`);
  const snap = await ref.get();
  const d = snap.data();
  if (!d) throw new HttpsError("not-found", "교류전을 찾을 수 없어요.");
  if (!isGymStaff(caller, d.requesterGymId) && !isGymStaff(caller, d.opponentGymId)) {
    throw new HttpsError("permission-denied", "운영진만 결과를 기록할 수 있어요.");
  }
  if (d.status === "COMPLETED") throw new HttpsError("failed-precondition", "이미 결과가 기록됐어요.");
  const p1 = d.requesterMemberId as string;
  const p2 = d.opponentMemberId as string;
  if (!isDraw && winnerMemberId !== p1 && winnerMemberId !== p2) {
    throw new HttpsError("invalid-argument", "승자는 교류전 참가자여야 해요.");
  }
  const inc = admin.firestore.FieldValue.increment(1);
  const batch = db.batch();
  batch.update(ref, {
    status: "COMPLETED",
    isDraw,
    winnerMemberId: isDraw ? null : winnerMemberId,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  if (isDraw) {
    batch.update(db.doc(`members/${p1}`), { exchangeDraws: inc });
    batch.update(db.doc(`members/${p2}`), { exchangeDraws: inc });
  } else {
    const loser = winnerMemberId === p1 ? p2 : p1;
    batch.update(db.doc(`members/${winnerMemberId}`), { exchangeWins: inc });
    batch.update(db.doc(`members/${loser}`), { exchangeLosses: inc });
  }
  await batch.commit();
  logger.info("[recordDuelResult] done", { id: matchId, isDraw, winner: winnerMemberId ? maskId(winnerMemberId) : null });
  return { ok: true };
});

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
 * 회원 재가입 — 탈퇴(`status=LEFT`) 상태의 회원이 다시 카카오 로그인 시 호출.
 *
 * Firebase Auth uid 가 같으면 `members/{uid}` 문서가 그대로 남아있어 `completeSignup` 으로는
 * "이미 가입됨" 으로 분기해 새 doc 생성 못함. 본 함수가 LEFT → PENDING/APPROVED 로 reactivate.
 *
 * 동작:
 *  - status 가 LEFT 가 아니면 no-op (alreadyActive: true)
 *  - LEFT 면 [matchRole] 로 역할 재평가 (allowlist 변경 가능성 대비) → role 갱신 + 적절한 status
 *  - 기존 출석/스킬/코멘트 등 서브컬렉션은 보존 — 회원 자산 복구
 */
export const rejoinMembership = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Login required");
    }
    const uid = request.auth.uid;
    const docRef = admin.firestore().doc(`members/${uid}`);
    const snap = await docRef.get();
    if (!snap.exists) {
      throw new HttpsError(
        "not-found",
        "Member doc not found. Use completeSignup for fresh signup.",
      );
    }
    const data = snap.data()!;
    if (data.status !== "LEFT") {
      return { ok: true, alreadyActive: true, status: data.status };
    }
    const name = data.name as string;
    const evaluatedRole = matchRole(name);
    const newStatus = ["CREATOR", "MASTER", "COACH"].includes(evaluatedRole)
      ? "APPROVED"
      : "PENDING";
    await docRef.update({
      status: newStatus,
      role: evaluatedRole,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    return { ok: true, status: newStatus, role: evaluatedRole };
  },
);

/**
 * RoleAllowlist — Android 클라이언트의 [RoleAllowlist.kt] 와 동일한 명단을 server 가 보유.
 * 양쪽 동기화 필요. 추후 Firestore `roleAllowlist` 컬렉션으로 이전 검토.
 */
/**
 * PII 마스킹 — Cloud Functions logger 출력 시 사용. logcat 보다 접근 제한이 강하지만
 * 운영 환경에선 GCP Logs Explorer 권한자가 다수일 수 있어 원본 PII 노출을 차단.
 *
 * 정책:
 * - name/nickname: 첫 글자 + `*`. 길이 1 이면 `*` 만.
 * - 식별자(uid): 앞 3 / 뒤 3 + 길이. 6 자 이하면 그대로 (보통 짧은 id 는 의미 추적용).
 */
function maskName(raw: string | undefined | null): string {
  if (raw == null) return "<null>";
  if (raw.length === 0) return "<blank>";
  if (raw.length === 1) return "*";
  if (raw.length === 2) return `${raw[0]}*`;
  return `${raw[0]}${"*".repeat(raw.length - 2)}${raw[raw.length - 1]}`;
}

function maskId(raw: string | undefined | null): string {
  if (raw == null) return "<null>";
  if (raw.length <= 6) return raw;
  return `${raw.slice(0, 3)}...${raw.slice(-3)} (len=${raw.length})`;
}

function matchRole(name: string): "CREATOR" | "MASTER" | "COACH" | "MEMBER" {
  const CREATORS = new Set(["이지연"]);
  const MASTERS = new Set(["김파시"]);
  const COACHES = new Set<string>([]);

  if (CREATORS.has(name)) return "CREATOR";
  if (MASTERS.has(name)) return "MASTER";
  if (COACHES.has(name)) return "COACH";
  return "MEMBER";
}

/**
 * 카카오 OctaLink 앱 ID — 콘솔 [내 애플리케이션 → 앱 설정 → 일반 → 앱 ID] 에 표시.
 * SET 의 `aud` claim 검증용 (스푸핑 요청 차단). 0 이면 검증 skip.
 */
const KAKAO_APP_ID = 1455062;

/**
 * 카카오 이벤트 스키마 URI prefix — Security Event Token (SET, RFC 8417) 표준.
 * 카카오 콘솔에 표시되는 풀 URI: `https://schemas.openid.net/secevent/oauth/event-type/{name}`.
 * 핸들러는 prefix 제거 후 마지막 segment(`user-unlinked`, `user-scope-withdraw` 등) 로 분기.
 */
const KAKAO_EVENT_PREFIX = "https://schemas.openid.net/secevent/oauth/event-type/";

/**
 * 카카오 계정 상태 변경 웹훅 핸들러 — 카카오가 HTTP POST 로 **JWT 본문 (SET 형식)** 전송.
 *
 * Body 형식 (RFC 8417 Security Event Token):
 *   - Content-Type: `application/secevent+jwt`
 *   - Body: JWT 문자열 (header.payload.signature 의 base64url 3 분할)
 *   - Payload claims:
 *     - `iss`, `aud`, `iat`, `jti`: 표준 JWT 클레임 (aud = 카카오 앱 ID)
 *     - `events`: `{ "<schema URI>": { ...event-specific data... } }` 형태
 *     - `sub_id` / `subject`: 영향 받는 사용자 식별자 (Kakao user_id)
 *
 * 카카오 콘솔 등록 절차:
 *   1. 콘솔 → 내 애플리케이션 → 카카오 로그인 → 계정 상태 변경 웹훅 설정
 *   2. 상태: 사용함, 웹훅 URL: 본 함수 배포 URL
 *   3. 변경 이벤트 (OAuth 탭) — 최소 **User Unlinked** + 권장 **User Scope Withdraw**
 *
 * 처리:
 *   - `user-unlinked` → MemberDoc status=LEFT 전환 + PII 필드 즉시 삭제
 *   - `user-scope-withdraw` → 철회된 scope 에 매핑된 필드만 삭제 (status 유지)
 *   - 기타 → 로깅만
 *
 * 보안 노트: 현재는 JWT 서명 검증 skip — 운영 환경에선 https://kauth.kakao.com/.well-known/jwks.json
 * 로 JWKS 받아 signature 검증 필요. `aud` claim + `KAKAO_APP_ID` 검증으로 1차 방어.
 */
export const kakaoAccountWebhook = onRequest(
  { region: "asia-northeast3", invoker: "public" },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).send("Method Not Allowed");
      return;
    }

    const contentType = req.get("content-type") ?? "";
    // rawBody 우선 — `application/secevent+jwt` 는 Express body parser 가 처리 안 함.
    // JSON 직접 전송 시 req.body 가 이미 객체.
    const raw = req.rawBody?.toString("utf8") ?? "";
    const claims = parseSetPayload(raw, req.body, contentType);
    if (!claims) {
      logger.warn(`[kakaoWebhook] SET 파싱 실패 — content-type=${contentType}, raw.len=${raw.length}`);
      res.status(200).send("ok");
      return;
    }
    logger.info("[kakaoWebhook] SET claims", {
      iss: claims.iss,
      aud: claims.aud,
      iat: claims.iat,
      jti: claims.jti,
      eventKeys: Object.keys(claims.events ?? {}),
    });

    // aud (앱 ID) 검증
    if (KAKAO_APP_ID && claims.aud && Number(claims.aud) !== KAKAO_APP_ID) {
      logger.warn(`[kakaoWebhook] aud mismatch: expected ${KAKAO_APP_ID}, got ${claims.aud}`);
      res.status(200).send("ok");
      return;
    }

    // 페이로드 root 의 sub_id / subject 에서 사용자 id 추출 (이벤트별 subject 가 더 우선)
    const rootUserId = extractKakaoUserId(claims.sub_id ?? claims.subject);

    const events = (claims.events ?? {}) as Record<string, unknown>;
    for (const [eventUri, rawData] of Object.entries(events)) {
      const eventName = eventUri.startsWith(KAKAO_EVENT_PREFIX)
        ? eventUri.slice(KAKAO_EVENT_PREFIX.length)
        : eventUri;
      const data = (rawData ?? {}) as Record<string, unknown>;
      const eventUserId = extractKakaoUserId(data.subject) ?? rootUserId;
      if (!eventUserId) {
        logger.warn(`[kakaoWebhook] ${eventName}: user id 추출 실패`, data);
        continue;
      }
      const uid = `kakao:${eventUserId}`;

      try {
        if (eventName === "user-unlinked") {
          await handleUserUnlinked(uid);
        } else if (eventName === "user-scope-withdraw") {
          const scopes = Array.isArray(data.scopes) ? (data.scopes as string[]) : [];
          await handleScopeWithdraw(uid, scopes);
        } else {
          logger.info(`[kakaoWebhook] ${eventName} uid=${uid}: no-op`);
        }
      } catch (e) {
        logger.error(`[kakaoWebhook] ${eventName} uid=${uid}: handler failed`, e);
      }
    }

    res.status(200).send("ok");
  },
);

/**
 * SET 페이로드 추출 — JWT (Content-Type `application/secevent+jwt`) 또는 JSON 양쪽 지원.
 * JWT 서명 검증은 별도 (현재 skip).
 */
function parseSetPayload(
  raw: string,
  parsedBody: unknown,
  contentType: string,
): Record<string, any> | null {
  // 1) JWT (Content-Type: application/secevent+jwt) — raw 가 `xxx.yyy.zzz` 형식
  if (raw && /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(raw.trim())) {
    try {
      const payload = raw.trim().split(".")[1];
      const json = Buffer.from(payload, "base64url").toString("utf8");
      return JSON.parse(json);
    } catch (e) {
      logger.error("[kakaoWebhook] JWT decode 실패", e);
      return null;
    }
  }
  // 2) JSON 본문 (Express body parser 가 이미 파싱)
  if (parsedBody && typeof parsedBody === "object" && !Buffer.isBuffer(parsedBody)) {
    return parsedBody as Record<string, any>;
  }
  // 3) JSON 문자열 (raw)
  if (raw) {
    try {
      return JSON.parse(raw);
    } catch {
      logger.warn(`[kakaoWebhook] raw JSON parse 실패 — content-type=${contentType}`);
    }
  }
  return null;
}

/**
 * SET subject 객체에서 카카오 user_id 추출.
 * 표준 형식: `{ format: "iss_sub" or "opaque", iss: ..., sub: "<kakao user id>" }`
 * 또는 단순히 `{ sub: "..." }`.
 */
function extractKakaoUserId(subject: unknown): string | null {
  if (!subject || typeof subject !== "object") return null;
  const s = subject as Record<string, unknown>;
  const candidate = s.sub ?? s.user_id ?? s.id;
  return candidate == null ? null : String(candidate);
}

/**
 * 카카오 연결 해제 — 회원 doc 을 LEFT 로 전환하고 PII 필드 즉시 삭제.
 * 출석/스킬/코멘트 등 운영 자료는 보존 (별도 요청 시 관장이 수동 삭제).
 */
async function handleUserUnlinked(uid: string): Promise<void> {
  const docRef = admin.firestore().doc(`members/${uid}`);
  const snap = await docRef.get();
  if (!snap.exists) {
    logger.info(`[kakaoWebhook] unlinked uid=${uid}: member doc not found, skip`);
    return;
  }
  const FieldValue = admin.firestore.FieldValue;
  await docRef.update({
    status: "LEFT",
    phone: FieldValue.delete(),
    email: FieldValue.delete(),
    gender: FieldValue.delete(),
    ageRange: FieldValue.delete(),
    birthday: FieldValue.delete(),
    birthyear: FieldValue.delete(),
    unlinkedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
  logger.info(`[kakaoWebhook] unlinked uid=${uid}: status=LEFT, PII cleared`);
}

/** Kakao scope ID → MemberDoc 필드 매핑. */
const SCOPE_TO_FIELD: Record<string, string> = {
  phone_number: "phone",
  account_email: "email",
  gender: "gender",
  age_range: "ageRange",
  birthday: "birthday",
  birthyear: "birthyear",
  name: "name", // 단, name 삭제는 회원 식별 불가 야기 → 정책상 보수적으로 skip 가능
};

/**
 * 사용자가 특정 scope 동의 철회 — 해당 PII 필드만 삭제. status 는 유지.
 */
async function handleScopeWithdraw(uid: string, scopes: string[]): Promise<void> {
  if (scopes.length === 0) {
    logger.info(`[kakaoWebhook] scope.withdraw uid=${uid}: empty scopes, skip`);
    return;
  }
  const docRef = admin.firestore().doc(`members/${uid}`);
  const snap = await docRef.get();
  if (!snap.exists) {
    logger.info(`[kakaoWebhook] scope.withdraw uid=${uid}: member doc not found, skip`);
    return;
  }
  const FieldValue = admin.firestore.FieldValue;
  const updates: Record<string, unknown> = {
    updatedAt: FieldValue.serverTimestamp(),
  };
  let cleared = 0;
  for (const s of scopes) {
    // name 철회는 회원 식별 불가 → 정책상 처리 안 함 (UX 차원)
    if (s === "name") continue;
    const field = SCOPE_TO_FIELD[s];
    if (field) {
      updates[field] = FieldValue.delete();
      cleared++;
    }
  }
  if (cleared === 0) {
    logger.info(`[kakaoWebhook] scope.withdraw uid=${uid}: no mappable scopes in ${JSON.stringify(scopes)}`);
    return;
  }
  await docRef.update(updates);
  logger.info(`[kakaoWebhook] scope.withdraw uid=${uid}: cleared ${cleared} field(s) for scopes=${JSON.stringify(scopes)}`);
}

// ════════════════════════════════════════════════════════
// 푸시 알림 (FCM) — Firestore 이벤트 트리거 기반 발송
// ════════════════════════════════════════════════════════
//
// 클라이언트 Schema.kt 의 NotificationType enum 과 1:1 매칭 — type 키 값:
//   COMMENT / TOURNAMENT_DRAWN / NEW_NOTICE / SIGNUP_RESULT / SKILL_UPDATED
//   (CLASS_REMINDER 는 클라이언트 WorkManager 가 직접 fire — 서버 트리거 없음)
//
// payload 구조: data.{ type, title, body } + notification.{ title, body }.
//   - notification 필드: foreground/background 모두 OS 가 표시 가능. iOS 호환.
//   - data 필드: OctaLinkMessagingService.onMessageReceived 가 type 으로 채널 라우팅 + prefs 필터.
//
// prefs 필터 — 본인이 ProfileScreen 에서 OFF 한 알림은 발송 자체를 skip.

type NotificationTypeKey =
  | "COMMENT"
  | "TOURNAMENT_DRAWN"
  | "NEW_NOTICE"
  | "SIGNUP_RESULT"
  | "SKILL_UPDATED"
  | "NEW_POST_COMMENT"
  | "MENTION"
  // 운영진(MASTER/CREATOR) 전용 — 검토 큐 신규 항목 알림.
  | "NEW_SIGNUP_PENDING"
  | "NEW_SKILL_PROPOSAL"
  // 교류전 — 결투 신청 시 상대 + 양측 운영진에게.
  | "DUEL_REQUESTED";

/** [NotificationType.defaultEnabled] 과 일치 — 클라이언트에서 prefs 키 누락 시 기본값. */
const DEFAULT_ENABLED: Record<NotificationTypeKey, boolean> = {
  COMMENT: true,
  TOURNAMENT_DRAWN: true,
  NEW_NOTICE: true,
  SIGNUP_RESULT: true,
  SKILL_UPDATED: true,
  NEW_POST_COMMENT: true,
  MENTION: true,
  NEW_SIGNUP_PENDING: true,
  NEW_SKILL_PROPOSAL: true,
  DUEL_REQUESTED: true,
};

/**
 * 클라이언트 [NotificationType.channelId] 와 정확히 일치 — Android 8.0+ 의 NotificationChannel.
 * notification payload 에 함께 보내야 background/killed 상태에서 OS auto-display 시 올바른 채널로
 * routing 됨. 누락 시 OS 가 default(없는) 채널로 dispatch 후 silently drop 하는 케이스 발생.
 */
const CHANNEL_ID: Record<NotificationTypeKey, string> = {
  COMMENT: "octalink_comment",
  TOURNAMENT_DRAWN: "octalink_tournament",
  NEW_NOTICE: "octalink_notice",
  SIGNUP_RESULT: "octalink_signup",
  SKILL_UPDATED: "octalink_skill",
  NEW_POST_COMMENT: "octalink_post_comment",
  MENTION: "octalink_mention",
  NEW_SIGNUP_PENDING: "octalink_admin_signup_pending",
  NEW_SKILL_PROPOSAL: "octalink_admin_skill_proposal",
  DUEL_REQUESTED: "octalink_duel",
};

/**
 * APPROVED 상태인 MASTER + CREATOR 들의 회원 id 셋 조회 — 운영진 검토 큐 알림 발송용.
 * COACH 는 제외 (가입 승인 / 스킬 검토 권한은 isMaster() 만 — rules 가 강제).
 */
async function fetchMasterIds(): Promise<string[]> {
  const snap = await admin.firestore()
    .collection("members")
    .where("status", "==", "APPROVED")
    .where("role", "in", ["MASTER", "CREATOR"])
    .get();
  return snap.docs.map((d) => d.id);
}

/**
 * 주어진 memberId 들 중 (a) fcmToken 보유 + (b) 해당 type prefs 가 ON 인 사람만 추려서
 * FCM multicast 발송. 401(invalid token) 응답은 자동 토큰 정리 (members/{uid}.fcmToken = null).
 */
async function sendNotificationTo(
  memberIds: string[],
  type: NotificationTypeKey,
  title: string,
  body: string,
): Promise<void> {
  if (memberIds.length === 0) return;
  const db = admin.firestore();
  const defaultEnabled = DEFAULT_ENABLED[type];

  // distinct id → 토큰/uid 쌍 수집. 빠진 doc / 토큰 없음 / pref OFF 는 모두 skip.
  // 각 단계 drop 시 명시적 로깅 — 클라이언트 미수신 진단 시 추적 가능.
  const idTokenPairs: Array<{ uid: string; token: string }> = [];
  await Promise.all(
    Array.from(new Set(memberIds)).map(async (memberId) => {
      const snap = await db.collection("members").doc(memberId).get();
      const data = snap.data();
      if (!data) {
        logger.info(`[fcm] type=${type} skip ${memberId} — member doc 없음`);
        return;
      }
      const token = data.fcmToken as string | undefined;
      if (!token) {
        logger.info(`[fcm] type=${type} skip ${memberId} — fcmToken 없음`);
        return;
      }
      const prefs = (data.notificationPrefs as Record<string, boolean> | undefined) ?? {};
      const enabled = prefs[type] !== undefined ? prefs[type] : defaultEnabled;
      if (!enabled) {
        logger.info(`[fcm] type=${type} skip ${memberId} — prefs[${type}]=false`);
        return;
      }
      logger.info(`[fcm] type=${type} → ${memberId} token=${token.substring(0, 16)}...`);
      idTokenPairs.push({ uid: memberId, token });
    }),
  );
  if (idTokenPairs.length === 0) {
    logger.info(`[fcm] type=${type} — 0 recipients after filter (sent to none of ${memberIds.length})`);
    return;
  }

  const response = await admin.messaging().sendEachForMulticast({
    tokens: idTokenPairs.map((p) => p.token),
    data: { type, title, body },
    notification: { title, body },
    android: {
      priority: "high",
      notification: {
        // 이 채널 id 가 클라이언트의 NotificationChannel 등록 id (`octalink_*`) 와 정확히 일치해야
        // background/killed 상태에서 OS auto-display 가 옳은 채널로 routing 됨.
        channelId: CHANNEL_ID[type],
      },
    },
  });
  logger.info(`[fcm] type=${type} sent=${response.successCount}/${idTokenPairs.length} failed=${response.failureCount}`);

  // invalid token cleanup — Firebase 가 unregistered/invalid-argument 응답 시 token 폐기.
  const cleanupPromises: Promise<unknown>[] = [];
  response.responses.forEach((r, i) => {
    if (r.success) return;
    const errCode = r.error?.code ?? "";
    if (errCode === "messaging/registration-token-not-registered" || errCode === "messaging/invalid-argument") {
      const { uid } = idTokenPairs[i];
      logger.info(`[fcm] cleanup stale token uid=${uid} reason=${errCode}`);
      cleanupPromises.push(db.collection("members").doc(uid).update({ fcmToken: null }));
    } else {
      logger.warn(`[fcm] send error uid=${idTokenPairs[i].uid} code=${errCode}`);
    }
  });
  if (cleanupPromises.length > 0) await Promise.all(cleanupPromises);
}

/**
 * 한 줄 코멘트 — 운영진이 회원에게 작성. `members/{memberId}/comments/{commentId}` create 시 본인에게 알림.
 */
export const notifyOnCommentCreated = onDocumentCreated(
  {
    document: "members/{memberId}/comments/{commentId}",
    region: "asia-northeast3",
  },
  async (event) => {
    const data = event.data?.data();
    if (!data) return;
    const memberId = event.params.memberId;
    const byName = (data.byMasterName as string | undefined) ?? "운영진";
    const text = (data.text as string | undefined) ?? "";
    const snippet = text.length > 60 ? text.slice(0, 60) + "…" : text;
    await sendNotificationTo(
      [memberId],
      "COMMENT",
      `${byName} 한 줄 코멘트`,
      snippet,
    );
  },
);

/**
 * 토너먼트 추첨 — `tournaments/{tournamentId}` create 시 모든 참가자에게.
 * matches 서브컬렉션 은 같은 batch 에서 작성되므로 doc 생성 직후 read 가능.
 */
export const notifyOnTournamentCreated = onDocumentCreated(
  {
    document: "tournaments/{tournamentId}",
    region: "asia-northeast3",
  },
  async (event) => {
    const data = event.data?.data();
    if (!data) return;
    const tournamentId = event.params.tournamentId;
    const title = (data.title as string | undefined) ?? "토너먼트";
    const matchesSnap = await admin.firestore()
      .collection(`tournaments/${tournamentId}/matches`)
      .get();
    const participantIds = Array.from(new Set(
      matchesSnap.docs.flatMap((d) => {
        const md = d.data();
        return [md.redMemberId, md.blueMemberId];
      }).filter((id): id is string => typeof id === "string" && id.length > 0),
    ));
    if (participantIds.length === 0) {
      logger.info(`[fcm] tournament ${tournamentId} has no participants — skip`);
      return;
    }
    await sendNotificationTo(
      participantIds,
      "TOURNAMENT_DRAWN",
      "토너먼트 추첨 완료",
      `${title} 대진표가 업데이트되었습니다.`,
    );
  },
);

/**
 * 새 공지 — 운영진이 NOTICE 태그로 글 작성 시 모든 APPROVED 회원(작성자 제외)에게.
 */
export const notifyOnNoticeCreated = onDocumentCreated(
  {
    document: "posts/{postId}",
    region: "asia-northeast3",
  },
  async (event) => {
    const data = event.data?.data();
    if (!data) return;
    if (data.tag !== "NOTICE") return;
    const authorId = data.authorId as string | undefined;
    const titleText = (data.title as string | undefined) ?? "";
    const body = (data.body as string | undefined) ?? "";
    const snippet = body.length > 80 ? body.slice(0, 80) + "…" : body;

    // 모든 APPROVED 회원 fetch (작성자 제외).
    const membersSnap = await admin.firestore()
      .collection("members")
      .where("status", "==", "APPROVED")
      .get();
    const targetIds = membersSnap.docs
      .map((d) => d.id)
      .filter((id) => id !== authorId);
    await sendNotificationTo(
      targetIds,
      "NEW_NOTICE",
      titleText.trim().length > 0 ? `[공지] ${titleText}` : "새 공지",
      snippet,
    );
  },
);

/**
 * 가입 승인 결과 — `members/{memberId}` status 가 PENDING 에서 변경될 때 본인에게.
 * APPROVED / REJECTED 만 노티 (LEFT/SUSPENDED 는 별도 시나리오).
 */
export const notifyOnSignupResult = onDocumentUpdated(
  {
    document: "members/{memberId}",
    region: "asia-northeast3",
  },
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;
    const beforeStatus = before.status as string | undefined;
    const afterStatus = after.status as string | undefined;
    if (beforeStatus !== "PENDING") return;
    if (afterStatus !== "APPROVED" && afterStatus !== "REJECTED") return;

    const memberId = event.params.memberId;
    const approved = afterStatus === "APPROVED";
    await sendNotificationTo(
      [memberId],
      "SIGNUP_RESULT",
      approved ? "가입이 승인되었습니다 🎉" : "가입 신청 결과",
      approved
        ? "이제 OctaLink 의 모든 기능을 사용할 수 있어요."
        : "관장님께 문의 부탁드립니다.",
    );
  },
);

/**
 * 스킬 점수 갱신 — `members/{memberId}.skills` 필드가 변경될 때 본인에게.
 * skills 가 새로 생성(null → set) 되거나 기존 값에서 변경된 경우 모두 포함.
 */
export const notifyOnSkillsUpdated = onDocumentUpdated(
  {
    document: "members/{memberId}",
    region: "asia-northeast3",
  },
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;
    // 깊은 비교 회피 — JSON stringify 로 충분 (Float/Number 동등성 + key order 안정).
    const beforeJson = JSON.stringify(before.skills ?? null);
    const afterJson = JSON.stringify(after.skills ?? null);
    if (beforeJson === afterJson) return;

    const memberId = event.params.memberId;
    await sendNotificationTo(
      [memberId],
      "SKILL_UPDATED",
      "스킬 점수 갱신됨",
      "프로필에서 새 스킬 차트를 확인해보세요.",
    );
  },
);

/**
 * 새 댓글 — `posts/{postId}/postComments/{commentId}` create 시 글 작성자에게 알림.
 * 본인이 본인 글에 댓글 단 경우는 skip (자기 알림 차단).
 */
export const notifyOnPostCommentCreated = onDocumentCreated(
  {
    document: "posts/{postId}/postComments/{commentId}",
    region: "asia-northeast3",
  },
  async (event) => {
    const data = event.data?.data();
    if (!data) return;
    const postId = event.params.postId;
    const commenterId = data.authorId as string | undefined;
    const commenterName = (data.authorName as string | undefined) ?? "회원";
    const body = (data.body as string | undefined) ?? "";
    const snippet = body.length > 60 ? body.slice(0, 60) + "…" : body;

    // 부모 글에서 작성자 id 조회.
    const postSnap = await admin.firestore().collection("posts").doc(postId).get();
    const postAuthorId = postSnap.data()?.authorId as string | undefined;
    if (!postAuthorId) return;
    if (postAuthorId === commenterId) return; // 자기 글에 자기 댓글 — skip

    await sendNotificationTo(
      [postAuthorId],
      "NEW_POST_COMMENT",
      `${commenterName} 님의 댓글`,
      snippet,
    );
  },
);

/**
 * @멘션 알림 + 사진/영상 공개 승인 요청 — `posts/{postId}` create 시.
 *
 *  - 멘션만 (미디어 없음): MENTION 타입. 일반 호명 — 알림으로 인지만.
 *  - 미디어 + 멘션 조합: 글이 PENDING_APPROVAL 로 생성됨. 멘션된 회원에게 승인 요청 알림.
 *
 * 작성자 자신이 멘션 리스트에 있으면 클라이언트에서 제거하지만 안전망으로 서버에서도 한 번 더 거름.
 */
export const notifyOnPostMention = onDocumentCreated(
  {
    document: "posts/{postId}",
    region: "asia-northeast3",
  },
  async (event) => {
    const data = event.data?.data();
    if (!data) return;
    const mentions = (data.mentionedMemberIds as string[] | undefined) ?? [];
    if (mentions.length === 0) return;

    const authorId = data.authorId as string | undefined;
    const authorName = (data.authorName as string | undefined) ?? "회원";
    const visibility = data.visibility as string | undefined;
    const needsApproval = visibility === "PENDING_APPROVAL";

    // 작성자 본인 + 중복 제거.
    const targets = Array.from(new Set(mentions)).filter((id) => id !== authorId);
    if (targets.length === 0) return;

    // 글 제목 — 빈 문자열 허용 스키마라 trim 후 비어있으면 body 에 첨부 안 함.
    const postTitle = ((data.title as string | undefined) ?? "").trim();
    const titleLine = postTitle ? `글 제목: "${postTitle}"` : "";

    const title = needsApproval
      ? `${authorName}님이 사진/영상을 담은 글에서 회원님을 멘션했어요.`
      : `${authorName}님이 커뮤니티에서 회원님을 멘션했어요. :)`;
    const body = needsApproval
      ? `공개 전 승인이 필요해요. 커뮤니티에서 확인해주세요. :)${titleLine ? `\n${titleLine}` : ""}`
      : titleLine;

    await sendNotificationTo(targets, "MENTION", title, body);
  },
);

/**
 * 가입 신청 — `members/{memberId}` create 시 status=PENDING 이면 모든 관장(MASTER/CREATOR)에게.
 *
 * 즉시 APPROVED 로 시작하는 운영진 가입(`RoleAllowlist` 매칭)은 PENDING 이 아니므로 skip.
 * `completeSignup` Cloud Function 이 doc 을 작성할 때 status 가 같이 결정되므로 create 트리거가
 * 의도된 상태를 본다.
 */
export const notifyOnSignupRequest = onDocumentCreated(
  {
    document: "members/{memberId}",
    region: "asia-northeast3",
  },
  async (event) => {
    const data = event.data?.data();
    if (!data) return;
    if (data.status !== "PENDING") return;

    const applicantName = (data.name as string | undefined) ?? "신규 회원";
    const masters = await fetchMasterIds();
    if (masters.length === 0) {
      logger.warn("[notifyOnSignupRequest] no masters to notify");
      return;
    }

    logger.info(`[notifyOnSignupRequest] applicant=${maskName(applicantName)} → masters=${masters.length}`);
    await sendNotificationTo(
      masters,
      "NEW_SIGNUP_PENDING",
      "새 가입 신청",
      `${applicantName} 님이 가입을 신청했어요. 승인 대기 중.`,
    );
  },
);

/**
 * 스킬 점수 검토 요청 — `members/{memberId}/skillScores/{scoreId}` create 시
 * status=PROPOSED 면 모든 관장(MASTER/CREATOR)에게.
 *
 * 관장이 `directApprove` 로 직접 APPROVED 생성한 경우는 PROPOSED 가 아니라 skip.
 * 작성자(코치) 본인이 관장이기도 한 드문 경우엔 자기 알림 회피 위해 masters 에서 제외.
 */
export const notifyOnSkillProposed = onDocumentCreated(
  {
    document: "members/{memberId}/skillScores/{scoreId}",
    region: "asia-northeast3",
  },
  async (event) => {
    const data = event.data?.data();
    if (!data) return;
    if (data.status !== "PROPOSED") return;

    const memberId = event.params.memberId;
    const byUserId = (data.byUserId as string | undefined) ?? "";

    const db = admin.firestore();
    // 평가 대상 회원 이름 (비정규화 안 됨 — doc fetch).
    const memberSnap = await db.collection("members").doc(memberId).get();
    const memberName = (memberSnap.data()?.name as string | undefined) ?? "회원";

    // 작성자 이름 (코치 또는 관장 본인이 PROPOSED 경로 사용한 경우).
    let byName = "코치";
    if (byUserId) {
      const userSnap = await db.collection("members").doc(byUserId).get();
      byName = (userSnap.data()?.name as string | undefined) ?? "코치";
    }

    // MASTER/CREATOR 중 작성자 본인 제외 (자기 알림 회피).
    const masters = (await fetchMasterIds()).filter((id) => id !== byUserId);
    if (masters.length === 0) {
      logger.warn(`[notifyOnSkillProposed] no masters to notify (byUserId=${maskId(byUserId)})`);
      return;
    }

    logger.info(`[notifyOnSkillProposed] target=${maskName(memberName)} by=${maskName(byName)} → masters=${masters.length}`);
    await sendNotificationTo(
      masters,
      "NEW_SKILL_PROPOSAL",
      "새 스킬 점수 검토 요청",
      `${byName} 코치가 ${memberName} 님 스킬 점수를 입력했어요. 검토 대기 중.`,
    );
  },
);

// =============================================================================
// 출석 체크인 — 백도어 차단 (서버 시간 / role / 스케줄 검증)
// =============================================================================

/**
 * 도장 수업 슬롯 — 클라이언트 [Schedule.kt] 와 동일 정의. 변경 시 양쪽 모두 갱신.
 * KST 기준 시작 시각만 검증. 종료 시각은 윈도우 계산에 불필요 (start + 10분이 close).
 */
const WEEKDAY_SLOT_STARTS: Array<{ h: number; m: number }> = [
  { h: 11, m: 0 },
  { h: 12, m: 0 },
  { h: 13, m: 0 },
  { h: 18, m: 0 },
  { h: 19, m: 30 },
  { h: 21, m: 0 },
];
const SATURDAY_SLOT_STARTS: Array<{ h: number; m: number }> = [{ h: 11, m: 0 }];
const CHECK_IN_OPEN_MIN_BEFORE = 30;
const CHECK_IN_CLOSE_MIN_AFTER = 10;

function slotStartsForKstDow(dow: number): Array<{ h: number; m: number }> {
  // dow: 0=Sun, 1=Mon, ..., 6=Sat (KST 기준)
  if (dow === 0) return [];
  if (dow === 6) return SATURDAY_SLOT_STARTS;
  return WEEKDAY_SLOT_STARTS;
}

/**
 * 현재 KST 시각이 어느 슬롯의 체크인 윈도우 (`[start-30분, start+10분]`) 안에 들어가는지 검사.
 * 들어가지 않으면 null, 들어가면 그 슬롯 시작 시각의 KST date string ("YYYY-MM-DD").
 */
function withinCheckInWindow(): { ok: boolean; classDate: string; reason?: string } {
  const nowUtcMs = Date.now();
  const kstMs = nowUtcMs + 9 * 60 * 60 * 1000;
  const kst = new Date(kstMs);
  const dow = kst.getUTCDay();
  const slots = slotStartsForKstDow(dow);
  if (slots.length === 0) {
    return { ok: false, classDate: "", reason: "휴무일에는 체크인할 수 없어요." };
  }

  const hour = kst.getUTCHours();
  const minute = kst.getUTCMinutes();
  const nowMin = hour * 60 + minute;

  for (const slot of slots) {
    const slotMin = slot.h * 60 + slot.m;
    if (nowMin >= slotMin - CHECK_IN_OPEN_MIN_BEFORE && nowMin <= slotMin + CHECK_IN_CLOSE_MIN_AFTER) {
      const y = kst.getUTCFullYear();
      const mo = String(kst.getUTCMonth() + 1).padStart(2, "0");
      const d = String(kst.getUTCDate()).padStart(2, "0");
      return { ok: true, classDate: `${y}-${mo}-${d}` };
    }
  }
  return { ok: false, classDate: "", reason: "지금은 체크인 가능 시간이 아니에요. 수업 시작 30분 전 ~ 10분 후 사이에 다시 시도해주세요." };
}

/**
 * 출석 체크인 — 클라이언트 직접 Firestore write 는 rules 에서 차단되고, 이 함수만 통과.
 *
 *  - 본인 uid 명의로 `members/{uid}/attendance/{classDate}` 생성 (set with admin SDK).
 *  - 서버 시각으로 슬롯 윈도우 검증 → 백도어 (UI 우회) 차단.
 *  - role=MASTER 는 상시 운영 인력이라 체크인 자체가 무의미 → 거부.
 *  - 같은 날 재호출은 set() 으로 checkInAt 갱신 (취소 후 재체크인 케이스).
 */
export const recordAttendance = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Login required");
    }
    const uid = request.auth.uid;
    const classDefId = (request.data?.classDefId as string | undefined) ?? "default";
    logger.info("[recordAttendance] entry", { uid: maskId(uid), classDefId });

    const db = admin.firestore();
    const memberSnap = await db.collection("members").doc(uid).get();
    const member = memberSnap.data();
    if (!member) {
      throw new HttpsError("not-found", "회원 정보를 찾지 못했어요.");
    }
    if (member.status !== "APPROVED") {
      throw new HttpsError("permission-denied", "승인된 회원만 체크인할 수 있어요.");
    }
    if (member.role === "MASTER") {
      throw new HttpsError("permission-denied", "관장은 상시 운영이라 별도 체크인이 없어요.");
    }

    const window = withinCheckInWindow();
    if (!window.ok) {
      logger.warn("[recordAttendance] window closed", { uid: maskId(uid), reason: window.reason });
      throw new HttpsError("failed-precondition", window.reason ?? "체크인 윈도우가 닫혀있어요.");
    }

    const docRef = db.doc(`members/${uid}/attendance/${window.classDate}`);
    await docRef.set({
      id: window.classDate,
      memberId: uid,
      classDefId,
      classDate: window.classDate,
      checkInAt: admin.firestore.FieldValue.serverTimestamp(),
      verified: false,
    });
    logger.info("[recordAttendance] saved", { uid: maskId(uid), classDate: window.classDate });
    return { ok: true, classDate: window.classDate };
  },
);

/**
 * 출석 체크인 취소 — 동일 권한 모델(본인). admin SDK 로 doc 삭제.
 * 윈도우 검증은 안 함 (취소는 언제든 가능). 같은 날 doc 만 삭제 (`classDate` = today KST).
 */
export const cancelAttendance = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Login required");
    }
    const uid = request.auth.uid;
    const nowKst = new Date(Date.now() + 9 * 60 * 60 * 1000);
    const y = nowKst.getUTCFullYear();
    const mo = String(nowKst.getUTCMonth() + 1).padStart(2, "0");
    const d = String(nowKst.getUTCDate()).padStart(2, "0");
    const classDate = `${y}-${mo}-${d}`;

    await admin.firestore().doc(`members/${uid}/attendance/${classDate}`).delete();
    logger.info("[cancelAttendance] deleted", { uid: maskId(uid), classDate });
    return { ok: true, classDate };
  },
);

// =============================================================================
// AI 주간 보강 루틴 — Phase 1 MVP
// =============================================================================

/** 6축 한국어 ↔ Schema 필드 매핑 (prompt 입력 + LLM 출력의 targetAxis 검증용). */
const AXIS_FIELDS = [
  "striking",
  "grappling",
  "stamina",
  "technique",
  "mental",
  "speed",
] as const;
const AXIS_LABEL_KO: Record<string, string> = {
  striking: "스트라이킹",
  grappling: "그래플링",
  stamina: "체력",
  technique: "기술",
  mental: "멘탈",
  speed: "스피드",
};

/**
 * 신규 회원이 EmptyRoutineCard 에서 입력한 6축 자가 점수의 sanitizer.
 *
 *  - 객체가 아니거나 비어있으면 null.
 *  - AXIS_FIELDS 화이트리스트만 통과 (알 수 없는 키 무시).
 *  - 숫자 + 유한값만 허용, [0, 1] 클램프.
 *  - 결과가 빈 객체면 null 반환 → 호출부에서 "없음" 으로 처리.
 *
 * 이 값은 *프롬프트와 focusAxes 계산* 에만 사용되며 members/{id}.skills 에는 절대 쓰지 않는다.
 */
function sanitizeSelfRatedSkills(raw: unknown): Record<string, number> | null {
  if (!raw || typeof raw !== "object") return null;
  const src = raw as Record<string, unknown>;
  const out: Record<string, number> = {};
  for (const axis of AXIS_FIELDS) {
    const v = src[axis];
    if (typeof v !== "number" || !isFinite(v)) continue;
    out[axis] = Math.max(0, Math.min(1, v));
  }
  return Object.keys(out).length === 0 ? null : out;
}

/**
 * ISO 8601 주키 — "2026-W22" 형식.
 * 월요일 시작, 한 해의 첫 목요일이 속한 주가 1주차.
 * Cloud Function 호출 시점의 KST 기준으로 계산.
 */
function isoWeekKey(now = new Date()): string {
  const kstMs = now.getTime() + 9 * 60 * 60 * 1000;
  const d = new Date(kstMs);
  d.setUTCHours(0, 0, 0, 0);
  // 목요일로 이동 (ISO 주 규칙).
  d.setUTCDate(d.getUTCDate() + 4 - (d.getUTCDay() || 7));
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  const weekNo = Math.ceil(
    ((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7,
  );
  return `${d.getUTCFullYear()}-W${String(weekNo).padStart(2, "0")}`;
}

/** 입관 개월 수 (요청 시점 기준). joinDate 가 잘못된 형식이면 0. */
function monthsSinceJoin(joinDate: string | undefined): number {
  if (!joinDate) return 0;
  const join = new Date(joinDate);
  if (isNaN(join.getTime())) return 0;
  const now = new Date();
  return Math.max(
    0,
    (now.getFullYear() - join.getFullYear()) * 12 +
      (now.getMonth() - join.getMonth()),
  );
}

// ExerciseDB 의존 제거 (2026-05): 데이터셋이 웨이트/맨몸 위주라 MMA 동작 매칭이 거의 실패.
// 대안으로 LLM 이 영문 YouTube 검색어를 직접 생성하고 Cloud Function 에서
// YouTube Data API v3 `search.list` 로 상위 1개 videoId 매핑. 클라이언트는 그 videoId 로
// hqdefault 썸네일 표시 + 탭 시 watch URL 진입.

/**
 * YouTube `search.list` 단건 호출 — 검색어 → 상위 1개 영상의 videoId (없으면 null).
 * 비용: 1회 = 100 quota units.
 */
async function youtubeSearchOnce(q: string, apiKey: string): Promise<string | null> {
  try {
    // videoEmbeddable=true / safeSearch=strict 은 MMA / 격투기 영상 다수 거름.
    // Phase 1 은 watch URL 외부 진입(앱 임베드 X)이라 embeddable 필터 불필요. safeSearch=moderate 로 완화.
    const url = "https://www.googleapis.com/youtube/v3/search?" +
      new URLSearchParams({
        part: "snippet",
        q,
        type: "video",
        maxResults: "1",
        safeSearch: "moderate",
        relevanceLanguage: "en",
        key: apiKey,
      }).toString();
    const res = await fetch(url);
    if (!res.ok) {
      logger.warn("[youtube] search not ok", { q, status: res.status });
      return null;
    }
    const body = (await res.json()) as { items?: Array<{ id?: { videoId?: string } }> };
    return body.items?.[0]?.id?.videoId ?? null;
  } catch (e) {
    logger.error("[youtube] search threw", { q, err: String(e) });
    return null;
  }
}

/**
 * 검색어를 넓혀 결과 0건 회피용 fallback 쿼리 생성.
 *  - "solo" 는 과도하게 좁히는 주범이라 제거.
 *  - 너무 긴 쿼리(>4단어)는 핵심 앞 4단어만 — 동작 유형 + 기본 키워드 위주.
 * 원본과 동일하면 빈 문자열 (재시도 불필요 신호).
 */
function broadenYoutubeQuery(query: string): string {
  const noSolo = query.replace(/\bsolo\b/gi, " ").replace(/\s+/g, " ").trim();
  const words = noSolo.split(" ").filter(Boolean);
  const broad = words.length > 4 ? words.slice(0, 4).join(" ") : noSolo;
  return broad === query.trim() ? "" : broad;
}

/**
 * 검색어 → videoId. 1차 검색이 0건이면 broaden 쿼리로 1회 재시도해 썸네일 누락(빨강 ▶ 박스) 최소화.
 * 실패 (quota 초과 / 키 오류 / 네트워크) 시 null 반환 → 클라이언트가 검색 폴백.
 */
async function searchYouTubeTopVideoId(query: string, apiKey: string): Promise<string | null> {
  const q = query.trim();
  if (!q || !apiKey) return null;

  const primary = await youtubeSearchOnce(q, apiKey);
  if (primary) return primary;

  const broad = broadenYoutubeQuery(q);
  if (broad) {
    const retry = await youtubeSearchOnce(broad, apiKey);
    if (retry) {
      logger.info("[youtube] broadened hit", { q, broad });
      return retry;
    }
  }
  logger.warn("[youtube] no results", { q, broad });
  return null;
}

/** Gemini 응답을 안전하게 파싱 — JSON 구조 보장 안 되면 null. */
interface LlmDrill {
  koName: string;
  /** 영문 YouTube 검색어 — LLM 출력. Phase 1 의 시각 자료 단일 소스. */
  youtubeQuery: string;
  desc: string;
  sets: string;
  durationMin: number;
  targetAxis: string;
}
interface LlmDay {
  day: string;
  title: string;
  drills: LlmDrill[];
}
interface LlmRoutine {
  focusSkills: string[];
  weeklyFeedback: string;
  days: LlmDay[];
}

function parseLlmRoutine(text: string): LlmRoutine | null {
  try {
    const parsed = JSON.parse(text);
    if (!parsed || !Array.isArray(parsed.days)) return null;
    return parsed as LlmRoutine;
  } catch (e) {
    logger.warn("[generateWeeklyRoutine] LLM JSON parse failed", { err: String(e), head: text.slice(0, 200) });
    return null;
  }
}

/**
 * 회원의 이번 주 AI 코치의 맞춤 루틴 생성/조회.
 *
 * - 요청 본인 또는 회원 본인 uid 매칭. 운영진(MASTER/CREATOR/COACH)도 호출 가능 — 다른 회원 trigger.
 * - 같은 weekId 의 문서가 이미 있으면 force 가 true 가 아닌 한 캐시 반환.
 * - LLM 호출 + ExerciseDB 매핑은 동일 트랜잭션 외부에서 수행 → write 는 1회.
 */
export const generateWeeklyRoutine = onCall(
  { region: "asia-northeast3", timeoutSeconds: 120, secrets: [YOUTUBE_API_KEY] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Login required");
    }
    const callerUid = request.auth.uid;
    const memberId = (request.data?.memberId as string | undefined) ?? callerUid;
    const force = (request.data?.force as boolean | undefined) ?? false;
    const difficultyRaw = (request.data?.difficulty as string | undefined) ?? "INTERMEDIATE";
    const difficulty = ["BEGINNER", "INTERMEDIATE", "ADVANCED"].includes(difficultyRaw)
      ? difficultyRaw : "INTERMEDIATE";
    // 신규 회원이 EmptyRoutineCard 에서 직접 입력한 6축 임시 점수 (운영자 평가 전 fallback).
    // **회원 프로필 members/{id}.skills 에는 절대 쓰지 않음** — 이번 회차 프롬프트/focusAxes 에만 사용.
    const selfRatedSkills = sanitizeSelfRatedSkills(request.data?.selfRatedSkills);
    const weekId = isoWeekKey();
    logger.info("[generateWeeklyRoutine] entry", { caller: maskId(callerUid), memberId: maskId(memberId), weekId, force, difficulty, selfRated: selfRatedSkills != null });

    const db = admin.firestore();

    // 권한 — Phase 1 비공개 베타 (선착순 12명 + CREATOR).
    //  - CREATOR : 모든 memberId 에 대해 호출 가능 (운영자 트리거)
    //  - aiRoutineBeta == true 회원 : 자기 자신 (memberId == callerUid) 에 대해서만 호출 가능
    //  - 그 외 : 거부
    // 클라이언트 [AiRoutineAccess.kt] / Firestore rules `canUseAiRoutine()` 과 동일 게이트.
    const callerSnap = await db.collection("members").doc(callerUid).get();
    const callerData = callerSnap.data();
    const callerRole = callerData?.role as string | undefined;
    const callerAiBeta = callerData?.aiRoutineBeta === true;
    const isCreator = callerRole === "CREATOR";
    const allowed = isCreator || (callerAiBeta && memberId === callerUid);
    if (!allowed) {
      logger.warn("[generateWeeklyRoutine] denied", { caller: maskId(callerUid), role: callerRole, aiBeta: callerAiBeta, memberId: maskId(memberId) });
      throw new HttpsError("permission-denied", "현재 비공개 베타라 선착순 12명만 사용할 수 있어요.");
    }

    const docRef = db.doc(`members/${memberId}/weeklyRoutines/${weekId}`);

    if (!force) {
      const existing = await docRef.get();
      if (existing.exists) {
        logger.info("[generateWeeklyRoutine] cached", { weekId });
        return { weekId, cached: true, doc: existing.data() };
      }
    }

    // 1) 회원 컨텍스트.
    const memberSnap = await db.collection("members").doc(memberId).get();
    if (!memberSnap.exists) {
      throw new HttpsError("not-found", "회원을 찾지 못했어요");
    }
    const member = memberSnap.data() ?? {};
    const skills = member.skills as Record<string, number> | undefined;
    const belt = (member.belt as string | undefined) ?? "WHITE";
    const weightClass = (member.weightClass as string | undefined) ?? "LIGHT";
    const joinDate = member.joinDate as string | undefined;
    const months = monthsSinceJoin(joinDate);
    const gender = (member.gender as string | undefined) ?? null; // "MALE"/"FEMALE"

    // 약축 2개 도출 (점수 낮은 순).
    //  - member.skills 가 있으면 (운영자 평가 완료) 그 값을 사용.
    //  - 없고 selfRatedSkills 가 함께 왔으면 (신규 회원 자가입력) 그 값을 사용.
    //    이 값은 회원 프로필에 절대 영속화되지 않으며 이번 회차 프롬프트에만 살아있다.
    //  - 둘 다 없으면 0.5 평탄화 fallback.
    const skillSource: Record<string, number> | undefined = skills ?? selfRatedSkills ?? undefined;
    const ranked: Array<{ axis: string; v: number }> = AXIS_FIELDS.map((axis) => ({
      axis,
      v: typeof skillSource?.[axis] === "number" ? (skillSource?.[axis] as number) : 0.5,
    })).sort((a, b) => a.v - b.v);
    const focusAxes = ranked.slice(0, 2).map((r) => r.axis);

    // 2) 최근 5건 코멘트.
    const commentsSnap = await db
      .collection(`members/${memberId}/comments`)
      .orderBy("createdAt", "desc")
      .limit(5)
      .get();
    const recentComments = commentsSnap.docs.map((d) => ({
      id: d.id,
      text: (d.data().text as string | undefined) ?? "",
      classDate: (d.data().classDate as string | undefined) ?? "",
    }));

    // 3) Prompt 구성.
    const prompt = buildRoutinePrompt({
      belt,
      weightClass,
      months,
      gender,
      difficulty,
      skills: ranked,
      focusAxes,
      recentComments,
    });
    logger.info("[generateWeeklyRoutine] prompt built", { promptLen: prompt.length, focusAxes, difficulty });

    // 4) Gemini 호출.
    let llmText: string;
    try {
      const result = await ai.models.generateContent({
        model: GEMINI_MODEL,
        contents: prompt,
        config: GEMINI_CONFIG,
      });
      llmText = result.text ?? "";
    } catch (e) {
      logger.error("[generateWeeklyRoutine] Gemini failed", e);
      throw new HttpsError("internal", "AI 호출 실패 — 잠시 후 다시 시도해주세요");
    }
    const llm = parseLlmRoutine(llmText);
    if (!llm) {
      throw new HttpsError("internal", "AI 응답 파싱 실패");
    }

    // 5) 드릴 가공 — 각 youtubeQuery 로 YouTube `search.list` 호출해 상위 1개 videoId 매핑.
    // 실패 (키 누락 / quota 초과 / 결과 0건) 면 videoId = null → 클라이언트가 검색 폴백.
    const apiKey = YOUTUBE_API_KEY.value();
    const enrichedDays = await Promise.all(
      llm.days.map(async (day) => ({
        day: day.day,
        title: day.title,
        drills: await Promise.all(
          (day.drills ?? []).map(async (drill) => {
            const videoId = await searchYouTubeTopVideoId(drill.youtubeQuery, apiKey);
            return {
              koName: drill.koName,
              youtubeQuery: drill.youtubeQuery,
              desc: drill.desc,
              sets: drill.sets,
              durationMin: drill.durationMin,
              targetAxis: drill.targetAxis,
              videoId,
              done: false,
              skipped: false,
            };
          }),
        ),
      })),
    );

    const doc: Record<string, unknown> = {
      weekId,
      generatedAt: admin.firestore.FieldValue.serverTimestamp(),
      focusSkills: llm.focusSkills?.length ? llm.focusSkills : focusAxes,
      referencedCommentIds: recentComments.map((c) => c.id),
      days: enrichedDays,
      weeklyFeedback: llm.weeklyFeedback ?? "",
      difficulty, // 사용자 선택 난이도 — UI 에 칩으로 표시 ("BEGINNER" / "INTERMEDIATE" / "ADVANCED")
    };
    // 운영자 평가 전 신규 회원이 자가입력한 점수가 실제로 채택됐을 때만 흔적을 남김.
    // member.skills 가 이미 있으면 self 값은 무시됐으므로 보존하지 않음.
    if (!skills && selfRatedSkills) {
      doc.selfRatedSkills = selfRatedSkills;
    }
    await docRef.set(doc);
    logger.info("[generateWeeklyRoutine] saved", { weekId, days: enrichedDays.length });

    return { weekId, cached: false, doc };
  },
);

interface PromptArgs {
  belt: string;
  weightClass: string;
  months: number;
  gender: string | null;
  difficulty: string;
  skills: Array<{ axis: string; v: number }>;
  focusAxes: string[];
  recentComments: Array<{ id: string; text: string; classDate: string }>;
}

const DIFFICULTY_LABEL_KO: Record<string, string> = {
  BEGINNER: "초급 (기초 자세 / 가벼운 부하)",
  INTERMEDIATE: "중급 (기본 콤비네이션 / 중간 부하)",
  ADVANCED: "고급 (복합 시퀀스 / 높은 부하 + 카운터 연결)",
};
const GENDER_LABEL_KO: Record<string, string> = {
  MALE: "남성",
  FEMALE: "여성",
};

function buildRoutinePrompt(args: PromptArgs): string {
  const { belt, weightClass, months, gender, difficulty, skills, focusAxes, recentComments } = args;

  const skillLines = skills
    .map((s) => `- ${AXIS_LABEL_KO[s.axis]} (${s.axis}): ${(s.v * 100).toFixed(0)}점`)
    .join("\n");

  const focusLine = focusAxes
    .map((a) => `${AXIS_LABEL_KO[a]}(${a})`)
    .join(", ");

  const commentBlock =
    recentComments.length === 0
      ? "(최근 코멘트 없음)"
      : recentComments
          .map(
            (c, i) =>
              `[${i + 1}] (${c.classDate || "날짜 미상"}) ${c.text}`,
          )
          .join("\n");

  const genderLine = gender ? GENDER_LABEL_KO[gender] ?? gender : "(미입력)";
  const difficultyLine = DIFFICULTY_LABEL_KO[difficulty] ?? difficulty;

  return `당신은 종합격투기(MMA) 도장의 보조 코치입니다. 회원의 6축 스킬 점수와 관장의 한 줄 코멘트를 보고
**회원이 도장에서 혼자 연습할 수 있는** 보강용 개인 루틴(요일별 정확히 2개 드릴, 일별 총 20분 이내)을 제안합니다.
파트너 / 코치가 필요한 드릴은 절대 금지. 단, 도장 비치 기구는 활용 가능.

# 회원 컨텍스트
- 성별: ${genderLine}
- 벨트: ${belt}
- 체급: ${weightClass}
- 입관 ${months}개월
- 사용자 선택 난이도: ${difficultyLine}
- 6축 점수(낮은 순):
${skillLines}
- 우선 보강 축: ${focusLine}

# 관장의 한 줄 코멘트 (최근 5건) — 내부 참고용. 번호([1],[2])는 식별자일 뿐 출력에 쓰지 말 것.
${commentBlock}

# 작성 규칙
1. days 는 **정확히 3개 — 반드시 "월", "수", "금" 셋 다 채울 것** (도장 수업/휴식 요일 가정). 2개 이하로 줄이지 말 것.
2. 각 day.drills 는 **정확히 2개**. day 총 durationMin 합이 **20 이하** (한 드릴당 평균 10분).
3. 우선 보강 축에 60% 이상 시간 배정.
4. **사용자 선택 난이도 (${difficulty}) 에 맞춰 드릴 강도/세트수/복잡도 조정.** 초급은 단일 동작 반복, 중급은 2~3동작 콤비, 고급은 카운터/체이닝 시퀀스.
5. 성별이 명시되어 있으면 (남성/여성) 체급 + 성별 평균 신체 조건을 가볍게 반영 — 무리한 부하 금지.
6. **드릴은 MMA 종목 위주로 선택.** 복싱 / 킥복싱 / 무에타이 / 주짓수 (BJJ) / 레슬링 / 그래플링 기본기 + 격투기 컨디셔닝.
   체력 보강이 필요한 경우만 보조적으로 일반 컨디셔닝(버피, 점프 로프 등) 사용. 일반 헬스 동작은 지양.
7. **회원이 도장에서 혼자 연습 가능한 솔로 드릴만 추천.**
   - ✅ OK 동작 유형: 셰도우 복싱·킥복싱 / 솔로 BJJ 무브 (새우 빼기·브릿지·그랜비 롤·테크니컬 스탠드업) / 스프롤 / 풋워크 / 셰도우 콤비네이션 / 맨몸 컨디셔닝 (버피·점프 스쿼트·플랭크·푸시업·런지).
   - ✅ OK 도장 비치 기구 (이 5종만): **덤벨 / 케틀벨 / 바벨 / TRX / 로잉 머신**. + 미러·매트는 기본.
   - ❌ NO: 파트너 필요 (스파링·미트 홀딩·테이크다운·서브미션 응용) / **헤비백 / 점프로프 / 케이블 머신·스미스 머신·레그 프레스·메디신볼·배틀로프 등 도장에 없는 장비** / 코치 큐잉 필요한 고급 기술.
8. youtubeQuery 는 영문 검색어 — 실제 YouTube 에서 시연 영상이 검색되는 표현을 사용.
   - 좋은 예: "muay thai teep technique solo", "bjj shrimp drill solo", "boxing shadow boxing jab cross slip drill", "wrestling sprawl drill solo", "kettlebell swing tutorial", "trx row form".
   - 피할 표현: 너무 일반적인 "exercise", "training" 단어 / 한국어 / 한자 / 도장에 없는 장비명 (heavy bag, jump rope, cable, smith machine 등).
9. targetAxis 는 정확히 다음 중 하나: ${AXIS_FIELDS.join(", ")}
10. desc 는 한국어 1~2문장 — **무엇을 어떻게 하라**는 동작 지시만.
    **"관장님 피드백처럼", "코멘트처럼", "코멘트에서 언급된" 같은 출처 언급 금지.**
    코멘트 내용을 반영하되, 인용하지 말고 지시문으로 직접 녹일 것 (회원은 이 화면에서 코멘트를 볼 수 없음).
11. weeklyFeedback 은 **진짜 MMA 체육관 코치가 회원의 성장을 진심으로 응원하는 따뜻한 톤**의 한국어
    **3~4문장 (140~180자)**. 구성: (a) 이번 주 어떤 축을 왜 보강하는지 → (b) 그게 실전/성장에 왜 중요한지
    → (c) 구체적이고 따뜻한 격려·동기부여 한마디. 진심 어린 코치 말투 (예: "~해봐요", "분명 늘어요").
    단, **코멘트/피드백을 출처로 언급 금지** — 번호([1],[2]), "관장님 코멘트/피드백", "코멘트에서 언급된 …을 반영하여"
    같은 표현 금지. 드릴 이름 일일이 나열 금지. 회원은 이 화면에서 코멘트를 볼 수 없으니 결과·방향만 코치 톤으로 서술.

# 출력 형식 (반드시 JSON, 다른 텍스트 금지)
# days 배열은 "월", "수", "금" 3개 요소를 모두 포함해야 한다 (각 drills 2개).
{
  "focusSkills": ["${focusAxes[0]}", "${focusAxes[1] ?? focusAxes[0]}"],
  "weeklyFeedback": "회원 성장을 응원하는 따뜻한 코치 톤 3~4문장 (140~180자)",
  "days": [
    {
      "day": "월",
      "title": "예: 스트라이킹 정확도",
      "drills": [
        {
          "koName": "한국어 드릴 이름",
          "youtubeQuery": "english youtube search query (mma technique)",
          "desc": "한국어 설명 1~2문장",
          "sets": "3세트 × 12회",
          "durationMin": 10,
          "targetAxis": "striking"
        }
      ]
    },
    { "day": "수", "title": "...", "drills": [ /* 2개 */ ] },
    { "day": "금", "title": "...", "drills": [ /* 2개 */ ] }
  ]
}`;
}

