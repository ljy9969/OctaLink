package com.unboundapex.octalink.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.unboundapex.octalink.data.schema.NotificationType

/**
 * 앱 시작 시 [NotificationType] 의 모든 채널을 OS 에 1회 등록.
 *
 * Android 8.0+ 부터 NotificationChannel 없이 알림 표시 불가. 사용자는 OS 설정에서 채널별로
 * 끌 수 있고, 채널마다 다른 톤/중요도 설정 가능. 채널 ID 는 [NotificationType.channelId] 로
 * 고정 — 사용자가 OS 에서 끈 채널 설정은 OS 가 영구 보관.
 *
 * 알림 차단 정책 2단:
 *  1. 앱 내 [NotificationType] 토글 (Profile 화면) — Firestore notificationPrefs.
 *  2. OS 채널 토글 — NotificationManagerCompat.areNotificationsEnabled / channel.importance == NONE.
 *
 * 호출: [com.unboundapex.octalink.OctaLinkApplication.onCreate] 에서 1회.
 */
object NotificationChannels {
    fun ensureRegistered(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        NotificationType.values().forEach { type ->
            val channel = NotificationChannel(
                type.channelId,
                type.channelName,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = type.channelDescription
            }
            manager.createNotificationChannel(channel)
        }
    }
}
