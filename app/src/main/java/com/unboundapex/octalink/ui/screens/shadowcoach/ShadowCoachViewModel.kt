package com.unboundapex.octalink.ui.screens.shadowcoach

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.unboundapex.octalink.data.shadowcoach.PoseFrame
import com.unboundapex.octalink.data.shadowcoach.PostureCheck
import com.unboundapex.octalink.data.shadowcoach.PostureSample
import com.unboundapex.octalink.data.shadowcoach.ShadowMotionAnalyzer
import com.unboundapex.octalink.data.shadowcoach.ShadowSession
import com.unboundapex.octalink.data.shadowcoach.Technique
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 화면 상태 — 카운터/코칭 칩/요약. 오버레이용 [ShadowCoachViewModel.poseFrame] 은 별도 flow. */
data class ShadowUiState(
    val running: Boolean = false,
    val poseDetected: Boolean = false,
    val jabCount: Int = 0,
    /** 이번 프레임에서 위반 중인 자세 항목 — 실시간 코칭 칩. */
    val liveCues: Set<PostureCheck> = emptySet(),
    val elapsedMs: Long = 0L,
    /** 세션 종료 시 채워짐 → 요약 카드/다이얼로그 노출. null 이면 진행 중/대기. */
    val summary: ShadowSession? = null,
)

/**
 * Shadow Coach 세션 상태 보유 + [ShadowMotionAnalyzer] 결과 누적.
 *
 * 프레임 유입: 화면이 CameraX → PoseLandmarkerHelper 로 [PoseFrame] 을 받아 [onPoseFrame] 호출.
 * 카운트/자세 표본은 [start]~[stop] 사이에만 누적. 오버레이 스켈레톤은 항상 갱신(자리잡기 보조).
 */
class ShadowCoachViewModel : ViewModel() {

    private val analyzer = ShadowMotionAnalyzer()

    private val _ui = MutableStateFlow(ShadowUiState())
    val ui: StateFlow<ShadowUiState> = _ui.asStateFlow()

    private val _poseFrame = MutableStateFlow<PoseFrame?>(null)
    val poseFrame: StateFlow<PoseFrame?> = _poseFrame.asStateFlow()

    private var startedAtMs = 0L
    private var jab = 0
    private var analyzedFrames = 0
    private val postureAcc = mutableMapOf<PostureCheck, PostureSample>()

    fun start() {
        analyzer.reset()
        jab = 0
        analyzedFrames = 0
        postureAcc.clear()
        startedAtMs = SystemClock.elapsedRealtime()
        _ui.value = ShadowUiState(running = true)
    }

    fun stop() {
        if (!_ui.value.running) return
        val duration = SystemClock.elapsedRealtime() - startedAtMs
        val session = ShadowSession(
            techniqueCounts = mapOf(Technique.JAB to jab),
            postureSamples = postureAcc.toMap(),
            durationMs = duration,
            analyzedFrames = analyzedFrames,
        )
        _ui.value = _ui.value.copy(running = false, summary = session, elapsedMs = duration)
    }

    fun dismissSummary() {
        _ui.value = ShadowUiState() // 초기 상태로 (다시 시작 가능)
    }

    /** PoseLandmarkerHelper 결과 콜백. 임의 스레드에서 올 수 있음 — StateFlow 는 thread-safe. */
    fun onPoseFrame(frame: PoseFrame) {
        _poseFrame.value = frame
        val result = analyzer.process(frame)

        if (!_ui.value.running) {
            // 대기 중 — 자세 감지 여부만 반영(시작 전 자리잡기 힌트).
            if (_ui.value.poseDetected != result.posed) {
                _ui.value = _ui.value.copy(poseDetected = result.posed)
            }
            return
        }

        if (result.posed) analyzedFrames++
        jab += result.newReps

        // 자세 표본 누적 (포즈 잡힌 프레임만).
        if (result.posed) {
            PostureCheck.mvpEnabled.forEach { check ->
                if (check == PostureCheck.POOR_EXTENSION) return@forEach // rep 단위로 별도 기록
                val violated = check in result.activeCues
                val prev = postureAcc[check] ?: PostureSample()
                postureAcc[check] = prev.record(violated)
            }
        }
        // 신전 부족 — rep 완료 시 1회 기록.
        if (result.newReps > 0) {
            val prev = postureAcc[PostureCheck.POOR_EXTENSION] ?: PostureSample()
            postureAcc[PostureCheck.POOR_EXTENSION] = prev.record(result.transientPoorExtension)
        }

        _ui.value = _ui.value.copy(
            poseDetected = result.posed,
            jabCount = jab,
            liveCues = result.activeCues,
            elapsedMs = SystemClock.elapsedRealtime() - startedAtMs,
        )
    }
}
