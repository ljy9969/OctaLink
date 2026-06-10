package com.unboundapex.octalink.messaging

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.getSystemService

/**
 * 정확 시각 알람(SCHEDULE_EXACT_ALARM) 권한 헬퍼.
 *
 * **배경**: 수업 30분 전 리마인더는 `AlarmManager.setAlarmClock` 으로 정시 발화하는데, Android 12+
 * (API 31+) 부터 이 호출에 SCHEDULE_EXACT_ALARM 권한이 필요. USE_EXACT_ALARM 은 '알람 시계/캘린더'
 * 앱 전용이라 회원 관리 앱인 OctaLink 엔 부적격 → SCHEDULE_EXACT_ALARM 사용.
 *
 * **부여 방식 (OS 버전별 차이)**:
 *  - Android 12 (API 31~32): install 시 자동 부여 (단 사용자가 설정에서 끌 수 있음).
 *  - Android 13+ (API 33+): targetSdk 33+ 신규 설치 앱은 **자동 부여 안 됨** — 사용자가 설정의
 *    "알람 및 리마인더" 특별 액세스에서 직접 허용해야 함. [launchSettings] 로 해당 화면 유도.
 *
 * [canSchedule] 이 false 면 [ClassReminderScheduler.scheduleAll] 이 graceful skip + UI 가 안내 행 노출.
 */
object ExactAlarmHelper {

    /** 현재 정확 알람 예약이 가능한지. Android 12 미만은 권한 개념 없어 항상 true. */
    fun canSchedule(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService<AlarmManager>() ?: return false
        return am.canScheduleExactAlarms()
    }

    /**
     * "알람 및 리마인더" 특별 액세스 설정 화면 진입 — Android 12+ 전용.
     * 사용자가 여기서 OctaLink 를 허용하면 [canSchedule] 이 true 로 전환.
     */
    fun launchSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                // 일부 OEM 은 위 액션 미지원 — 앱 상세 페이지 폴백.
                BatteryOptimizationHelper.openAppDetails(context)
            }
    }
}
