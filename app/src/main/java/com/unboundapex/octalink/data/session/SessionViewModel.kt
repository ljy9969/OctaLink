package com.unboundapex.octalink.data.session

import androidx.lifecycle.ViewModel
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.RoleAllowlist
import com.unboundapex.octalink.data.schema.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 현재 로그인한 회원 세션 상태.
 *
 * `CurrentUser` 싱글톤(Compose mutableStateOf)에서 단일 source-of-truth `StateFlow`로 이전.
 * 추후 Firebase Auth 연동 시 토큰 갱신/로그아웃 트리거가 이 VM에 모이게 된다.
 *
 * **역할(role) 결정 정책:** 사용자가 직접 변경 불가. 카카오 OAuth 회원 가입 시
 * 카카오 표시 이름이 [RoleAllowlist] 의 masters/coaches 목록에 있으면 자동으로
 * MASTER/COACH 부여 + 가입 승인 단계 skip. 그 외엔 MEMBER + PENDING 상태로 등록되어
 * 관장 승인 대기. MVP는 mock 세션이라 이름으로부터 자동 결정.
 */
data class SessionState(
    val name: String = "이지연",
    val belt: Belt = Belt.WHITE,
    val avatarId: String = "rose",
    val role: Role = RoleAllowlist.roleOf(name),
)

class SessionViewModel : ViewModel() {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun updateAvatar(avatarId: String) = _state.update { it.copy(avatarId = avatarId) }
    fun updateBelt(belt: Belt) = _state.update { it.copy(belt = belt) }

    /**
     * 이름 변경 시 [RoleAllowlist] 조회로 역할도 동기 갱신.
     * Firebase Auth 도입 후엔 가입/로그인 플로우에서만 호출됨.
     */
    fun updateName(name: String) = _state.update {
        it.copy(name = name, role = RoleAllowlist.roleOf(name))
    }

    /**
     * 역할 직접 변경은 ❌ — 사용자가 임의로 권한을 올릴 수 없도록 막음.
     * 운영자는 [RoleAllowlist] 코드 수정 + 새 빌드 배포로 권한 부여.
     */
}
