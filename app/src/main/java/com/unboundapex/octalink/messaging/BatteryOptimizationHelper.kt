package com.unboundapex.octalink.messaging

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService

/**
 * 배터리 최적화 제외 (Doze 우회) 권한 헬퍼.
 *
 * **왜 필요한가**: `AlarmManager.setAlarmClock` 이 표준 Android Doze 는 우회하지만, OEM
 * (특히 삼성 One UI) 의 더 공격적인 "절전 앱 / Sleeping apps" 정책에 묶이면 알람조차 fire 안 됨.
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 권한 받으면 OS 의 표준 배터리 최적화에서 제외되어
 * 백그라운드 알람 발화 안정성 크게 향상.
 *
 * **Google Play 정책**: 이 권한은 "alarm / calendar" 카테고리 앱에 한해 허용. OctaLink 의
 * CLASS_REMINDER (수업 시작 30분 전 정시 발화) 가 이에 해당.
 *
 * **OEM 추가 정책 (코드로 못 푸는 영역)**: 삼성의 "절전 앱" / 화웨이의 PowerGenie 등은 별도.
 * 사용자가 OS 설정 (설정 → 디바이스 케어 → 배터리 → 백그라운드 사용 한도 → 절대 절전 안 함
 * 앱) 에서 직접 OctaLink 추가 필요. 안내 다이얼로그로 사용자에게 명시.
 */
object BatteryOptimizationHelper {

    /** 현재 OctaLink 가 배터리 최적화에서 제외돼 있는지. */
    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService<PowerManager>() ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 시스템 다이얼로그로 배터리 최적화 제외 권한 요청.
     * 사용자가 "허용" 누르면 [isIgnoring] 이 true 로 전환.
     * "허용 안 함" 누르면 변동 없음 — 사용자가 직접 설정에서 풀어줘야 함.
     *
     * @SuppressLint("BatteryLife") — Google Play 가 제한된 권한 직접 요청을 lint warning 하는데,
     * alarm 앱 카테고리는 정책상 허용. Play Console 의 권한 선언 양식에서 사유 명시 필요.
     */
    @SuppressLint("BatteryLife")
    fun launchRequest(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 시스템 배터리 최적화 설정 페이지 진입 — 다이얼로그 거부 후 사용자가 직접 풀어야 할 때.
     */
    fun launchSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 앱 알림 설정 페이지 직접 진입 — POST_NOTIFICATIONS 거부 또는 사용자가 OS 알림 채널을
     * 끈 경우 안내용. 사용자가 "알림 권한" 항목을 직접 토글.
     */
    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 앱 상세 정보 페이지 직접 진입 — "사용량 → 배터리 → 제한 없음" 항목을 사용자가
     * 직접 찾아가야 할 때 (특히 삼성 One UI 의 "절전 앱" 은 별도 API 없어서 이 페이지에서 처리).
     */
    fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
