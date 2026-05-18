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
import kotlinx.coroutines.flow.first
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

    /**
     * 회원 id 가 변경되면 (a) flow 구독 전환 + (b) **1회성 legacy 슬롯 정리** 트리거.
     *
     * 마이그레이션 대상 — 이전 ClassReminderConfigDialog 가 평일 오픈매트 / 토요일 PT 슬롯까지
     * 노출했을 때 사용자가 저장한 키들. 새 그리드 다이얼로그는 평일 그룹 수업 4시간대만 노출이라
     * 이 키들은 UI 에서 해제 불가 + WorkManager 가 무효 시간에 reminder fire 가능 → 이중 위험.
     *
     * 정책: stored set ∩ [validClassReminderKeys] 가 stored set 과 다르면 cleaned 셋으로 일괄 write.
     * Idempotent — 다시 trigger 돼도 cleaned == stored 이므로 no-op. 이후 schedule 재실행.
     */
    fun observeFor(memberId: String?) {
        _memberId.value = memberId
        if (memberId != null) viewModelScope.launch { migrateClassReminderSlotsIfNeeded(memberId) }
    }

    private suspend fun migrateClassReminderSlotsIfNeeded(memberId: String) {
        runCatching {
            // 한 번만 read — observeById 의 첫 emit 사용. observeById 는 콜백 listener 라 즉시 첫값 emit.
            val member = members.observeById(memberId).first()
            val raw = member?.classReminderSlots.orEmpty().toSet()
            val valid = com.unboundapex.octalink.data.validClassReminderKeys
            val cleaned = raw intersect valid
            if (cleaned == raw) return@runCatching
            val dropped = raw - cleaned
            android.util.Log.i(
                "OctaLink.NotifPrefs",
                "migrate classReminderSlots: dropped=$dropped (${raw.size} -> ${cleaned.size})",
            )
            members.updateClassReminderSlots(memberId, cleaned.toList())
            // 영속 정리 후 WorkManager 재스케줄 — 무효 키로 잡혀 있던 worker 가 있다면 cancelAll + 다시 scheduleAll.
            val ctx = getApplication<Application>().applicationContext
            if (cleaned.isEmpty()) {
                ClassReminderScheduler.cancelAll(ctx)
            } else {
                ClassReminderScheduler.scheduleAll(ctx)
            }
        }.onFailure { android.util.Log.w("OctaLink.NotifPrefs", "migrate failed", it) }
    }

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
     * 현재 사용자가 선택한 CLASS_REMINDER 슬롯 키 셋 (예: `"MONDAY_19:30"`).
     * 마이그레이션 영속 처리와 별개로 UI 단에서도 항상 [validClassReminderKeys] 와 교집합만 노출 —
     * write-back 이 끝나기 전 잠시 raw 값이 노출되는 race 차단.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val classReminderSlots: StateFlow<Set<String>> =
        _memberId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else members.observeById(id).map { it?.classReminderSlots.orEmpty() }
            }
            .map { it.toSet() intersect com.unboundapex.octalink.data.validClassReminderKeys }
            .catch { e ->
                android.util.Log.e("OctaLink.NotifPrefs", "classReminderSlots flow error", e)
                emit(emptySet())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

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
        }
    }

    /**
     * 회원이 30분 전 리마인더 받을 슬롯 셋을 일괄 갱신. 비어있으면 모든 예약 취소.
     * 비어있지 않으면 FCM 토큰도 함께 fetch + 즉시 [ClassReminderScheduler.scheduleAll] 호출.
     */
    fun updateClassReminderSlots(slots: Set<String>) {
        val memberId = _memberId.value ?: return
        val serialized = slots.toList()
        viewModelScope.launch {
            runCatching { members.updateClassReminderSlots(memberId, serialized) }
                .onFailure { android.util.Log.e("OctaLink.NotifPrefs", "classReminderSlots save FAILED", it) }
            val ctx = getApplication<Application>().applicationContext
            if (slots.isEmpty()) {
                ClassReminderScheduler.cancelAll(ctx)
            } else {
                runCatching {
                    val token = FirebaseMessaging.getInstance().token.await()
                    members.updateFcmToken(memberId, token)
                }.onFailure { android.util.Log.w("OctaLink.NotifPrefs", "fcm token fetch FAILED", it) }
                ClassReminderScheduler.scheduleAll(ctx)
            }
        }
    }
}
