package com.unboundapex.octalink.messaging

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.unboundapex.octalink.MainActivity
import com.unboundapex.octalink.R
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.NotificationType
import com.unboundapex.octalink.data.util.PiiMask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * FCM 메시지 수신 + 토큰 갱신 핸들러.
 *
 * 토큰 흐름:
 *   1. [onNewToken] 호출 시 Firebase Auth uid 가 있으면 `members/{uid}.fcmToken` 으로 영속화
 *      → server 측 (Cloud Function) 이 사용자에게 push 발송 시 이 토큰을 대상으로 함.
 *   2. uid 가 아직 없으면(가입 전) 토큰 보관 안 함. 가입 완료 후 첫 푸시 알림 토글 시 강제 갱신
 *      ([com.google.firebase.messaging.FirebaseMessaging.getInstance().token]) 로 재발급 가능.
 *
 * 메시지 수신:
 *   FCM data payload 의 `type` 필드가 [NotificationType.name] 과 일치하면 그 채널로 라우팅.
 *   사용자가 ProfileScreen 에서 OFF 한 타입은 무시 (Firestore notificationPrefs).
 *
 *   payload 예시:
 *   { "type": "COMMENT", "title": "관장님이 한 줄 코멘트를 남기셨어요", "body": "리드 잽 후..." }
 */
class OctaLinkMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM_TOKEN registered ${PiiMask.id(token)}")
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "skip token persist — no Firebase Auth uid yet (pre-signup)")
            return
        }
        scope.launch {
            runCatching { RepositoryProvider.members.updateFcmToken(uid, token) }
                .onFailure { Log.w(TAG, "updateFcmToken FAILED", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val typeName = message.data["type"]
        val type = typeName?.let { runCatching { NotificationType.valueOf(it) }.getOrNull() }
        Log.i(
            TAG,
            "FCM_MESSAGE from=${PiiMask.id(message.from)}, type=$typeName, hasNotif=${message.notification != null}",
        )
        if (type == null) {
            Log.w(TAG, "FCM_MESSAGE missing/unknown 'type' data field — drop")
            return
        }

        // 사용자 prefs 확인 — 비동기 read 가 필요하지만, 알림 표시는 main 스레드 안전.
        // 단순화: prefs 확인은 코루틴 안에서, OFF 면 표시 skip. 알림 표시도 같은 코루틴에서.
        scope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val enabled = if (uid == null) type.defaultEnabled
            else loadPrefEnabled(uid, type)
            if (!enabled) {
                Log.i(TAG, "drop notification type=${type.name} — user pref OFF")
                return@launch
            }
            showNotification(message, type)
        }
    }

    private suspend fun loadPrefEnabled(uid: String, type: NotificationType): Boolean {
        val member = runCatching { RepositoryProvider.members.observeById(uid).first() }.getOrNull()
        return member?.notificationPrefs?.get(type.name) ?: type.defaultEnabled
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(message: RemoteMessage, type: NotificationType) {
        // POST_NOTIFICATIONS 권한 OS 차단 시 silently drop — ProfileScreen 토글 흐름에서 권한 요청.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.d(TAG, "POST_NOTIFICATIONS not granted — drop ${type.name}")
                return
            }
        }
        val title = message.notification?.title
            ?: message.data["title"]
            ?: type.displayName
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""

        // 알림 탭 → 앱 메인 진입 (단일 task). deep link 라우팅은 후속.
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("notif_type", type.name)
        }
        val pending = PendingIntent.getActivity(
            this,
            type.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(this, type.channelId)
            .setSmallIcon(R.drawable.logo_octalink) // 단색 아이콘 권장하지만 MVP — 로고 재사용
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(this).notify(nextId(), notif)
    }

    companion object {
        private const val TAG = "OctaLinkFCM"
        // 알림 id — 같은 채널 안에서도 stack 으로 쌓이도록 매번 새 id 부여.
        private val idCounter = AtomicInteger(1000)
        private fun nextId(): Int = idCounter.incrementAndGet()
    }
}
