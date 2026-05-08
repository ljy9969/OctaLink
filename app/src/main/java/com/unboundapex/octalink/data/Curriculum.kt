package com.unboundapex.octalink.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * 평일(월~금) 그룹 수업의 주간 커리큘럼.
 * 토요일은 PT 전용이라 별도 분기.
 * 추후 관장이 매주 갱신할 수 있도록 Firestore weekly_curriculum 컬렉션으로 영속화 예정.
 */
data class CurriculumDay(
    val dayOfWeek: DayOfWeek,
    val theme: String,
    val drills: List<String>,
    val coach: String,
    val tag: String,
)

val weeklyCurriculum: List<CurriculumDay> = listOf(
    CurriculumDay(
        dayOfWeek = DayOfWeek.MONDAY,
        theme = "잽 거리감 + 인사이드 로우킥",
        drills = listOf(
            "1-2 거리 측정 + 스텝 인",
            "프론트 푸시킥 페인트 → 인사이드 로우",
            "패드 라운드 3 × 3분",
        ),
        coach = "관장 김파시",
        tag = "스트라이킹",
    ),
    CurriculumDay(
        dayOfWeek = DayOfWeek.TUESDAY,
        theme = "더블 잽 카운터 + 로우킥",
        drills = listOf(
            "더블 잽 압박 + 백스텝 슬립",
            "상대 잽에 카운터 라이트",
            "1-2 후 인사이드 로우킥 콤보",
        ),
        coach = "코치 박",
        tag = "킥복싱",
    ),
    CurriculumDay(
        dayOfWeek = DayOfWeek.WEDNESDAY,
        theme = "그래플링 / 클린치 컨트롤",
        drills = listOf(
            "더블레그 테이크다운 폼",
            "클린치 + 니 스트라이크",
            "그라운드 컨트롤 → 마운트 이스케이프",
        ),
        coach = "관장 김파시",
        tag = "그래플링",
    ),
    CurriculumDay(
        dayOfWeek = DayOfWeek.THURSDAY,
        theme = "스탠드업 → 클린치 → 테이크다운",
        drills = listOf(
            "1-2 후 거리 좁히기 + 클린치",
            "케이지 푸시 → 더블레그",
            "케이지 컨트롤 1분 라운드",
        ),
        coach = "관장 김파시",
        tag = "MMA",
    ),
    CurriculumDay(
        dayOfWeek = DayOfWeek.FRIDAY,
        theme = "스파링 라운드 (라이트 / 풀)",
        drills = listOf(
            "장비 점검 + 안전 브리핑",
            "라이트 스파링 3 × 2분",
            "쿨다운 + 피드백 1대1",
        ),
        coach = "관장 김파시",
        tag = "스파링",
    ),
)

/** 오늘에 해당하는 커리큘럼 (평일만, 주말이면 null) */
fun curriculumForToday(now: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))): CurriculumDay? =
    weeklyCurriculum.firstOrNull { it.dayOfWeek == now.dayOfWeek }

fun dayLabelKor(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "월"
    DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"
    DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}
