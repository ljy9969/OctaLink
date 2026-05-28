package com.unboundapex.octalink.data

import com.unboundapex.octalink.ui.components.SkillStat

/**
 * 6축 스킬 점수 스냅샷 (현 시점 최신 평가).
 * 값은 0.0~1.0 정규화. 표시 시 0~100 % 로 환산.
 *
 * 영속화: [com.unboundapex.octalink.data.schema.MemberDoc.skills]
 *  - mock 단계: MemberDoc 에 직접 임베드 (현재 구현)
 *  - Firestore 단계: 추후 `members/{id}/skillScores/{scoreId}` 서브컬렉션의 최신 APPROVED 문서
 *    [com.unboundapex.octalink.data.schema.SkillScoreDoc] 가 정식 영속 모델
 */
data class SkillSet(
    val striking: Float = 0f,
    val grappling: Float = 0f,
    val stamina: Float = 0f,
    val technique: Float = 0f,
    val mental: Float = 0f,
    val speed: Float = 0f,
) {
    fun toStats(): List<SkillStat> = listOf(
        SkillStat("스트라이킹", striking),
        SkillStat("그래플링", grappling),
        SkillStat("체력", stamina),
        SkillStat("기술", technique),
        SkillStat("멘탈", mental),
        SkillStat("스피드", speed),
    )

    companion object {
        /** 신규 회원 기본값 — 점수 입력 전 (모두 0). HexagonSkillChart 가 0 도 정상 렌더. */
        val EMPTY = SkillSet()
    }
}

/**
 * 운영자가 아직 스킬 점수를 부여하지 않은 상태.
 * null (Firestore 에 skills map 없음) 이거나 모든 축 0 이면 미평가로 간주.
 *
 * 신규 회원이 AI 코치 루틴을 사용할 때 자가 입력 UI 노출 조건.
 */
fun SkillSet?.isUnset(): Boolean = this == null || this == SkillSet.EMPTY
