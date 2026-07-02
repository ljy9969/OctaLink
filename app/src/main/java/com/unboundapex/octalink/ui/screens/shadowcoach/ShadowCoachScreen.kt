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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.BuildConfig
import com.unboundapex.octalink.data.shadowcoach.Combo
import com.unboundapex.octalink.data.shadowcoach.ComboLevel
import com.unboundapex.octalink.data.shadowcoach.Combinations
import com.unboundapex.octalink.data.shadowcoach.PoseFrame
import com.unboundapex.octalink.data.shadowcoach.PoseLandmarkerHelper
import com.unboundapex.octalink.data.shadowcoach.PoseLandmarks
import com.unboundapex.octalink.data.shadowcoach.PostureCheck
import com.unboundapex.octalink.data.shadowcoach.ShadowSession
import com.unboundapex.octalink.data.shadowcoach.Technique
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

    // 개발용 인앱 "화면 녹화" (MediaProjection) — 스켈레톤·카운트 칩까지 입혀 갤러리(Movies/ShadowCoach)
    // 에 저장. BuildConfig.DEBUG 일 때만. 서비스/권한은 app/src/debug 에만 있어 release 엔 미포함.
    // 카메라 원본만 찍던 CameraX VideoCapture 로는 오버레이가 안 들어가 화면 캡처 방식으로 전환.
    var isRecording by remember { mutableStateOf(false) }

    fun recordServiceIntent(action: String): android.content.Intent =
        android.content.Intent().apply {
            component = android.content.ComponentName(
                context.packageName,
                "com.unboundapex.octalink.data.shadowcoach.ShadowScreenRecordService",
            )
            this.action = action
        }

    fun startRecordService(resultCode: Int, data: android.content.Intent) {
        val dm = context.resources.displayMetrics
        val intent = recordServiceIntent("com.unboundapex.octalink.SHADOW_REC_START").apply {
            putExtra("result_code", resultCode)
            putExtra("data", data)
            putExtra("width", dm.widthPixels)
            putExtra("height", dm.heightPixels)
            putExtra("dpi", dm.densityDpi)
        }
        runCatching { ContextCompat.startForegroundService(context, intent); isRecording = true }
            .onFailure { android.util.Log.e("ShadowCoach.Rec", "녹화 서비스 시작 실패", it) }
    }

    fun stopRecordService() {
        runCatching {
            ContextCompat.startForegroundService(
                context, recordServiceIntent("com.unboundapex.octalink.SHADOW_REC_STOP"),
            )
        }
        isRecording = false
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            startRecordService(result.resultCode, data)
        }
    }

    fun toggleRecording() {
        if (!BuildConfig.DEBUG) return
        if (isRecording) {
            stopRecordService()
        } else {
            val mpm = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                as android.media.projection.MediaProjectionManager
            runCatching { projectionLauncher.launch(mpm.createScreenCaptureIntent()) }
                .onFailure { android.util.Log.e("ShadowCoach.Rec", "화면 캡처 권한 요청 실패", it) }
        }
    }

    // 화면 떠날 때 녹화 중이면 정지.
    DisposableEffect(Unit) { onDispose { if (isRecording) stopRecordService() } }

    // 화면 자동 꺼짐 방지 — 쉐도우 화면에 있는 동안 항상 켜둠 (운동 중 화면 꺼지면 인식 확인 불가).
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // 자세 코칭 음성(TTS) — liveCues 변할 때 쿨다운 두고 발화.
    val tts = remember { ShadowTts(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    LaunchedEffect(ui.liveCues, ui.running) {
        if (ui.running) tts.announce(ui.liveCues)
    }
    // 격려 음성 — 10회마다 마일스톤. (교정 발화 우선, 자체 쿨다운으로 남발 방지.)
    LaunchedEffect(ui.totalStrikes) {
        val n = ui.totalStrikes
        if (ui.running && n > 0 && n % 10 == 0) tts.encourage()
    }

    // 콤비네이션 추천 — 난이도 버튼 누르면 해당 난이도 콤비 1개 뽑아 TTS 발화 + 화면 표시.
    var recommendedCombo by remember { mutableStateOf<Combo?>(null) }
    fun recommendCombo(level: ComboLevel) {
        val combo = Combinations.random(level, exclude = recommendedCombo)
        recommendedCombo = combo
        tts.callCombo(level.displayName, combo.spoken())
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
            // 프리뷰 + 분석만 바인딩. 녹화는 화면 캡처(MediaProjection) 라 카메라 use case 불필요.
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
        ShadowHud(
            ui = ui,
            onStart = vm::start,
            onStop = vm::stop,
            comboLabel = recommendedCombo?.label(),
            onRecommendCombo = { recommendCombo(it) },
            // 개발용 녹화 — DEBUG 빌드에서만 하단 우측에 노출. release 엔 미노출.
            showRecord = BuildConfig.DEBUG,
            isRecording = isRecording,
            onToggleRecord = { toggleRecording() },
        )
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

/** 상단 카운터/코칭 칩 + 콤비 추천 + 하단 시작/정지 버튼. */
@Composable
private fun ShadowHud(
    ui: ShadowUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    comboLabel: String? = null,
    onRecommendCombo: (ComboLevel) -> Unit = {},
    showRecord: Boolean = false,
    isRecording: Boolean = false,
    onToggleRecord: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 상단 1행 — 동작 종류별 카운트 + 자세 힌트. 색상별 칩, 가로 스크롤로 항상 한 줄.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Technique.mvpEnabled.forEach { tech ->
                ColorChip("${tech.displayName} ${ui.techniqueCounts[tech] ?: 0}", techniqueColor(tech))
            }
            if (!ui.poseDetected) ColorChip("전신이 보이게 서세요", Color(0xE6F59E0B))
        }
        Spacer(Modifier.height(6.dp))
        // 상단 2행 — 총 카운트 + 시간 (어두운 HudChip).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HudChip("총 ${ui.totalStrikes}")
            HudChip(formatElapsed(ui.elapsedMs))
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

        // 콤비네이션 추천 — 추천된 콤비 표시(있으면) + 난이도별 버튼 한 줄.
        comboLabel?.let {
            Text(
                text = "🥊 $it",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ComboLevel.entries.forEach { level ->
                ComboLevelButton(
                    level = level,
                    onClick = { onRecommendCombo(level) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // 하단 한 줄 — 시작/정지 버튼(그라데이션) + (DEBUG) 녹화 버튼 우측.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ui.running) {
                GradientPillButton(
                    text = "세션 종료",
                    brush = Brush.horizontalGradient(listOf(Color(0xFF374151), Color(0xFF111827))),
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                )
            } else {
                GradientPillButton(
                    text = "쉐도우 시작",
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF4F46E5), Color(0xFF9333EA), Color(0xFFDB2777)),
                    ),
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                )
            }
            if (showRecord) {
                Text(
                    text = if (isRecording) "■ 녹화중" else "● 녹화",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isRecording) Color(0xE6C8102E) else Color(0xAA000000))
                        .clickable { onToggleRecord() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** 난이도별 콤비네이션 추천 버튼 — 탭 시 해당 난이도 콤비를 음성으로 불러줌. */
@Composable
private fun ComboLevelButton(
    level: ComboLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = when (level) {
        ComboLevel.BEGINNER -> Color(0xE60F9D58)      // 그린 (초급)
        ComboLevel.INTERMEDIATE -> Color(0xE6F4B400)  // 앰버 (중급)
        ComboLevel.ADVANCED -> Color(0xE6DB4437)      // 레드 (고급)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = level.displayName,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/** 그라데이션 알약 버튼 — 카메라 위 CTA 용. */
@Composable
private fun GradientPillButton(
    text: String,
    brush: androidx.compose.ui.graphics.Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(brush)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** 총 카운트/시간 등 중립 정보 칩 (어두운 배경). */
@Composable
private fun HudChip(text: String, warn: Boolean = false) {
    ColorChip(text = text, bg = if (warn) Color(0xCCC8102E) else Color(0xAA000000))
}

/** 동작 종류·자세 힌트 칩 — 색상 구분 + 컴팩트 사이즈. */
@Composable
private fun ColorChip(text: String, bg: Color) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 동작 종류별 칩 색 — TagChip 팔레트 톤과 통일. 알파 0xE6 으로 카메라 위 가독 확보. */
private fun techniqueColor(tech: Technique): Color = when (tech) {
    Technique.JAB -> Color(0xE61E88E5)        // 블루 (왼손 잽)
    Technique.STRAIGHT -> Color(0xE6C8102E)   // 브랜드 레드 (오른손 라이트)
    Technique.HOOK -> Color(0xE6FF6B35)       // 오렌지 (훅)
    Technique.UPPERCUT -> Color(0xE67C3AED)   // 바이올렛 (어퍼)
    Technique.DUCK -> Color(0xE600ACC1)       // 시안 (더킹)
    Technique.SLIP -> Color(0xE60097A7)       // 틸 (슬립)
    Technique.WEAVE -> Color(0xE626A69A)      // 청록 (위빙)
    Technique.LOW_KICK -> Color(0xE627AE60)   // 그린 (로우킥, 후속)
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
                Text("총 ${session.totalStrikes}회", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    Technique.mvpEnabled.joinToString("  ") {
                        "${it.displayName} ${session.techniqueCounts[it] ?: 0}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("자세 점수 ${session.overallScore()}점 / 100", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("시간 ${formatElapsed(session.durationMs)}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                // 표본이 있는 자세 항목만 노출 (다리 미노출 등으로 표본 없는 항목은 생략).
                PostureCheck.mvpEnabled.forEach { check ->
                    val pct = session.compliancePercent(check) ?: return@forEach
                    Text(
                        "${check.displayName}: $pct% 준수",
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
