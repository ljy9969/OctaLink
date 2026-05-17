package com.unboundapex.octalink.messaging

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.unboundapex.octalink.MainActivity
import com.unboundapex.octalink.R
import com.unboundapex.octalink.data.isClosed
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.NotificationType
import com.unboundapex.octalink.data.weeklyPlan
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 수업 시작 30분 전 리마인더 — 클라이언트 사이드 WorkManager 스케줄링.
 *
 * 서버 cron 대신 클라이언트가 매일 자기 디바이스에서 직접 fire — 단일 도장 MVP 의 단순한 선택.
 *
 * **스케줄링 전략**:
 *  1. [scheduleAll]: 오늘 남은 수업들의 (start-30min) 시점에 [ClassReminderWorker] 를 OneTimeWork 로 enqueue.
 *  2. [scheduleDailyRollover]: 매일 04:00 KST 에 [DailyReschedulerWorker] 가 fire → 그날 분 다시 [scheduleAll].
 *  3. 호출 트리거: 앱 시작 시 + 사용자가 [NotificationType.CLASS_REMINDER] ON 으로 토글한 직후.
 *
 * **휴무 처리**: [isClosed] 가 true 인 날은 오늘 스케줄 skip. 자정 넘으면 다음날 04:00 rollover 가
 * 알아서 그날 평가.
 *
 * **prefs 가드**: 스케줄 시점에 user prefs 가 OFF 면 enqueue 안 함. fire 시점에도 재확인.
 */
object ClassReminderScheduler {
    private const val TAG = "OctaLinkClassReminder"
    private const val LEAD_MINUTES = 30L
    private const val WORK_TAG_ONESHOT = "class_reminder_oneshot"
    private const val DAILY_ROLLOVER_WORK = "class_reminder_daily_rollover"
    private val KST: ZoneId = ZoneId.of("Asia/Seoul")

    /**
     * 오늘 남은 수업들에 대해 30분 전 알림 enqueue. 기존 oneshot 모두 취소 후 재등록 (멱등).
     * `awaitPrefCheck = true` 면 Firestore 에서 pref 확인 후 OFF 면 skip.
     */
    suspend fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        // 기존 예약 모두 취소 — 중복/과거 잔재 방지.
        wm.cancelAllWorkByTag(WORK_TAG_ONESHOT)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "scheduleAll skip — no Firebase Auth uid")
            return
        }
        val enabled = isPrefEnabled(uid)
        if (!enabled) {
            Log.d(TAG, "scheduleAll skip — pref OFF")
            return
        }

        val now = LocalDateTime.now(KST)
        val today: LocalDate = now.toLocalDate()
        if (isClosed(today)) {
            Log.d(TAG, "scheduleAll skip — closed day ($today)")
            return
        }

        val slots = todaySlots(today)
        var enqueued = 0
        slots.forEach { slot ->
            val fireAt = LocalDateTime.of(today, slot.start).minusMinutes(LEAD_MINUTES)
            if (!fireAt.isAfter(now)) return@forEach // 이미 지난 시각 skip
            val delayMillis = Duration.between(now, fireAt).toMillis()
            val req = OneTimeWorkRequestBuilder<ClassReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG_ONESHOT)
                .setInputData(
                    workDataOf(
                        "slotStart" to slot.start.toString(),
                        "slotName" to slot.name,
                    )
                )
                .build()
            wm.enqueue(req)
            enqueued++
        }
        Log.i(TAG, "scheduleAll enqueued=$enqueued slots for $today")
    }

    /**
     * 매일 04:00 KST 에 fire 되는 PeriodicWork — 그날 자정 직후 가능한 첫 anchor.
     * 멱등 (KEEP) — 이미 등록돼 있으면 재등록 안 함. 앱 첫 실행에서 1회 enqueue 면 충분.
     */
    fun scheduleDailyRollover(context: Context) {
        val now = LocalDateTime.now(KST)
        var nextFire = now.toLocalDate().atTime(4, 0)
        if (!nextFire.isAfter(now)) nextFire = nextFire.plusDays(1)
        val initialDelayMs = Duration.between(now, nextFire).toMillis()

        val req = PeriodicWorkRequestBuilder<DailyReschedulerWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_ROLLOVER_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
        Log.i(TAG, "scheduleDailyRollover anchored at $nextFire (delay ${initialDelayMs}ms)")
    }

    /** prefs OFF / pref 해제 시 모든 예약 취소. */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG_ONESHOT)
        Log.i(TAG, "cancelAll — pref OFF")
    }

    /** 오늘(요일) 의 ClassSlot 리스트. weeklyPlan 의 "평일" / "토요일" 매핑. */
    private fun todaySlots(today: LocalDate): List<com.unboundapex.octalink.data.ClassSlot> {
        val dayPlan = when (today.dayOfWeek) {
            java.time.DayOfWeek.SATURDAY -> weeklyPlan.firstOrNull { it.title == "토요일" }
            java.time.DayOfWeek.SUNDAY -> null
            else -> weeklyPlan.firstOrNull { it.title == "평일" }
        }
        return dayPlan?.slots.orEmpty()
    }

    private suspend fun isPrefEnabled(uid: String): Boolean {
        val member = runCatching {
            RepositoryProvider.members.observeById(uid).first()
        }.getOrNull()
        val prefs = member?.notificationPrefs.orEmpty()
        return prefs[NotificationType.CLASS_REMINDER.name]
            ?: NotificationType.CLASS_REMINDER.defaultEnabled
    }
}

/**
 * 단일 수업 30분 전 알림 표시 Worker. ClassReminderScheduler 가 enqueue 한 입력으로 동작.
 *
 * fire 시점에 pref/휴무 재확인 — 스케줄 후 사용자가 OFF 했거나 공휴일로 바뀐 경우 drop.
 */
class ClassReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val slotStart = inputData.getString("slotStart") ?: return Result.success()
        val slotName = inputData.getString("slotName") ?: return Result.success()
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        if (isClosed(today)) return Result.success()

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val member = runCatching { RepositoryProvider.members.observeById(uid).first() }.getOrNull()
        val prefs = member?.notificationPrefs.orEmpty()
        val enabled = prefs[NotificationType.CLASS_REMINDER.name]
            ?: NotificationType.CLASS_REMINDER.defaultEnabled
        if (!enabled) return Result.success()

        showNotification(slotStart, slotName)
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(slotStart: String, slotName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val type = NotificationType.CLASS_REMINDER
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("notif_type", type.name)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            type.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val parsed = runCatching { LocalTime.parse(slotStart) }.getOrNull()
        val timeText = parsed?.let { "%02d:%02d".format(it.hour, it.minute) } ?: slotStart
        val notif = NotificationCompat.Builder(applicationContext, type.channelId)
            .setSmallIcon(R.drawable.logo_octalink)
            .setContentTitle("수업 30분 전")
            .setContentText("$timeText $slotName")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$timeText 시작 — $slotName"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(System.currentTimeMillis().toInt(), notif)
    }
}

/**
 * 매일 새벽 4시 fire — 그날의 ClassReminder 들을 다시 스케줄. ClassReminderScheduler.scheduleAll 위임.
 */
class DailyReschedulerWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ClassReminderScheduler.scheduleAll(applicationContext)
        return Result.success()
    }
}
