package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import kotlinx.coroutines.flow.Flow

/**
 * 회원 ([MemberDoc]) 영속화 추상화. Firestore `members/{uid}` 컬렉션과 1:1 매핑 예정.
 *
 * 현재 in-memory mock. 변경 작업은 mock 구현 단에선 권한 검증 생략 — 호출자(ViewModel)가
 * 사전 권한 확인 필요. 실제 Firestore 환경에선 Security Rules 가 동일 검증을 강제.
 */
interface MemberRepository {
    fun observeAll(): Flow<List<MemberDoc>>
    fun observeByStatus(status: MembershipStatus): Flow<List<MemberDoc>>
    fun observeById(memberId: String): Flow<MemberDoc?>
    fun observeByAuthProviderId(authProviderId: String): Flow<MemberDoc?>

    /**
     * 신규 회원 가입. [com.unboundapex.octalink.data.RoleAllowlist] 매칭 시 즉시 APPROVED
     * + 사전 정의된 Role 부여. 그 외엔 MEMBER + PENDING (관장 승인 대기).
     *
     * 동일 [SignupRequest.authProviderId] 로 재호출 시 기존 MemberDoc 반환 (idempotent).
     */
    suspend fun signup(req: SignupRequest): MemberDoc

    /** PENDING → APPROVED 또는 REJECTED 등. 관장/창조자 권한 필요. */
    suspend fun setStatus(memberId: String, status: MembershipStatus)

    /** 회원 역할 변경 (창조자 단독 권한). */
    suspend fun setRole(memberId: String, role: Role)

    /** 본인 프로필 변경. 권한 검증은 호출자 책임 (또는 Firestore Rules). */
    suspend fun updateProfile(
        memberId: String,
        name: String? = null,
        belt: Belt? = null,
        avatarId: String? = null,
    )

    /**
     * 본인 회원 탈퇴 — [com.unboundapex.octalink.data.schema.MembershipStatus.LEFT] 로 전환.
     * 도장 명단(MemberDoc) 자체 삭제는 CREATOR/MASTER 권한 (별도 요청).
     * Cloud Function `leaveMembership` 호출 (server-side audit trail).
     */
    suspend fun leaveMembership(memberId: String)
}

/** 가입 폼 입력 결과 — 폼에서 사용자가 입력한 값 + 카카오 동의 항목에서 가져온 값 합산. */
data class SignupRequest(
    val authProviderId: String,
    val name: String,
    val belt: Belt,
    val weightClass: WeightClass,
    val avatarId: String,
    val phone: String? = null,
    /** 카카오 동의 항목에서 가져온 부가 정보 — null 가능, 권한/미동의 시 비어옴 */
    val email: String? = null,
    val gender: String? = null,
    val ageRange: String? = null,
    val birthday: String? = null,
    val birthyear: String? = null,
)
