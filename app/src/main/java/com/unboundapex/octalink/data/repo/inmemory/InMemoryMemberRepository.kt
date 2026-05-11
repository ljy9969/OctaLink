package com.unboundapex.octalink.data.repo.inmemory

import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.RoleAllowlist
import com.unboundapex.octalink.data.repo.MemberRepository
import com.unboundapex.octalink.data.repo.SignupRequest
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 회원 목록 in-memory 저장소. [MockSeed.members] 로 초기화. 모든 변경은 단일 StateFlow 갱신.
 *
 * 추후 FirestoreMemberRepository 로 교체되면 동일 인터페이스로 동작 — UI/VM 변경 불필요.
 */
class InMemoryMemberRepository(
    seed: List<MemberDoc> = MockSeed.members,
) : MemberRepository {
    private val _members = MutableStateFlow(seed)

    override fun observeAll(): Flow<List<MemberDoc>> = _members.asStateFlow()

    override fun observeByStatus(status: MembershipStatus): Flow<List<MemberDoc>> =
        _members.map { list -> list.filter { it.status == status } }

    override fun observeById(memberId: String): Flow<MemberDoc?> =
        _members.map { list -> list.firstOrNull { it.id == memberId } }

    override fun observeByAuthProviderId(authProviderId: String): Flow<MemberDoc?> =
        _members.map { list -> list.firstOrNull { it.authProviderId == authProviderId } }

    override suspend fun signup(req: SignupRequest): MemberDoc {
        val existing = _members.value.firstOrNull { it.authProviderId == req.authProviderId }
        if (existing != null) return existing

        val now = Instant.now()
        val role = RoleAllowlist.roleOf(req.name)
        val status = if (RoleAllowlist.skipApproval(req.name)) MembershipStatus.APPROVED
                     else MembershipStatus.PENDING
        val doc = MemberDoc(
            id = UUID.randomUUID().toString(),
            name = req.name,
            belt = req.belt,
            weightClass = req.weightClass,
            avatarId = req.avatarId,
            role = role,
            status = status,
            joinDate = LocalDate.now(),
            phone = req.phone,
            authProviderId = req.authProviderId,
            createdAt = now,
            updatedAt = now,
        )
        _members.value = _members.value + doc
        return doc
    }

    override suspend fun setStatus(memberId: String, status: MembershipStatus) {
        _members.value = _members.value.map {
            if (it.id == memberId) it.copy(status = status, updatedAt = Instant.now()) else it
        }
    }

    override suspend fun setRole(memberId: String, role: Role) {
        _members.value = _members.value.map {
            if (it.id == memberId) it.copy(role = role, updatedAt = Instant.now()) else it
        }
    }

    override suspend fun updateProfile(
        memberId: String,
        name: String?,
        belt: Belt?,
        avatarId: String?,
    ) {
        _members.value = _members.value.map { m ->
            if (m.id != memberId) return@map m
            m.copy(
                name = name ?: m.name,
                belt = belt ?: m.belt,
                avatarId = avatarId ?: m.avatarId,
                updatedAt = Instant.now(),
            )
        }
    }
}
