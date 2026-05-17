package com.unboundapex.octalink.data.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 영상 업로드 — content URI → 길이/사이즈 검증 → Firebase Storage `posts/videos/{uuid}.mp4` → download URL.
 *
 * 정책:
 *  - 최대 길이 [MAX_DURATION_MS] (1분 30초) — 짧은 도장 클립 (스파링 한 라운드 / 드릴 시연 등).
 *  - 최대 파일 사이즈 [MAX_BYTES] (100MB). 90초 / 100MB ≈ 1.1MB/s — 1080p30 phone-native 영상
 *    (iPhone HEVC ~3Mbps, H.264 ~5Mbps) 가 무압축으로 통과. Storage 룰도 동일 강제.
 *
 * 업로드 전 [VideoTranscoder] 가 720p H.264 로 재인코딩 시도 — 통상 50-70% 사이즈 감축.
 * 트랜스코딩 실패(코덱 미지원/디스크 부족 등) 시 원본으로 fallback, 단 최종 사이즈/길이 검증은 동일.
 *
 * 검증 실패 시 [IllegalArgumentException] — caller(ViewModel) 가 메시지 그대로 사용자에 노출.
 */
object VideoUploader {
    /** 최대 재생 시간(ms) — 1분 30초. 스파링 한 라운드 / 드릴 시연 한 세트 분량. */
    const val MAX_DURATION_MS = 90L * 1000L

    /** 최대 파일 바이트 — 100MB. [MAX_DURATION_MS] 와 비례 (1.1MB/s 예산). Storage 룰과 일치. */
    const val MAX_BYTES = 100L * 1024L * 1024L

    private const val STORAGE_PATH = "posts/videos"

    /**
     * @return Firebase Storage download URL (HTTPS).
     * @throws IllegalArgumentException 길이 초과(트랜스코딩 *전*) 또는 트랜스코딩 후에도 사이즈 초과 / metadata 추출 실패.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun uploadPostVideo(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        // 1. 길이 검증 먼저 — 트랜스코딩 비용 들이기 전 cap 위반은 즉시 거부.
        val durationMs = extractDurationMs(context, uri)
            ?: error("영상 길이를 확인할 수 없습니다. 손상되었거나 지원되지 않는 형식일 수 있습니다.")
        if (durationMs > MAX_DURATION_MS) {
            error("영상이 너무 깁니다 (${formatDuration(durationMs)}). 최대 ${formatDuration(MAX_DURATION_MS)}까지 업로드 가능합니다.")
        }

        // 2. 720p 재인코딩 시도 — 실패해도 원본으로 fallback (caller 에는 transparent).
        val uploadUri = runCatching { VideoTranscoder.transcodeTo720p(context, uri) }
            .onFailure { android.util.Log.w("OctaLink.VideoUploader", "transcode 실패 — 원본 사용", it) }
            .getOrDefault(uri)

        // 3. 최종 사이즈 검증 — 트랜스코딩 후에도 cap 초과면 거부 (사용자에게 화질/길이 줄여 재시도 안내).
        val sizeBytes = resolveFileSize(context, uploadUri)
        if (sizeBytes <= 0L) {
            error("영상 파일 크기를 확인할 수 없습니다.")
        }
        if (sizeBytes > MAX_BYTES) {
            val mb = sizeBytes / 1024 / 1024
            val maxMb = MAX_BYTES / 1024 / 1024
            error("영상 파일이 너무 큽니다 (${mb}MB). 최대 ${maxMb}MB까지 업로드 가능합니다.")
        }

        // 4. Storage 업로드.
        val ref = Firebase.storage.reference
            .child("$STORAGE_PATH/${UUID.randomUUID()}.mp4")
        ref.putFile(uploadUri).await()
        ref.downloadUrl.await().toString()
    }

    /** 길이만 빠르게 추출 — 트랜스코딩 전 fail-fast 용. */
    private fun extractDurationMs(context: Context, uri: Uri): Long? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            android.util.Log.e("OctaLink.VideoUploader", "duration metadata 추출 실패", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** 60초 이하면 "Xs", 그 이상이면 "Xm Ys" (Y=0 이면 "Xm" 만). */
    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        if (totalSec < 60) return "${totalSec}초"
        val m = totalSec / 60
        val s = totalSec % 60
        return if (s == 0L) "${m}분" else "${m}분 ${s}초"
    }

    /**
     * content:// URI 는 `openAssetFileDescriptor` 의 length 가 권위 있음. file:// 는 java.io.File.length().
     * 둘 다 실패하면 -1 — caller 가 검증 실패로 처리.
     */
    private fun resolveFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            val path = uri.path ?: return -1L
            val f = java.io.File(path)
            return if (f.exists()) f.length() else -1L
        }
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
    }
}
