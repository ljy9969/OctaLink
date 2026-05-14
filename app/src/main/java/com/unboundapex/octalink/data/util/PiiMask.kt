package com.unboundapex.octalink.data.util

import com.unboundapex.octalink.BuildConfig

/**
 * PII (개인정보) 로그 마스킹 유틸.
 *
 * 정책:
 * - **release 빌드** — 모든 PII 는 `<redacted>` 로 치환되어 절대 logcat 에 노출되지 않음
 * - **debug 빌드** — 디버깅 가능성을 위해 마스킹된 형태로 노출
 *   - 이름: `이*연` (첫/마지막 글자만)
 *   - 전화번호: `010-***-9339` (앞 3 / 뒤 4 자리만)
 *   - 이메일: `j***@example.com` (로컬 첫 글자만)
 *   - 식별자(uid/token): 앞 3 / 뒤 3 + 길이
 *
 * null / blank 입력은 `<null>` / `<blank>` 로 명시되어 디버깅 시 입력 누락 진단 가능.
 *
 * 원본 PII 를 logcat 에 직접 찍을 일이 있을 때 항상 이 유틸을 거치는 것을 규칙으로 함.
 */
object PiiMask {

    private val isDebug: Boolean get() = BuildConfig.DEBUG

    fun name(raw: String?): String {
        if (!isDebug) return if (raw == null) "<null>" else "<redacted>"
        if (raw == null) return "<null>"
        if (raw.isBlank()) return "<blank>"
        return when (raw.length) {
            1 -> "*"
            2 -> "${raw.first()}*"
            else -> "${raw.first()}${"*".repeat(raw.length - 2)}${raw.last()}"
        }
    }

    fun phone(raw: String?): String {
        if (!isDebug) return if (raw == null) "<null>" else "<redacted>"
        if (raw == null) return "<null>"
        if (raw.isBlank()) return "<blank>"
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 7) return "*".repeat(digits.length.coerceAtLeast(1))
        val head = digits.take(3)
        val tail = digits.takeLast(4)
        val mid = "*".repeat(digits.length - 7)
        return "$head-$mid-$tail"
    }

    fun email(raw: String?): String {
        if (!isDebug) return if (raw == null) "<null>" else "<redacted>"
        if (raw == null) return "<null>"
        if (raw.isBlank()) return "<blank>"
        val at = raw.indexOf('@')
        if (at <= 0) return "*".repeat(raw.length.coerceAtMost(8))
        val local = raw.substring(0, at)
        val domain = raw.substring(at)
        return "${local.first()}${"*".repeat((local.length - 1).coerceAtLeast(0))}$domain"
    }

    /** Firebase uid / FCM 토큰 / accessToken 등 식별자 — 길이만 노출 (전체 미노출). */
    fun id(raw: String?): String {
        if (!isDebug) return if (raw == null) "<null>" else "<redacted>"
        if (raw == null) return "<null>"
        if (raw.length <= 6) return raw
        return "${raw.take(3)}...${raw.takeLast(3)} (len=${raw.length})"
    }
}
