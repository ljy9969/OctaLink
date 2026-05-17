import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import {
  onDocumentCreated,
  onDocumentUpdated,
} from "firebase-functions/v2/firestore";
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

    await docRef.set({
      id: uid,
      name,
      belt: data.belt ?? "WHITE",
      weightClass: data.weightClass ?? "LIGHT",
      avatarId: data.avatarId ?? "ryu",
      role,
      status,
      joinDate,
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
  | "SKILL_UPDATED";

/** [NotificationType.defaultEnabled] 과 일치 — 클라이언트에서 prefs 키 누락 시 기본값. */
const DEFAULT_ENABLED: Record<NotificationTypeKey, boolean> = {
  COMMENT: true,
  TOURNAMENT_DRAWN: true,
  NEW_NOTICE: true,
  SIGNUP_RESULT: true,
  SKILL_UPDATED: true,
};

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
  const idTokenPairs: Array<{ uid: string; token: string }> = [];
  await Promise.all(
    Array.from(new Set(memberIds)).map(async (memberId) => {
      const snap = await db.collection("members").doc(memberId).get();
      const data = snap.data();
      if (!data) return;
      const token = data.fcmToken as string | undefined;
      if (!token) return;
      const prefs = (data.notificationPrefs as Record<string, boolean> | undefined) ?? {};
      const enabled = prefs[type] !== undefined ? prefs[type] : defaultEnabled;
      if (!enabled) return;
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
      "스킬 점수가 갱신되었습니다",
      "프로필에서 새 6축 차트를 확인해보세요.",
    );
  },
);
