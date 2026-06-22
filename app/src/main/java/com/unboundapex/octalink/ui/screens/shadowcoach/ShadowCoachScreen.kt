package com.unboundapex.octalink.ui.screens.shadowcoach

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.data.shadowcoach.PoseFrame
import com.unboundapex.octalink.data.shadowcoach.PoseLandmarkerHelper
import com.unboundapex.octalink.data.shadowcoach.PoseLandmarks
import com.unboundapex.octalink.data.shadowcoach.PostureCheck
import com.unboundapex.octalink.data.shadowcoach.ShadowSession
import java.util.concurrent.Executors

/**
 * AI 쉐도우 코치 — 카메라 기반 실시간 자세 분석 화면.
 *
 * 흐름: 카메라 권한 → CameraX 프리뷰 + ImageAnalysis(RGBA) → [PoseLandmarkerHelper] 온디바이스
 * 포즈 추정 → [ShadowCoachViewModel] 누적 → 스켈레톤 오버레이 + 잽 카운트 + 가드/턱 실시간 코칭.
 *
 * 영상은 단말 밖으로 전송되지 않음 (관절 좌표만 로컬 분석).
 * **실기기 검증 필요** — 에뮬레이터 카메라는 가짜 영상이라 포즈 인식이 안 됨.
 */
@Composable
fun ShadowCoachScreen(
    onBack: () -> Unit = {},
    vm: ShadowCoachViewModel = viewModel(),
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            ShadowCameraExperience(vm = vm)
        } else {
            CameraPermissionGate(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            )
        }
    }
}

@Composable
private fun CameraPermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "🥊 AI 쉐도우 코치",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "실시간 자세 분석을 위해 카메라 권한이 필요해요.\n" +
                "영상은 저장·전송되지 않고 단말에서만 분석됩니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("카메라 권한 허용") }
    }
}

@Composable
private fun ShadowCameraExperience(vm: ShadowCoachViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ui by vm.ui.collectAsState()
    val poseFrame by vm.poseFrame.collectAsState()

    // MVP: 전면 카메라(자기 모습 확인). 전면은 미리보기가 좌우반전이라 오버레이 x 미러.
    val lensFacing = CameraSelector.LENS_FACING_FRONT
    val mirrorX = lensFacing == CameraSelector.LENS_FACING_FRONT

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val helper = remember {
        PoseLandmarkerHelper(
            context = context.applicationContext,
            onResult = { frame -> vm.onPoseFrame(frame) },
        )
    }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(Unit) {
        helper.setup()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor) { proxy -> helper.analyze(proxy) } }
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            helper.close()
            analysisExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        PoseOverlay(frame = poseFrame, mirrorX = mirrorX, modifier = Modifier.fillMaxSize())
        ShadowHud(ui = ui, onStart = vm::start, onStop = vm::stop)
    }

    ui.summary?.let { session ->
        ShadowSummaryDialog(
            session = session,
            onRestart = { vm.start() },
            onDismiss = { vm.dismissSummary() },
        )
    }
}

/** 프리뷰 위에 33관절 스켈레톤 그리기. normalized 좌표 → 캔버스 매핑, 전면 카메라면 x 미러. */
@Composable
private fun PoseOverlay(frame: PoseFrame?, mirrorX: Boolean, modifier: Modifier = Modifier) {
    val points = frame?.points ?: return
    if (points.isEmpty()) return
    val lineColor = Color(0xFF00E5FF)
    val jointColor = Color(0xFFFFD54F)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun px(i: Int): Offset? {
            val p = points.getOrNull(i) ?: return null
            if (p.visibility < PoseFrame.MIN_VISIBILITY) return null
            val x = if (mirrorX) (1f - p.x) else p.x
            return Offset(x * w, p.y * h)
        }
        // 연결선.
        PoseLandmarks.CONNECTIONS.forEach { (a, b) ->
            val pa = px(a); val pb = px(b)
            if (pa != null && pb != null) {
                drawLine(color = lineColor, start = pa, end = pb, strokeWidth = 6f, cap = Stroke.DefaultCap)
            }
        }
        // 관절점.
        for (i in points.indices) {
            px(i)?.let { drawCircle(color = jointColor, radius = 7f, center = it) }
        }
    }
}

/** 상단 카운터/코칭 칩 + 하단 시작/정지 버튼. */
@Composable
private fun ShadowHud(
    ui: ShadowUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 상단 — 카운터 + 시간.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HudChip("잽 ${ui.jabCount}")
            HudChip(formatElapsed(ui.elapsedMs))
            if (!ui.poseDetected) HudChip("전신이 보이게 서세요", warn = true)
        }
        Spacer(Modifier.height(8.dp))
        // 실시간 코칭 칩.
        if (ui.running) {
            ui.liveCues.forEach { cue ->
                HudChip(cue.cue, warn = true)
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        // 하단 — 시작/정지.
        if (ui.running) {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("세션 종료") }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("쉐도우 시작") }
        }
    }
}

@Composable
private fun HudChip(text: String, warn: Boolean = false) {
    val bg = if (warn) Color(0xCCC8102E) else Color(0xAA000000)
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun ShadowSummaryDialog(
    session: ShadowSession,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("세션 요약", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("잽 ${session.totalStrikes}회", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("자세 점수 ${session.overallScore()}점 / 100", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("시간 ${formatElapsed(session.durationMs)}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                PostureCheck.mvpEnabled.forEach { check ->
                    val pct = session.compliancePercent(check)
                    Text(
                        "${check.displayName}: " + (pct?.let { "$it% 준수" } ?: "표본 없음"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onRestart) { Text("다시 하기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
