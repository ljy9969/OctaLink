package com.unboundapex.octalink.messaging

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 기반 작업 — 현재 단계는 토큰 발급 + 메시지 수신 로깅만.
 *
 * 실제 사용자 알림 표시(NotificationChannel + NotificationManager + deep link)는
 * 남은 일의 "푸시 알림 (FCM)" 태스크에서 구현 예정. 그 전까지는 Firebase Console
 * → Cloud Messaging → 테스트 메시지 송신으로 FCM 동작만 검증.
 *
 * 토큰 확인:
 *   1. 앱 첫 실행 시 onNewToken 호출 → Logcat에서 "FCM_TOKEN" 검색
 *   2. 또는 임시로 MainActivity에서 FirebaseMessaging.getInstance().token.addOnSuccessListener { ... }
 *
 * 테스트 메시지 송신:
 *   Firebase Console → DevOps 및 사용자 참여 → Cloud Messaging → 첫 캠페인 만들기
 *   → Notifications composer → 토큰 또는 토픽 지정 → 전송
 */
class OctaLinkMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM_TOKEN: $token")
        // TODO(푸시 알림 태스크): 백엔드(Firestore users/{uid}/fcmTokens)에 등록
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.i(TAG, "FCM_MESSAGE from=${message.from} data=${message.data} notif=${message.notification?.title}")
        // TODO(푸시 알림 태스크): NotificationChannel + NotificationManager로 사용자 알림 표시
    }

    companion object {
        private const val TAG = "OctaLinkFCM"
    }
}
