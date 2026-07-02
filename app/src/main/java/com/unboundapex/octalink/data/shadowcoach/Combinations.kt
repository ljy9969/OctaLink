package com.unboundapex.octalink.data.shadowcoach

import kotlin.random.Random

/** 콤비네이션 난이도. */
enum class ComboLevel(val displayName: String) {
    BEGINNER("초급"),
    INTERMEDIATE("중급"),
    ADVANCED("고급"),
}

/**
 * 펀치/회피 동작 시퀀스 콤비네이션.
 * TTS 로 읽어줄 때는 각 스텝의 [Technique.displayName] 을 순서대로 나열.
 */
data class Combo(val steps: List<Technique>) {
    /** 화면 표시·음성용 — "잽 · 라이트 · 훅". */
    fun label(): String = steps.joinToString(" · ") { it.displayName }

    /** TTS 발화용 — "잽, 라이트, 훅" (쉼표로 끊어 또박또박). */
    fun spoken(): String = steps.joinToString(", ") { it.displayName }
}

/**
 * 난이도별 쉐도우 복싱 콤비네이션 세트.
 *
 * 초급: 스트레이트 계열 2~3타(기본기), 중급: 훅·어퍼 섞은 3~4타,
 * 고급: 회피(더킹/슬립/위빙) 포함 4~5타. [random] 으로 매번 다른 콤비를 추천.
 */
object Combinations {
    private val J = Technique.JAB
    private val R = Technique.STRAIGHT
    private val H = Technique.HOOK
    private val U = Technique.UPPERCUT
    private val D = Technique.DUCK
    private val S = Technique.SLIP
    private val W = Technique.WEAVE

    private val sets: Map<ComboLevel, List<Combo>> = mapOf(
        ComboLevel.BEGINNER to listOf(
            Combo(listOf(J, R)),
            Combo(listOf(J, J)),
            Combo(listOf(J, R, J)),
            Combo(listOf(R, H)),
            Combo(listOf(J, R, H)),
        ),
        ComboLevel.INTERMEDIATE to listOf(
            Combo(listOf(J, R, H)),
            Combo(listOf(J, R, U)),
            Combo(listOf(R, H, R)),
            Combo(listOf(J, J, R, H)),
            Combo(listOf(J, U, H, R)),
        ),
        ComboLevel.ADVANCED to listOf(
            Combo(listOf(J, R, H, R)),
            Combo(listOf(S, R, H)),
            Combo(listOf(D, H, R, H)),
            Combo(listOf(J, R, S, R, H)),
            Combo(listOf(W, H, U, R)),
        ),
    )

    fun forLevel(level: ComboLevel): List<Combo> = sets[level].orEmpty()

    /** 해당 난이도에서 콤비 1개 무작위 추천. [exclude] 와 같으면 다시 뽑아 연속 중복 회피. */
    fun random(level: ComboLevel, exclude: Combo? = null): Combo {
        val pool = forLevel(level)
        if (pool.isEmpty()) return Combo(emptyList())
        if (pool.size == 1) return pool[0]
        var pick = pool[Random.nextInt(pool.size)]
        var guard = 0
        while (pick == exclude && guard++ < 5) pick = pool[Random.nextInt(pool.size)]
        return pick
    }
}
