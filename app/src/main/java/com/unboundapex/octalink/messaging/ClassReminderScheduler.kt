package com.unboundapex.octalink.messaging

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.unboundapex.octalink.MainActivity
import com.unboundapex.octalink.R
import com.unboundapex.octalink.data.allWeeklyClassSlots
import com.unboundapex.octalink.data.classSlotKey
import com.unboundapex.octalink.data.isClosed
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.NotificationType
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 수업 시작 30분 전 리마인더 — 클라이언트 사이드 알람 스케줄링.
 *
 * 서버 cron 대신 클라이언트가 매일 자기 디바이스에서 직접 fire — 단일 도장 MVP 의 단순한 선택.
 *
 * **스케줄링 백엔드 (06-04 변경)**: 정확 시각 보장을 위해 **AlarmManager.setAlarmClock** 사용.
 * 이전 WorkManager `OneTimeWorkRequestBuilder.setInitialDelay` 방식은 디바이스 Doze/대기 상태에서
 * 10~30 분 배치 지연이 발생 — 수업 30분 전 알람이 19분 전, 11분 전으로 늦게 와서 회원이 못 봄.
 * `setAlarmClock` 은 **정확 시각 + Doze 우회 + 권한 불요** (SCHEDULE_EXACT_ALARM 안 필요).
 *
 * **스케줄링 전략**:
 *  1. [scheduleAll]: 오늘 남은 수업들의 (start-30min) 시점에 알람 1개씩 set. PendingIntent 의
 *     requestCode 는 `slotKey.hashCode()` — 슬롯별 고유, 재호출 시 같은 PendingIntent 로 덮어쓰기.
 *  2. [scheduleDailyRollover]: 매일 04:00 KST 에 [DailyReschedulerWorker] (WorkManager Periodic) fire
 *     → 그날 분 다시 [scheduleAll]. 4 AM 정확성은 불필요 (수업 시작까지 충분한 여유) 라 Worker OK.
 *  3. 호출 트리거: 앱 시작 시 + 사용자가 [NotificationType.CLASS_REMINDER] ON 으로 토글한 직후 + 슬롯 변경 시.
 *
 * **휴무 처리**: [isClosed] 가 true 인 날은 오늘 스케줄 skip. [ClassReminderAlarmReceiver] 가
 * fire 시점에 다시 [isClosed] 확인 — 스케줄 후 공휴일로 바뀐 케이스 방어.
 *
 * **재부팅 후 알람 손실**: AlarmManager 알람은 디바이스 재부팅 시 모두 사라짐. 사용자가 앱을 다시
 * 열면 [scheduleAll] 이 재실행돼 복원. 자동 부팅 복원이 필요해지면 BOOT_COMPLETED 리시버 추가.
 *
 * **prefs 가드**: 스케줄 시점에 user prefs 가 OFF 면 enqueue 안 함. fire 시점엔 prefs 재확인 안 함 —
 * 스케줄→fire 간격이 짧고(<24시간) 사용자가 그 사이 OFF 한 경우 한 번 알람 나가는 건 허용 가능.
 */
object ClassReminderScheduler {
    private const val TAG = "OctaLinkClassReminder"
    private const val LEAD_MINUTES = 30L
    private const val DAILY_ROLLOVER_WORK = "class_reminder_daily_rollover"
    private val KST: ZoneId = ZoneId.of("Asia/Seoul")

    internal const val INTENT_ACTION = "com.unboundapex.octalink.CLASS_REMINDER"
    internal const val EXTRA_SLOT_KEY = "slotKey"
    internal const val EXTRA_SLOT_START = "slotStart"
    internal const val EXTRA_SLOT_NAME = "slotName"

    /**
     * 오늘 남은 수업 중 **회원이 선택한 슬롯** 만 30분 전 알람 set.
     * 기존 알람 모두 cancel 후 재등록 (멱등). 휴무일 / uid 없음 / 선택 슬롯 없음 → skip.
     *
     * 선택 슬롯 = `members/{uid}.classReminderSlots` (예: `["MONDAY_19:30", "WEDNESDAY_19:30"]`).
     */
    suspend fun scheduleAll(context: Context) {
        val alarmManager = context.getSystemService<AlarmManager>()
        if (alarmManager == null) {
            Log.w(TAG, "scheduleAll skip — AlarmManager 사용 불가")
            return
        }

        // 기존 예약 모두 취소 — 중복/과거 잔재 방지. 주간 모든 슬롯 키로 PendingIntent 조회 후 cancel.
        cancelAllAlarms(context, alarmManager)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "scheduleAll skip — no Firebase Auth uid")
            return
        }
        val selectedSlots = loadSelectedSlots(uid)
        if (selectedSlots.isEmpty()) {
            Log.d(TAG, "scheduleAll skip — no class reminder slots selected")
            return
        }

        val now = LocalDateTime.now(KST)
        val today: LocalDate = now.toLocalDate()
        if (isClosed(today)) {
            Log.d(TAG, "scheduleAll skip — closed day ($today)")
            return
        }

        // 오늘 요일의 슬롯들 중 사용자가 선택한 것만 + 시작 시각이 미래(또는 정확히 지금)인 것만.
        val todayDow = today.dayOfWeek
        var scheduled = 0
        allWeeklyClassSlots()
            .filter { (day, _) -> day == todayDow }
            .forEach { (day, slot) ->
                val key = classSlotKey(day, slot)
                if (key !in selectedSlots) return@forEach
                val fireAt = LocalDateTime.of(today, slot.start).minusMinutes(LEAD_MINUTES)
                if (!fireAt.isAfter(now)) return@forEach // 이미 지난 시각 skip
                val triggerMs = fireAt.atZone(KST).toInstant().toEpochMilli()

                val pending = buildPendingIntent(
                    context = context,
                    key = key,
                    slotStartIso = slot.start.toString(),
                    slotName = slot.name,
                    create = true,
                )!!

                // setAlarmClock — 정확 시각 보장 + Doze 우회 + 권한 불요.
                // AlarmClockInfo 의 showIntent 도 같은 PendingIntent 사용 — 상태바의 알람 아이콘 탭 시 동작.
                val info = AlarmManager.AlarmClockInfo(triggerMs, pending)
                alarmManager.setAlarmClock(info, pending)
                scheduled++
                Log.i(TAG, "scheduleAll set alarm: $key at $fireAt (${Duration.between(now, fireAt).toMinutes()} min later)")
            }
        Log.i(TAG, "scheduleAll done: $scheduled alarms for $today (selected total=${selectedSlots.size})")
    }

    /**
     * 매일 04:00 KST 에 fire 되는 PeriodicWork — 그날 자정 직후 가능한 첫 anchor.
     * 멱등 (KEEP) — 이미 등록돼 있으면 재등록 안 함. 앱 첫 실행에서 1회 enqueue 면 충분.
     *
     * 4 AM 정확성은 불필요 (수업 시작까지 충분한 여유) 라 WorkManager Periodic 유지 — 배치 지연 OK.
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

    /** 선택 슬롯 전부 해제 시 모든 예약 취소. */
    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        cancelAllAlarms(context, alarmManager)
        Log.i(TAG, "cancelAll — no slots selected")
    }

    /**
     * 주간 모든 슬롯 키에 대해 PendingIntent 조회 → 존재하면 alarmManager cancel + PendingIntent cancel.
     * 일부만 선택해도 31 슬롯 전부 cancel 시도 — `FLAG_NO_CREATE` 라 미존재 키는 null 반환, no-op.
     */
    private fun cancelAllAlarms(context: Context, alarmManager: AlarmManager) {
        allWeeklyClassSlots().forEach { (day, slot) ->
            val key = classSlotKey(day, slot)
            val existing = buildPendingIntent(
                context = context,
                key = key,
                slotStartIso = "",
                slotName = "",
                create = false,
            )
            existing?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    /**
     * 슬롯 키마다 고유 PendingIntent 생성/조회.
     *
     * @param create true 면 `FLAG_UPDATE_CURRENT` (덮어쓰기 OK), false 면 `FLAG_NO_CREATE` (조회만 — 없으면 null).
     * Action / extras 는 같은 키면 동일 — Intent equality 가 PendingIntent matching 에 사용됨.
     */
    private fun buildPendingIntent(
        context: Context,
        key: String,
        slotStartIso: String,
        slotName: String,
        create: Boolean,
    ): PendingIntent? {
        val intent = Intent(context, ClassReminderAlarmReceiver::class.java).apply {
            action = INTENT_ACTION
            putExtra(EXTRA_SLOT_KEY, key)
            if (create) {
                putExtra(EXTRA_SLOT_START, slotStartIso)
                putExtra(EXTRA_SLOT_NAME, slotName)
            }
        }
        val flags = if (create)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, key.hashCode(), intent, flags)
    }

    private suspend fun loadSelectedSlots(uid: String): Set<String> {
        val member = runCatching {
            RepositoryProvider.members.observeById(uid).first()
        }.getOrNull()
        return member?.classReminderSlots.orEmpty().toSet()
    }
}

/**
 * AlarmManager 가 호출하는 알람 수신기 — fire 시점에 휴무 재확인 후 알림 표시.
 *
 * onReceive 는 ~10초 budget — Firestore async read 등 무거운 작업 회피.
 * 슬롯 선택 변경의 fire-time 재확인은 생략 (스케줄→fire 간격이 짧음, 한 번 stale 알람 허용).
 */
class ClassReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ClassReminderScheduler.INTENT_ACTION) return

        val slotKey = intent.getStringExtra(ClassReminderScheduler.EXTRA_SLOT_KEY) ?: return
        val slotStart = intent.getStringExtra(ClassReminderScheduler.EXTRA_SLOT_START) ?: return
        val slotName = intent.getStringExtra(ClassReminderScheduler.EXTRA_SLOT_NAME) ?: return

        Log.i("OctaLinkClassReminder", "alarm fired: $slotKey at ${LocalDateTime.now(ZoneId.of("Asia/Seoul"))}")

        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        if (isClosed(today)) {
            Log.d("OctaLinkClassReminder", "alarm $slotKey dropped — closed day")
            return
        }

        showNotification(context, slotStart, slotName)
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(context: Context, slotStart: String, slotName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val type = NotificationType.CLASS_REMINDER
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("notif_type", type.name)
        }
        val pending = PendingIntent.getActivity(
            context,
            type.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val parsed = runCatching { LocalTime.parse(slotStart) }.getOrNull()
        val timeText = parsed?.let { "%02d:%02d".format(it.hour, it.minute) } ?: slotStart
        val notif = NotificationCompat.Builder(context, type.channelId)
            .setSmallIcon(R.drawable.logo_octalink)
            .setContentTitle("수업 30분 전")
            .setContentText("$timeText $slotName")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$timeText 시작 — $slotName"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context)
            .notify(System.currentTimeMillis().toInt(), notif)
    }
}

/**
 * 매일 새벽 4시 fire — 그날의 ClassReminder 들을 다시 스케줄. ClassReminderScheduler.scheduleAll 위임.
 *
 * 4 AM 정확성은 불필요 (수업 시작까지 충분한 여유) — WorkManager Periodic 의 배치 지연 OK.
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
