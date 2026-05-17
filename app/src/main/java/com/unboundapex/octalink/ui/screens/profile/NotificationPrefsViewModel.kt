package com.unboundapex.octalink.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.NotificationType
import com.unboundapex.octalink.messaging.ClassReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ProfileScreen 알림 설정 카드 — [NotificationType] 별 ON/OFF 토글.
 *
 * 저장소: 본인 `members/{uid}.notificationPrefs` (Map<String, Boolean>).
 * 키 누락 시 [NotificationType.defaultEnabled] 사용.
 *
 * 토글이 한 개라도 ON 인 첫 순간엔 FCM 토큰을 강제 fetch + Firestore 영속화 — 가입은 마쳤지만
 * onNewToken 콜백을 놓친 케이스(앱이 이미 토큰 있었음) 대비.
 */
class NotificationPrefsViewModel(application: Application) : AndroidViewModel(application) {
    private val members = RepositoryProvider.members

    private val _memberId = MutableStateFlow<String?>(null)
    fun observeFor(memberId: String?) { _memberId.value = memberId }

    /** 현재 사용자의 notificationPrefs 맵 — 키 누락 시 default 채워서 전달. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val prefs: StateFlow<Map<NotificationType, Boolean>> =
        _memberId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyMap())
                else members.observeById(id).map { member ->
                    val raw = member?.notificationPrefs.orEmpty()
                    NotificationType.values().associateWith { type ->
                        raw[type.name] ?: type.defaultEnabled
                    }
                }
            }
            .catch { e ->
                android.util.Log.e("OctaLink.NotifPrefs", "prefs flow error", e)
                emit(NotificationType.values().associateWith { it.defaultEnabled })
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                NotificationType.values().associateWith { it.defaultEnabled },
            )

    /**
     * 토글 한 건 변경 — 전체 prefs 를 다시 직렬화해 single update 로 push.
     * 동시에 (a) 처음 ON 으로 전환되는 순간이라면 FCM 토큰을 fetch 해 영속화.
     */
    fun setEnabled(type: NotificationType, enabled: Boolean) {
        val memberId = _memberId.value ?: return
        val current = prefs.value.toMutableMap()
        current[type] = enabled
        val serialized = current.mapKeys { it.key.name }
        viewModelScope.launch {
            runCatching { members.updateNotificationPrefs(memberId, serialized) }
                .onFailure { android.util.Log.e("OctaLink.NotifPrefs", "save FAILED", it) }
            // 첫 ON 시 FCM 토큰 영속화 (onNewToken 놓친 케이스 대비).
            if (enabled) {
                runCatching {
                    val token = FirebaseMessaging.getInstance().token.await()
                    members.updateFcmToken(memberId, token)
                }.onFailure { android.util.Log.w("OctaLink.NotifPrefs", "fcm token fetch FAILED", it) }
            }
            // CLASS_REMINDER 는 클라이언트 WorkManager 스케줄 — 토글 즉시 등록/취소.
            if (type == NotificationType.CLASS_REMINDER) {
                val ctx = getApplication<Application>().applicationContext
                if (enabled) ClassReminderScheduler.scheduleAll(ctx)
                else ClassReminderScheduler.cancelAll(ctx)
            }
        }
    }
}
