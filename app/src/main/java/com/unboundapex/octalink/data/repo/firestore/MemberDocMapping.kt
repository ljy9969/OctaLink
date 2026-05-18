package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import java.time.Instant
import java.time.LocalDate

/**
 * [MemberDoc] ↔ Firestore Map 직렬화. Firestore Rules 가 검증하는 필드 형식과 정확히 일치.
 *
 * - enum 은 `.name` 문자열로 (Role/Belt/WeightClass/MembershipStatus)
 * - [LocalDate] 는 ISO-8601 문자열 ("yyyy-MM-dd")
 * - [Instant] 는 Firestore [Timestamp]
 *
 * Cloud Function 의 completeSignup 도 동일 키 사용 — 서버/클라이언트 매핑 동기.
 */

internal fun MemberDoc.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "belt" to belt.name,
    "weightClass" to weightClass.name,
    "avatarId" to avatarId,
    "role" to role.name,
    "status" to status.name,
    "joinDate" to joinDate.toString(),
    "phone" to phone,
    "authProviderId" to authProviderId,
    "email" to email,
    "gender" to gender,
    "ageRange" to ageRange,
    "birthday" to birthday,
    "birthyear" to birthyear,
    "skills" to skills?.let {
        mapOf(
            "striking" to it.striking,
            "grappling" to it.grappling,
            "stamina" to it.stamina,
            "technique" to it.technique,
            "mental" to it.mental,
            "speed" to it.speed,
        )
    },
    "fcmToken" to fcmToken,
    "notificationPrefs" to notificationPrefs,
    "classReminderSlots" to classReminderSlots,
    "createdAt" to Timestamp(createdAt.epochSecond, createdAt.nano),
    "updatedAt" to Timestamp(updatedAt.epochSecond, updatedAt.nano),
)

internal fun DocumentSnapshot.toMemberDoc(): MemberDoc? {
    val name = getString("name") ?: return null
    val beltName = getString("belt") ?: return null
    val wcName = getString("weightClass") ?: return null
    return MemberDoc(
        id = id,
        name = name,
        belt = runCatching { Belt.valueOf(beltName) }.getOrDefault(Belt.WHITE),
        weightClass = runCatching { WeightClass.valueOf(wcName) }.getOrDefault(WeightClass.LIGHT),
        avatarId = getString("avatarId") ?: "m_light",
        role = runCatching { Role.valueOf(getString("role") ?: "MEMBER") }.getOrDefault(Role.MEMBER),
        status = runCatching {
            MembershipStatus.valueOf(getString("status") ?: "PENDING")
        }.getOrDefault(MembershipStatus.PENDING),
        joinDate = runCatching {
            LocalDate.parse(getString("joinDate") ?: "")
        }.getOrDefault(LocalDate.now()),
        phone = getString("phone"),
        authProviderId = getString("authProviderId"),
        email = getString("email"),
        gender = getString("gender"),
        ageRange = getString("ageRange"),
        birthday = getString("birthday"),
        birthyear = getString("birthyear"),
        skills = (get("skills") as? Map<*, *>)?.let { map ->
            SkillSet(
                striking  = (map["striking"]  as? Number)?.toFloat() ?: 0f,
                grappling = (map["grappling"] as? Number)?.toFloat() ?: 0f,
                stamina   = (map["stamina"]   as? Number)?.toFloat() ?: 0f,
                technique = (map["technique"] as? Number)?.toFloat() ?: 0f,
                mental    = (map["mental"]    as? Number)?.toFloat() ?: 0f,
                speed     = (map["speed"]     as? Number)?.toFloat() ?: 0f,
            )
        },
        fcmToken = getString("fcmToken"),
        notificationPrefs = (get("notificationPrefs") as? Map<*, *>)
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? Boolean ?: return@mapNotNull null
                key to value
            }?.toMap()
            .orEmpty(),
        classReminderSlots = (get("classReminderSlots") as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty(),
        createdAt = (get("createdAt") as? Timestamp)?.toInstantSafe() ?: Instant.EPOCH,
        updatedAt = (get("updatedAt") as? Timestamp)?.toInstantSafe() ?: Instant.EPOCH,
    )
}

private fun Timestamp.toInstantSafe(): Instant =
    Instant.ofEpochSecond(seconds, nanoseconds.toLong())
