package com.unboundapex.octalink.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

data class ClassSlot(
    val start: LocalTime,
    val end: LocalTime,
    val name: String,
) {
    val timeRangeText: String
        get() = "${start.format2digit()} - ${end.format2digit()}"

    fun contains(t: LocalTime): Boolean = t >= start && t < end
}

private fun LocalTime.format2digit(): String =
    "%02d:%02d".format(hour, minute)

data class DayPlan(
    val title: String,
    val slots: List<ClassSlot>,
    val footer: String? = null,
    val closed: Boolean = false,
)

private val weekdaySlots = listOf(
    ClassSlot(LocalTime.of(11, 0), LocalTime.of(12, 0), "오픈 매트"),
    ClassSlot(LocalTime.of(12, 0), LocalTime.of(13, 0), "복싱 · 킥복싱 · MMA"),
    ClassSlot(LocalTime.of(13, 0), LocalTime.of(17, 0), "오픈 매트"),
    ClassSlot(LocalTime.of(18, 0), LocalTime.of(19, 0), "복싱 · 킥복싱 · MMA"),
    ClassSlot(LocalTime.of(19, 30), LocalTime.of(20, 30), "복싱 · 킥복싱 · MMA"),
    ClassSlot(LocalTime.of(21, 0), LocalTime.of(22, 0), "복싱 · 킥복싱 · MMA"),
)

private val saturdaySlots = listOf(
    ClassSlot(LocalTime.of(11, 0), LocalTime.of(17, 0), "PT 전용"),
)

val weeklyPlan: List<DayPlan> = listOf(
    DayPlan(
        title = "평일",
        slots = weekdaySlots,
        footer = "개인 · 그룹 · 키즈 PT",
    ),
    DayPlan(
        title = "토요일",
        slots = saturdaySlots,
        footer = "개인 · 그룹 · 키즈",
    ),
)

fun isHoliday(date: LocalDate): Boolean = HolidayRepository.isHoliday(date)

fun isClosed(date: LocalDate): Boolean =
    date.dayOfWeek == DayOfWeek.SUNDAY || isHoliday(date)

private fun slotsFor(day: DayOfWeek): List<ClassSlot> = when (day) {
    DayOfWeek.SUNDAY -> emptyList()
    DayOfWeek.SATURDAY -> saturdaySlots
    else -> weekdaySlots
}

/**
 * 주간 운영 슬롯 전체 — CLASS_REMINDER 세분화 UI 가 enumerate.
 * 월~금(평일 6개) + 토 1개 = 31 슬롯. 일요일/공휴일 (휴무) 은 포함 안 됨.
 *
 * 반환 순서: 월요일부터 토요일까지, 각 요일 내에선 시작 시각 오름차순.
 */
fun allWeeklyClassSlots(): List<Pair<DayOfWeek, ClassSlot>> = buildList {
    val weekdays = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    weekdays.forEach { day -> weekdaySlots.forEach { add(day to it) } }
    saturdaySlots.forEach { add(DayOfWeek.SATURDAY to it) }
}

/**
 * 알림 ON/OFF 단위 — `"MONDAY_19:30"` 형식.
 * [com.unboundapex.octalink.data.schema.MemberDoc.classReminderSlots] 의 key.
 */
fun classSlotKey(day: DayOfWeek, slot: ClassSlot): String =
    "${day.name}_%02d:%02d".format(slot.start.hour, slot.start.minute)

/**
 * 현재 ClassReminderConfigDialog 가 노출하는 *유효* 슬롯 키 셋.
 * 평일(월~금) × 그룹 수업(복싱·킥복싱·MMA) 4시간대 = 20개. 토요일 PT / 평일 오픈 매트는 제외.
 *
 * 마이그레이션용 — 이전 다이얼로그(전 슬롯 노출) 에서 저장된 키 중 이 셋에 없는 항목은 legacy
 * 잔재이므로 정리 대상. [NotificationPrefsViewModel.observeFor] 가 1회성 정리 + UI 필터에 사용.
 */
val validClassReminderKeys: Set<String> = buildSet {
    val weekdays = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    val groupSlots = weekdaySlots.filter { it.name == "복싱 · 킥복싱 · MMA" }
    weekdays.forEach { day ->
        groupSlots.forEach { slot -> add(classSlotKey(day, slot)) }
    }
}

/**
 * 현재 시각 기준 진행 중인 수업 → 표시.
 * 진행 중인 수업이 없으면 다음 수업 → 표시.
 * 오늘 수업이 모두 끝났거나 휴무이면 그에 맞는 메시지.
 */
fun currentOrNextClassLabel(now: LocalDateTime = LocalDateTime.now(KST)): String {
    val today = now.toLocalDate()
    if (isClosed(today)) {
        return "오늘 휴무"
    }

    val slots = slotsFor(today.dayOfWeek)
    if (slots.isEmpty()) return "오늘 수업 없음"

    val time = now.toLocalTime()
    val current = slots.firstOrNull { it.contains(time) }
    if (current != null) return "${current.start.format2digit()} ${current.name} (진행 중)"

    val next = slots.firstOrNull { time < it.start }
    if (next != null) return "다음 ${next.start.format2digit()} ${next.name}"

    return "오늘 수업 종료"
}

/** 체크인 게이팅 상태. UI 가 분기 메시지/버튼 라벨에 사용. */
enum class CheckInWindow { OPEN, CLOSED_DAY, NO_CLASS_TODAY, BEFORE_WINDOW, AFTER_LAST_CLASS }

/**
 * 출석 체크인 허용 여부.
 * 규칙:
 * - 휴무일 (일/공휴일) → CLOSED_DAY
 * - 진행 중인 수업이 있으면 → OPEN
 * - 다음 수업까지 30분 이내 → OPEN
 * - 다음 수업까지 30분 초과 남음 → BEFORE_WINDOW
 * - 오늘 수업이 끝났으면 → AFTER_LAST_CLASS
 * - 오늘 수업 자체가 없으면 → NO_CLASS_TODAY
 */
fun checkInWindow(now: LocalDateTime = LocalDateTime.now(KST)): CheckInWindow {
    val today = now.toLocalDate()
    if (isClosed(today)) return CheckInWindow.CLOSED_DAY

    val slots = slotsFor(today.dayOfWeek)
    if (slots.isEmpty()) return CheckInWindow.NO_CLASS_TODAY

    val time = now.toLocalTime()
    if (slots.any { it.contains(time) }) return CheckInWindow.OPEN

    val next = slots.firstOrNull { time < it.start }
    if (next == null) return CheckInWindow.AFTER_LAST_CLASS

    val minutesUntil = ChronoUnit.MINUTES.between(time, next.start)
    return if (minutesUntil <= 30) CheckInWindow.OPEN else CheckInWindow.BEFORE_WINDOW
}
