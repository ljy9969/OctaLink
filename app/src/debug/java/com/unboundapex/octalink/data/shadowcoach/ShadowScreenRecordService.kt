package com.unboundapex.octalink.data.shadowcoach

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log

/**
 * 개발(debug) 전용 화면 녹화 포그라운드 서비스.
 *
 * MediaProjection 으로 **화면 전체**(카메라 프리뷰 + 스켈레톤 오버레이 + 카운트 칩)를 캡처해
 * H.264/MP4 로 인코딩, 갤러리 공유 폴더(`Movies/ShadowCoach`)에 저장한다. 자세 인식이
 * 어떤 동작을 잡고/놓쳤는지 영상으로 바로 검토할 수 있어 임계값 튜닝에 쓴다.
 *
 * 이 클래스와 관련 권한은 `app/src/debug` 에만 존재 → release(Play Store) 빌드엔 미포함.
 *
 * 시작 시퀀스(Android 14+ 요구): startForeground(type=mediaProjection) → getMediaProjection →
 * registerCallback → MediaRecorder(Surface) + VirtualDisplay → start.
 */
class ShadowScreenRecordService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var outputUri: Uri? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopRecording()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> { stopRecording(); stopSelf() }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        startForegroundNotif()
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
        if (data == null) { stopSelf(); return }
        val width = intent.getIntExtra(EXTRA_WIDTH, 720)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 1280)
        val dpi = intent.getIntExtra(EXTRA_DPI, 320)

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = mpm.getMediaProjection(resultCode, data)
        if (proj == null) { stopSelf(); return }
        projection = proj
        proj.registerCallback(projectionCallback, null)

        try {
            setupRecorder(width, height)
            val rec = recorder ?: error("recorder 미생성")
            virtualDisplay = proj.createVirtualDisplay(
                "ShadowCoachRec", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                rec.surface, null, null,
            )
            rec.start()
            Log.i(TAG, "녹화 시작 ${width}x$height → $outputUri")
        } catch (e: Exception) {
            Log.e(TAG, "녹화 시작 실패", e)
            stopRecording()
            stopSelf()
        }
    }

    private fun setupRecorder(width: Int, height: Int) {
        // 파일명에 날짜·시간 표기 (yyMMdd_HHmm) — 갤러리에서 어떤 회차인지 바로 구분.
        val stamp = java.text.SimpleDateFormat("yyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
        val name = "shadow_$stamp.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ShadowCoach")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert 실패")
        outputUri = uri
        val descriptor = contentResolver.openFileDescriptor(uri, "rw") ?: error("FD 열기 실패")
        pfd = descriptor

        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        rec.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(10_000_000)
            setOutputFile(descriptor.fileDescriptor)
            prepare()
        }
        recorder = rec
    }

    private fun stopRecording() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        projection = null
        runCatching { pfd?.close() }
        pfd = null
        // 갤러리에 보이도록 pending 해제.
        outputUri?.let { uri ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val v = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                runCatching { contentResolver.update(uri, v, null, null) }
            }
        }
        Log.i(TAG, "녹화 종료 → $outputUri")
        outputUri = null
        stopForegroundCompat()
    }

    private fun startForegroundNotif() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "쉐도우 녹화", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
        val notif: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("쉐도우 코치 녹화 중")
            .setContentText("화면(스켈레톤 포함)을 녹화하고 있어요")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ShadowCoach.ScreenRec"
        private const val NOTIF_ID = 4071
        private const val CHANNEL_ID = "shadow_screen_rec"

        const val ACTION_START = "com.unboundapex.octalink.SHADOW_REC_START"
        const val ACTION_STOP = "com.unboundapex.octalink.SHADOW_REC_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_DPI = "dpi"
    }
}
