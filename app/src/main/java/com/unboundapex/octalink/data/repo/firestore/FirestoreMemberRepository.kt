package com.unboundapex.octalink.data.repo.firestore

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.unboundapex.octalink.data.Belt
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

    override fun observeAll(): Flow<List<MemberDoc>> = col.snapshotsAsList()

    override fun observeByStatus(status: MembershipStatus): Flow<List<MemberDoc>> =
        col.whereEqualTo("status", status.name).snapshotsAsList()

    override fun observeById(memberId: String): Flow<MemberDoc?> = callbackFlow {
        val sub = col.document(memberId).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.toMemberDoc())
        }
        awaitClose { sub.remove() }
    }

    override fun observeByAuthProviderId(authProviderId: String): Flow<MemberDoc?> =
        col.whereEqualTo("authProviderId", authProviderId).limit(1)
            .snapshotsAsList()
            .map { it.firstOrNull() }

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
                    "phone" to req.phone,
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

    override suspend fun updateProfile(
        memberId: String,
        name: String?,
        belt: Belt?,
        avatarId: String?,
    ) {
        val updates = buildMap<String, Any> {
            name?.let { put("name", it) }
            belt?.let { put("belt", it.name) }
            avatarId?.let { put("avatarId", it) }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        if (updates.size == 1) return  // updatedAt only — no field to update
        col.document(memberId).update(updates).await()
    }

    /** Query 의 snapshot 을 List<MemberDoc> Flow 로 변환. */
    private fun Query.snapshotsAsList(): Flow<List<MemberDoc>> = callbackFlow {
        val sub = addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.documents?.mapNotNull { it.toMemberDoc() }.orEmpty())
        }
        awaitClose { sub.remove() }
    }
}
