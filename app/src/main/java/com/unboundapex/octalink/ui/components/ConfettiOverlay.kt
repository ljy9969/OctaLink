package com.unboundapex.octalink.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    val rotationSpeed: Float,
    val size: Float,
    val color: Color,
    val shape: Int,
    var life: Float,
)

private val confettiPalette = listOf(
    Color(0xFFFF3B3B),
    Color(0xFFFFB22C),
    Color(0xFFFFE16A),
    Color(0xFFFFFFFF),
    Color(0xFF2BB6FF),
    Color(0xFFFF4FA3),
    Color(0xFF7A5BFF),
)

/**
 * 챔피언 결정 시 1회 재생되는 색종이 폭죽 오버레이.
 *
 * triggerKey 가 null → non-null 로 변할 때 발사. 동일 값이 유지되면 다시 안 터짐.
 * 기본 발사 위치는 캔버스 하단 중앙(82%) — [origin] 으로 override 가능. UI 가 ChampionBanner 위치를
 * `onGloballyPositioned` 로 캡처해 전달하면 거기서 폭죽이 터짐. Canvas 기본 동작으로 입력 이벤트는 가로채지 않음.
 *
 * 사용:
 * ```
 * Box {
 *     // 기존 콘텐츠
 *     ConfettiOverlay(
 *         triggerKey = champion,
 *         origin = bannerCenter, // optional, null = 기본 위치
 *         modifier = Modifier.matchParentSize(),
 *     )
 * }
 * ```
 */
@Composable
fun ConfettiOverlay(
    triggerKey: Any?,
    modifier: Modifier = Modifier,
    origin: Offset? = null,
) {
    val particles = remember { mutableListOf<ConfettiParticle>() }
    var frame by remember { mutableLongStateOf(0L) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    // 첫 burst 직전에 origin 이 아직 set 되지 않은 경우 다음 burst 부터는 최신 값으로 emit 되도록
    // [rememberUpdatedState] 사용 — LaunchedEffect 재시작 없이 loop 안에서 최신 값 read.
    val originRef by rememberUpdatedState(newValue = origin)

    LaunchedEffect(triggerKey, canvasSize.width, canvasSize.height) {
        if (triggerKey == null || canvasSize == Size.Zero) {
            if (particles.isNotEmpty()) {
                particles.clear()
                frame = System.nanoTime()
            }
            return@LaunchedEffect
        }

        val rng = Random(System.nanoTime())
        particles.clear()
        // 3연발 — 좀 더 큰 잔치 느낌
        val burstTimes = longArrayOf(0L, 220L, 480L)
        var burstIdx = 0

        val start = withFrameNanos { it }
        var last = start
        val gravity = 1100f
        val airDrag = 0.6f

        while (true) {
            val now = withFrameNanos { it }
            val elapsedMs = (now - start) / 1_000_000
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now

            while (burstIdx < burstTimes.size && elapsedMs >= burstTimes[burstIdx]) {
                // 매 burst 마다 최신 origin 다시 read — 첫 frame 에 banner 위치가 아직 안 들어왔어도
                // 다음 burst (220ms / 480ms) 부터는 정확한 위치에서 emit.
                val o = originRef ?: Offset(canvasSize.width / 2f, canvasSize.height * 0.82f)
                repeat(35) {
                    // 위로 솟는 부채꼴 (-150° ~ -30°)
                    val angleDeg = -150f + rng.nextFloat() * 120f
                    val angleRad = angleDeg * (PI / 180.0).toFloat()
                    val speed = 600f + rng.nextFloat() * 500f
                    val jitterX = (rng.nextFloat() - 0.5f) * 120f
                    particles.add(
                        ConfettiParticle(
                            x = o.x + jitterX,
                            y = o.y,
                            vx = cos(angleRad) * speed,
                            vy = sin(angleRad) * speed,
                            rotation = rng.nextFloat() * 360f,
                            rotationSpeed = (rng.nextFloat() - 0.5f) * 720f,
                            size = 8f + rng.nextFloat() * 12f,
                            color = confettiPalette[rng.nextInt(confettiPalette.size)],
                            shape = if (rng.nextFloat() < 0.7f) 0 else 1,
                            life = 2.6f + rng.nextFloat() * 1.4f,
                        )
                    )
                }
                burstIdx++
            }

            val iter = particles.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                p.vy += gravity * dt
                p.vx *= (1f - airDrag * dt).coerceAtLeast(0f)
                p.vy *= (1f - airDrag * 0.35f * dt).coerceAtLeast(0f)
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.rotation += p.rotationSpeed * dt
                p.life -= dt
                if (p.life <= 0f || p.y > canvasSize.height + 80f) {
                    iter.remove()
                }
            }
            frame = now

            if (burstIdx >= burstTimes.size && particles.isEmpty()) break
            if (elapsedMs > 6500) break
        }
        if (particles.isNotEmpty()) {
            particles.clear()
            frame = System.nanoTime()
        }
    }

    Canvas(
        modifier = modifier.onSizeChanged {
            canvasSize = Size(it.width.toFloat(), it.height.toFloat())
        },
    ) {
        @Suppress("UNUSED_EXPRESSION")
        frame
        particles.forEach { p ->
            val alpha = (p.life / 2.6f).coerceIn(0f, 1f)
            rotate(p.rotation, pivot = Offset(p.x, p.y)) {
                if (p.shape == 0) {
                    drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(p.x - p.size / 2f, p.y - p.size / 4f),
                        size = Size(p.size, p.size / 2f),
                    )
                } else {
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = p.size / 2.8f,
                        center = Offset(p.x, p.y),
                    )
                }
            }
        }
    }
}
