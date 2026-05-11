package com.unboundapex.octalink.data.repo.inmemory

import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.RoleAllowlist
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.memberPool
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import java.time.Instant
import java.time.LocalDate

/**
 * In-memory Repository 초기 시드.
 *
 * 구성:
 * - [memberPool] 40명 — 이지연은 [RoleAllowlist] 매칭으로 CREATOR, 나머지는 MEMBER. 모두 APPROVED
 * - 김파시 (MASTER, [memberPool] 에는 없음 — 관장은 추첨 풀에 들어가지만 별도 시드)
 * - 2명 PENDING — 가입 승인 큐 데모용
 *
 * 실제 Firestore 환경에선 이 시드 제거. 가입 플로우에서만 회원 생성.
 */
internal object MockSeed {
    private val seedTime: Instant = Instant.parse("2026-01-01T00:00:00Z")

    val members: List<MemberDoc> = buildList {
        // 김파시 — memberPool 에 없어 별도 시드. MASTER 권한
        add(
            buildDoc(
                id = "master-pasi",
                name = "김파시",
                belt = Belt.BLACK,
                weightClass = WeightClass.MIDDLE,
                avatarId = "akuma",
                role = Role.MASTER,
                authProviderId = InMemoryAuthRepository.MOCK_MASTER_UID,
            )
        )

        // memberPool 의 40명 — 이지연만 CREATOR(allowlist 매칭), 나머지 MEMBER
        memberPool.forEach { m ->
            val role = RoleAllowlist.roleOf(m.name)
            val authId = if (m.name == "이지연") InMemoryAuthRepository.MOCK_CREATOR_UID
                         else "kakao:pool-${m.id}"
            add(
                buildDoc(
                    id = "pool-${m.id}",
                    name = m.name,
                    belt = m.belt,
                    weightClass = m.weightClass,
                    avatarId = m.avatarId,
                    role = role,
                    authProviderId = authId,
                )
            )
        }

        // 가입 승인 큐 데모 — 2명 PENDING
        add(
            buildDoc(
                id = "pending-jang-jihoon",
                name = "장지훈",
                belt = Belt.WHITE,
                weightClass = WeightClass.LIGHT,
                avatarId = "dan",
                status = MembershipStatus.PENDING,
                joinDate = LocalDate.of(2026, 5, 10),
            )
        )
        add(
            buildDoc(
                id = "pending-oh-hyunji",
                name = "오현지",
                belt = Belt.WHITE,
                weightClass = WeightClass.FEATHER,
                avatarId = "sakura",
                status = MembershipStatus.PENDING,
                joinDate = LocalDate.of(2026, 5, 11),
            )
        )
    }

    private fun buildDoc(
        id: String,
        name: String,
        belt: Belt,
        weightClass: WeightClass,
        avatarId: String,
        role: Role = Role.MEMBER,
        status: MembershipStatus = MembershipStatus.APPROVED,
        joinDate: LocalDate = LocalDate.of(2026, 1, 1),
        authProviderId: String = "kakao:$id",
    ): MemberDoc = MemberDoc(
        id = id,
        name = name,
        belt = belt,
        weightClass = weightClass,
        avatarId = avatarId,
        role = role,
        status = status,
        joinDate = joinDate,
        phone = null,
        authProviderId = authProviderId,
        createdAt = seedTime,
        updatedAt = seedTime,
    )
}
