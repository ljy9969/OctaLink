package com.teamposse.striking.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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
