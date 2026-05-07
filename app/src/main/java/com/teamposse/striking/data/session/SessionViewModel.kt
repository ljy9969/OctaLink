package com.teamposse.striking.data.session

import androidx.lifecycle.ViewModel
import com.teamposse.striking.data.Belt
import com.teamposse.striking.data.schema.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 현재 로그인한 회원 세션 상태.
 *
 * `CurrentUser` 싱글톤(Compose mutableStateOf)에서 단일 source-of-truth `StateFlow`로 이전.
 * 추후 Firebase Auth 연동 시 토큰 갱신/로그아웃 트리거가 이 VM에 모이게 된다.
 */
data class SessionState(
    val name: String = "이지연",
    val belt: Belt = Belt.WHITE,
    val avatarId: String = "rose",
    val role: Role = Role.MEMBER,
)

class SessionViewModel : ViewModel() {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun updateAvatar(avatarId: String) = _state.update { it.copy(avatarId = avatarId) }
    fun updateBelt(belt: Belt) = _state.update { it.copy(belt = belt) }
    fun updateName(name: String) = _state.update { it.copy(name = name) }
}
