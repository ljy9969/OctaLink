package com.unboundapex.octalink.messaging

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.unboundapex.octalink.data.util.PiiMask

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
        // 토큰 원본은 marketing/identifier 성 정보 — PiiMask 거쳐 길이만 노출.
        // 백엔드 등록 후 디버깅용 토큰 원본이 필요하면 Firestore 의 fcmTokens 컬렉션을 직접 조회.
        Log.i(TAG, "FCM_TOKEN registered ${PiiMask.id(token)}")
        // TODO(푸시 알림 태스크): 백엔드(Firestore users/{uid}/fcmTokens)에 등록
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // data / notification 본문은 사용자 메시지일 수 있어 release 빌드에선 key 만 노출.
        val dataKeys = message.data.keys.joinToString(",")
        Log.i(
            TAG,
            "FCM_MESSAGE from=${PiiMask.id(message.from)}, dataKeys=[$dataKeys], hasNotif=${message.notification != null}",
        )
        // TODO(푸시 알림 태스크): NotificationChannel + NotificationManager로 사용자 알림 표시
    }

    companion object {
        private const val TAG = "OctaLinkFCM"
    }
}
