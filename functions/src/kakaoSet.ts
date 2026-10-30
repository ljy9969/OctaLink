/**
 * 카카오 계정 상태 변경 웹훅 — 보안 이벤트 토큰(SET, JWT) 검증.
 * 규격(카카오 문서 '계정 상태 변경 웹훅'):
 *   - 헤더 alg=RS256, kid = 카카오 공개키 id(https://kauth.kakao.com/.well-known/jwks.json, 캐시 권장)
 *   - 페이로드 iss=https://kauth.kakao.com, aud=우리 앱의 대표 REST API 키, sub=회원번호, app_id=앱 ID
 *   - 성공 → 202(본문 없음), 실패 → 400 + {err, description}(invalid_request·invalid_key·invalid_issuer·invalid_audience)
 * 서명을 확인하지 않으면 위조 요청으로 회원을 탈퇴 처리·개인정보 삭제할 수 있으므로 처리 전에 반드시 통과시킨다.
 */
import { createPublicKey, verify, JsonWebKey, KeyObject } from "crypto";

export const KAKAO_ISSUER = "https://kauth.kakao.com";
export const KAKAO_JWKS_URL = "https://kauth.kakao.com/.well-known/jwks.json";

export type SetErrorCode = "invalid_request" | "invalid_key" | "invalid_issuer" | "invalid_audience";
export interface SetError { err: SetErrorCode; description: string }
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type SetClaims = Record<string, any>;
export type SetVerifyResult = { ok: true; claims: SetClaims } | { ok: false; error: SetError };

const fail = (err: SetErrorCode, description: string): SetVerifyResult => ({ ok: false, error: { err, description } });

function decodeJson(part: string): SetClaims | null {
  try {
    const v = JSON.parse(Buffer.from(part, "base64url").toString("utf8"));
    return v && typeof v === "object" && !Array.isArray(v) ? v : null;
  } catch {
    return null;
  }
}

/** SET 검증 — 형식 → iss → aud → 서명 순(카카오 안내 순서). 통과해야만 claims 를 쓴다. */
export async function verifySet(
  token: string,
  opts: { restApiKey: string; getKey: (kid: string) => Promise<KeyObject | null> },
): Promise<SetVerifyResult> {
  const parts = (token || "").trim().split(".");
  if (parts.length !== 3 || parts.some((p) => !/^[A-Za-z0-9_-]+$/.test(p))) {
    return fail("invalid_request", "SET is not a compact JWS");
  }
  const header = decodeJson(parts[0]);
  const claims = decodeJson(parts[1]);
  if (!header || !claims) return fail("invalid_request", "SET header/payload is not valid JSON");
  if (header.alg !== "RS256" || typeof header.kid !== "string") {
    return fail("invalid_request", `Unsupported alg/kid: ${String(header.alg)}`);
  }
  if (claims.iss !== KAKAO_ISSUER) return fail("invalid_issuer", `Unexpected issuer: ${String(claims.iss)}`);
  const aud = claims.aud;
  const audOk = !!opts.restApiKey && (Array.isArray(aud) ? aud.includes(opts.restApiKey) : aud === opts.restApiKey);
  if (!audOk) return fail("invalid_audience", "Audience does not match this app");
  const key = await opts.getKey(header.kid);
  if (!key) return fail("invalid_key", `Key ID ${header.kid} not found`);
  const signed = verify("RSA-SHA256", Buffer.from(`${parts[0]}.${parts[1]}`), key, Buffer.from(parts[2], "base64url"));
  return signed ? { ok: true, claims } : fail("invalid_key", `Signature mismatch for key ID ${header.kid}`);
}

export type JwksFetch = (url: string) => Promise<{ ok: boolean; status: number; json: () => Promise<unknown> }>;

/**
 * 카카오 공개키(JWKS) 캐시 — 같은 kid 는 TTL 동안 재사용, 모르는 kid(키 교체)는 최소 간격 뒤 한 번 재조회.
 * 카카오 안내: 공개키는 캐싱해서 쓰고, 너무 자주 요청하면 차단될 수 있음.
 * 조회 실패는 예외로 올려 웹훅이 5xx 를 내게 한다(카카오가 재전송 — 일시 장애에서 이벤트 유실 방지).
 */
export class JwksCache {
  private keys = new Map<string, KeyObject>();
  private fetchedAt = Number.NEGATIVE_INFINITY;
  private readonly url: string;
  private readonly ttlMs: number;
  private readonly minRefetchMs: number;
  private readonly now: () => number;

  constructor(
    private readonly fetchFn: JwksFetch,
    opts: { url?: string; ttlMs?: number; minRefetchMs?: number; now?: () => number } = {},
  ) {
    this.url = opts.url ?? KAKAO_JWKS_URL;
    this.ttlMs = opts.ttlMs ?? 6 * 3600 * 1000;
    this.minRefetchMs = opts.minRefetchMs ?? 60 * 1000;
    this.now = opts.now ?? Date.now;
  }

  async getKey(kid: string): Promise<KeyObject | null> {
    const age = this.now() - this.fetchedAt;
    if (age < this.ttlMs && this.keys.has(kid)) return this.keys.get(kid) ?? null;
    if (age >= this.minRefetchMs) await this.refresh();
    return this.keys.get(kid) ?? null;
  }

  private async refresh(): Promise<void> {
    const res = await this.fetchFn(this.url);
    if (!res.ok) throw new Error(`Kakao JWKS ${res.status}`);
    const body = (await res.json()) as { keys?: Array<JsonWebKey & { kid?: string }> };
    const next = new Map<string, KeyObject>();
    for (const k of body.keys ?? []) {
      if (k.kty !== "RSA" || !k.kid) continue;
      next.set(k.kid, createPublicKey({ key: k, format: "jwk" }));
    }
    this.keys = next;
    this.fetchedAt = this.now();
  }
}

/** 이벤트 대상 회원번호 — 이벤트의 subject.sub 우선, 없으면 SET 최상위 sub. */
export function kakaoUserIdOf(claims: SetClaims, eventData: Record<string, unknown>): string | null {
  const subject = eventData.subject as Record<string, unknown> | undefined;
  const id = subject?.sub ?? claims.sub;
  return id == null || id === "" ? null : String(id);
}

/** 동의 철회 항목 — 규격은 공백 구분 문자열 `scope`(예: "account_email birthday"). 배열도 허용. */
export function scopesOf(eventData: Record<string, unknown>): string[] {
  if (typeof eventData.scope === "string") return eventData.scope.split(/\s+/).filter(Boolean);
  if (Array.isArray(eventData.scopes)) return eventData.scopes.map(String);
  return [];
}
