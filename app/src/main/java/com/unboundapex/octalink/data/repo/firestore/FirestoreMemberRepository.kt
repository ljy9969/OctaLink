package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.SessionGym
import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.repo.MemberRepository
import com.unboundapex.octalink.data.repo.SignupRequest
import com.unboundapex.octalink.data.schema.Collections
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firestore 기반 [MemberRepository] 구현.
 *
 * 컬렉션: `members/{uid}` — 1 문서 = 1 회원. uid 는 Firebase Auth uid (= "kakao:{kakaoUserId}").
 * Security Rules ([com.unboundapex.octalink.data.repo.kakao.KakaoAuthRepository] 의 uid 규약 + firestore.rules):
 * - create: Cloud Function 만 (client 직접 create 차단) → [signup] 은 `completeSignup` Cloud Function 호출
 * - update: 본인 안전필드 / 관장 role 제외 / 창조자 전체
 * - delete: 창조자 단독
 *
 * 모든 변경은 Rules 가 거부하면 클라이언트에서 PermissionDeniedException 발생. ViewModel 에서
 * 호출자 권한 사전 확인 후 호출하면 더블 검증.
 */
class FirestoreMemberRepository : MemberRepository {
    private val db = Firebase.firestore
    private val col = db.collection(Collections.MEMBERS)
    private val functions = Firebase.functions("asia-northeast3")

    override fun observeAll(): Flow<List<MemberDoc>> = col.scopedToGym().snapshotsAsList()

    override fun observeByStatus(status: MembershipStatus): Flow<List<MemberDoc>> =
        col.scopedToGym().whereEqualTo("status", status.name).snapshotsAsList()

    /**
     * 현재 세션 사용자의 소속 체육관([SessionGym.gymId])으로 목록 쿼리를 제한(멀티테넌트).
     * gymId 가 비어 있으면 `gymId == ""` 로 fail-closed — 타 체육관 회원이 새지 않음.
     * (단일 회원 조회 [observeById] 는 본인 부트스트랩용이라 스코프하지 않음.)
     */
    private fun com.google.firebase.firestore.CollectionReference.scopedToGym(): Query =
        whereEqualTo("gymId", SessionGym.gymId ?: "")

    override fun observeById(memberId: String): Flow<MemberDoc?> = callbackFlow {
        val sub = col.document(memberId).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.toMemberDoc())
        }
        awaitClose { sub.remove() }
    }

    /**
     * Cloud Function `completeSignup` 이 `members/{uid}` 형식으로 doc 생성하므로
     * authProviderId == doc id 가 항상 성립. collection list 쿼리 대신 직접 doc get.
     *
     * 이렇게 하면 firestore.rules 의 `allow get: if isSelf(uid)` 만으로 부트스트랩 통과 가능
     * (list 쿼리는 isApproved 필요라 신규 가입자는 막힘).
     */
    override fun observeByAuthProviderId(authProviderId: String): Flow<MemberDoc?> =
        observeById(authProviderId)

    /**
     * 가입 폼 제출 → `completeSignup` Cloud Function 호출 (server-side 가 RoleAllowlist 매칭 +
     * `members/{uid}` 문서 생성). 클라이언트 직접 create 는 Security Rules 차단.
     *
     * 결과 [MemberDoc] 은 함수 응답 직후 Firestore 가 emit 하는 첫 snapshot 으로 [observeById] /
     * [observeByAuthProviderId] 에서 확인 가능. 이 함수는 호출 성공 여부만 반환.
     */
    override suspend fun signup(req: SignupRequest): MemberDoc {
        functions.getHttpsCallable("completeSignup")
            .call(
                mapOf(
                    "name" to req.name,
                    "belt" to req.belt.name,
                    "weightClass" to req.weightClass.name,
                    "avatarId" to req.avatarId,
                    "joinDate" to req.joinDate.toString(),
                    "phone" to req.phone,
                    "email" to req.email,
                    "gender" to req.gender,
                    "ageRange" to req.ageRange,
                    "birthday" to req.birthday,
                    "birthyear" to req.birthyear,
                )
            )
            .await()

        // 즉시 1회 조회 — observe 흐름이 따라잡기 전 caller 가 결과 필요한 경우 대비
        val snap = col.document(req.authProviderId).get().await()
        return snap.toMemberDoc()
            ?: error("completeSignup 응답 후 members/${req.authProviderId} 조회 실패")
    }

    override suspend fun setStatus(memberId: String, status: MembershipStatus) {
        col.document(memberId).update(
            mapOf(
                "status" to status.name,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    override suspend fun setRole(memberId: String, role: Role) {
        col.document(memberId).update(
            mapOf(
                "role" to role.name,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    /**
     * 본인 탈퇴 — Cloud Function `leaveMembership` 이 server-side 로 status=LEFT 갱신.
     * 클라이언트가 직접 update 하지 않는 이유: audit trail + 미래 cascade delete 확장 여지.
     */
    override suspend fun leaveMembership(memberId: String) {
        functions.getHttpsCallable("leaveMembership")
            .call(emptyMap<String, Any>())
            .await()
    }

    /**
     * LEFT → APPROVED/PENDING 재가입. Cloud Function 이 RoleAllowlist 재평가 후 status 결정.
     * 이미 active 상태면 server 가 alreadyActive=true 로 응답 (no-op).
     */
    override suspend fun rejoinMembership(memberId: String) {
        functions.getHttpsCallable("rejoinMembership")
            .call(emptyMap<String, Any>())
            .await()
    }

    override suspend fun updateProfile(
        memberId: String,
        name: String?,
        belt: Belt?,
        weightClass: WeightClass?,
        avatarId: String?,
        skills: SkillSet?,
    ) {
        val updates = buildMap<String, Any> {
            name?.let { put("name", it) }
            belt?.let { put("belt", it.name) }
            weightClass?.let { put("weightClass", it.name) }
            avatarId?.let { put("avatarId", it) }
            skills?.let {
                // skillScores doc 과 동일 정밀도 (소수점 2자리 floor) 로 저장.
                put("skills", mapOf(
                    "striking" to it.striking.toFirestoreScore(),
                    "grappling" to it.grappling.toFirestoreScore(),
                    "stamina" to it.stamina.toFirestoreScore(),
                    "technique" to it.technique.toFirestoreScore(),
                    "mental" to it.mental.toFirestoreScore(),
                    "speed" to it.speed.toFirestoreScore(),
                ))
            }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        if (updates.size == 1) return  // updatedAt only — no field to update
        col.document(memberId).update(updates).await()
    }

    override suspend fun clearSkills(memberId: String) {
        // member.skills 필드 자체 제거 → MemberDocMapping 에서 null 로 읽힘 → SkillSet.isUnset() = true.
        // 차트는 defaultSkillStats() 평탄화 + AI 루틴은 자가입력 UI 노출 흐름으로 복귀.
        val updates = mapOf(
            "skills" to FieldValue.delete(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        col.document(memberId).update(updates).await()
    }

    override suspend fun updateFcmToken(memberId: String, token: String?) {
        val updates = mapOf(
            "fcmToken" to token, // null 도 그대로 — 알림 OFF 전체 해제 의도 반영.
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        col.document(memberId).update(updates).await()
    }

    override suspend fun updateNotificationPrefs(memberId: String, prefs: Map<String, Boolean>) {
        val updates = mapOf(
            "notificationPrefs" to prefs,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        col.document(memberId).update(updates).await()
    }

    override suspend fun updateClassReminderSlots(memberId: String, slots: List<String>) {
        val updates = mapOf(
            "classReminderSlots" to slots,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        col.document(memberId).update(updates).await()
    }

    /** [FirestoreSkillScoreRepository] 와 동일 정밀도 — 슬라이더 raw float 을 0.01 단위로 양자화 (round). */
    private fun Float.toFirestoreScore(): Double =
        kotlin.math.round(this.toDouble() * 100) / 100

    /** Query 의 snapshot 을 List<MemberDoc> Flow 로 변환. */
    private fun Query.snapshotsAsList(): Flow<List<MemberDoc>> = callbackFlow {
        val sub = addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.documents?.mapNotNull { it.toMemberDoc() }.orEmpty())
        }
        awaitClose { sub.remove() }
    }
}
