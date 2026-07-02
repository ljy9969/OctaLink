package com.unboundapex.octalink.ui.screens.shadowcoach

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import com.unboundapex.octalink.data.shadowcoach.PostureCheck
import java.util.Locale

/**
 * 자세 코칭 음성 안내(TTS). 칩을 눈으로 읽을 수 없는 운동 중에 "가드를 올리세요" 등을 한국어로 발화.
 *
 * 쿨다운: 같은 코칭은 [COOLDOWN_MS] 안에 반복하지 않음. 발화 중이면 새 안내 스킵 — 말이 겹치지 않게.
 * 한 번에 한 항목만(가장 우선 cue) 읽음.
 */
class ShadowTts(context: Context) {
    private var ready = false
    private val lastSpokenAt = mutableMapOf<String, Long>()

    // lateinit + init: onInit 콜백이 tts 를 참조하므로 val 자기참조(재귀) 회피.
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                runCatching { tts.language = Locale.KOREAN }
                ready = true
            }
        }
    }

    /** 현재 위반 중인 자세 항목 집합을 받아, 쿨다운 지난 것 하나를 음성 안내. */
    fun announce(cues: Set<PostureCheck>) {
        if (!ready || cues.isEmpty()) return
        if (tts.isSpeaking) return
        val now = SystemClock.elapsedRealtime()
        // PostureCheck.mvpEnabled 순서대로(우선순위) 첫 발화 가능 항목 1개.
        for (cue in PostureCheck.mvpEnabled) {
            if (cue !in cues) continue
            val last = lastSpokenAt[cue.name] ?: 0L
            if (now - last >= COOLDOWN_MS) {
                tts.speak(cue.cue, TextToSpeech.QUEUE_FLUSH, null, cue.name)
                lastSpokenAt[cue.name] = now
                return
            }
        }
    }

    /**
     * 콤비네이션 추천 발화 — 사용자가 버튼을 눌러 요청한 것이라 쿨다운 무시, 진행 중 코칭을
     * 끊고(QUEUE_FLUSH) 바로 읽음. 예: "중급 콤비네이션. 잽, 라이트, 훅."
     */
    fun callCombo(levelName: String, spokenSteps: String) {
        if (!ready) return
        runCatching { tts.speak("$levelName 콤비네이션. $spokenSteps", TextToSpeech.QUEUE_FLUSH, null, "combo") }
    }

    private var lastEncourageAt = 0L
    private var encourageIdx = 0

    /** 격려 음성 — 마일스톤(일정 펀치 수)마다 호출. 교정 발화 중이면 양보, 자체 쿨다운. */
    fun encourage() {
        if (!ready || tts.isSpeaking) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastEncourageAt < ENCOURAGE_COOLDOWN) return
        lastEncourageAt = now
        val phrase = ENCOURAGEMENTS[encourageIdx % ENCOURAGEMENTS.size]
        encourageIdx++
        tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "encourage")
    }

    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }

    companion object {
        private const val COOLDOWN_MS = 4500L
        private const val ENCOURAGE_COOLDOWN = 6000L
        private val ENCOURAGEMENTS = listOf(
            "좋아요 계속 가요",
            "잘하고 있어요",
            "리듬 좋아요",
            "그대로 유지해요",
        )
    }
}
