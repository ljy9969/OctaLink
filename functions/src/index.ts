import { onCall, HttpsError } from "firebase-functions/v2/https";
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
  { region: "asia-northeast3", cors: false },
  async (request) => {
    const accessToken = request.data?.accessToken as string | undefined;
    if (!accessToken) {
      throw new HttpsError("invalid-argument", "accessToken required");
    }

    // 1) Kakao API 로 토큰 검증 + 사용자 정보 조회
    const res = await fetch("https://kapi.kakao.com/v2/user/me", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!res.ok) {
      throw new HttpsError(
        "unauthenticated",
        `Kakao token verification failed: ${res.status}`,
      );
    }
    const kakaoUser = (await res.json()) as KakaoUser;
    const uid = `kakao:${kakaoUser.id}`;
    const nickname = kakaoUser.kakao_account?.profile?.nickname;

    // 2) Firebase Auth 사용자 ensure — 없으면 생성, 있으면 displayName 만 동기화
    try {
      await admin.auth().getUser(uid);
      if (nickname) {
        await admin.auth().updateUser(uid, { displayName: nickname });
      }
    } catch {
      await admin.auth().createUser({
        uid,
        displayName: nickname,
      });
    }

    // 3) Custom Token 발급 (provider claim 부착해서 디버깅 식별 용이)
    const customToken = await admin.auth().createCustomToken(uid, {
      provider: "kakao",
      kakaoId: kakaoUser.id,
    });

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
      authProviderId: uid,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { ok: true, role, status };
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
