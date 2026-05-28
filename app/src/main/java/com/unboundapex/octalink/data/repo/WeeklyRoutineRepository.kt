package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.schema.WeeklyRoutineDoc
import kotlinx.coroutines.flow.Flow

/**
 * AI 가 생성한 주간 보강 루틴 영속화 추상화.
 *
 * Firestore 경로: `members/{memberId}/recommendations/{weekId}` —
 * doc id 는 ISO 주키 ("2026-W22"). Cloud Function `generateWeeklyRoutine` 이 doc 을
 * 만들고 클라이언트는 read + observe + manual trigger.
 */
interface WeeklyRoutineRepository {
    /** 이번 주 (KST 기준) 루틴 doc 실시간 구독. 미생성 시 null. */
    fun observeCurrentWeek(memberId: String): Flow<WeeklyRoutineDoc?>

    /**
     * Cloud Function `generateWeeklyRoutine` 호출 — 캐시가 있으면 그대로,
     * `force = true` 면 재생성. Firestore 에 결과 저장 → observe 가 후속 갱신.
     *
     * [difficulty] : "BEGINNER" / "INTERMEDIATE" / "ADVANCED" — Gemini 가 드릴 강도/복잡도를
     * 사용자 선택에 맞춰 조정. UI 에서 매번 선택받음.
     *
     * [selfRatedSkills] : 운영자가 아직 스킬 점수를 부여하지 않은 신규 회원이 본인이 생각하는
     * 6축 점수(0.0~1.0)를 임의로 입력해 AI 프롬프트 참고용으로만 전달. 키는 SkillSet 필드명
     * ("striking" / "grappling" / "stamina" / "technique" / "mental" / "speed"). null 이면
     * 서버가 기존 로직(member.skills 또는 0.5 평탄화) 사용. **회원 프로필에는 저장되지 않음.**
     */
    suspend fun generate(
        memberId: String,
        difficulty: String,
        force: Boolean = false,
        selfRatedSkills: Map<String, Float>? = null,
    )

    /** 드릴 done/skipped 상태 토글 — Phase 2 피드백 루프용. */
    suspend fun setDrillState(
        memberId: String,
        weekId: String,
        dayIndex: Int,
        drillIndex: Int,
        done: Boolean,
        skipped: Boolean,
    )
}
