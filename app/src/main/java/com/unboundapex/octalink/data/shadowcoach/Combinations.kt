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
    /** 화면 표시용 — 넘버링 콜 그대로 "원 · 투 · 훅". */
    fun label(): String = steps.joinToString(" · ") { it.callName }

    /** TTS 발화용 — "원, 투, 훅" (쉼표로 끊어 또박또박). */
    fun spoken(): String = steps.joinToString(", ") { it.callName }
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
            Combo(listOf(J, R)),          // 원·투
            Combo(listOf(J, J)),          // 원·원
            Combo(listOf(J, H)),          // 원·훅
            Combo(listOf(R, H)),          // 투·훅
            Combo(listOf(J, R, J)),       // 원·투·원
            Combo(listOf(J, J, R)),       // 원·원·투
            Combo(listOf(J, R, H)),       // 원·투·훅
        ),
        ComboLevel.INTERMEDIATE to listOf(
            Combo(listOf(J, R, H)),       // 원·투·훅
            Combo(listOf(J, R, U)),       // 원·투·어퍼
            Combo(listOf(R, H, R)),       // 투·훅·투
            Combo(listOf(R, U, H)),       // 투·어퍼·훅
            Combo(listOf(J, J, R, H)),    // 원·원·투·훅
            Combo(listOf(J, U, H, R)),    // 원·어퍼·훅·투
            Combo(listOf(J, R, H, U)),    // 원·투·훅·어퍼
        ),
        ComboLevel.ADVANCED to listOf(
            Combo(listOf(J, R, H, R)),        // 원·투·훅·투
            Combo(listOf(S, R, H)),           // 슬립·투·훅
            Combo(listOf(J, R, D, H)),        // 원·투·더킹·훅
            Combo(listOf(D, H, R, H)),        // 더킹·훅·투·훅
            Combo(listOf(S, R, H, U)),        // 슬립·투·훅·어퍼
            Combo(listOf(W, H, U, R)),        // 위빙·훅·어퍼·투
            Combo(listOf(J, R, S, R, H)),     // 원·투·슬립·투·훅
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
