package com.unboundapex.octalink.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * 이미지 업로드 — content URI → 압축 → Firebase Storage `posts/images/{uuid}.jpg` → download URL.
 *
 * 압축 정책:
 *  - 긴 변 [MAX_DIM] 픽셀로 리사이즈 (in-sample 으로 메모리 절약)
 *  - JPEG 85% 품질
 *  - 결과 사이즈 [MAX_BYTES] 초과 시 IllegalArgumentException
 *
 * Storage 룰도 동일 사이즈/타입 검증을 강제 — 클라이언트 변조 차단.
 */
object ImageUploader {
    /** 긴 변 최대 픽셀. ~1080p 디스플레이에 충분. */
    const val MAX_DIM = 1920

    /** 압축 후 최대 바이트. 5MB. Storage 룰과 일치. */
    const val MAX_BYTES = 5 * 1024 * 1024

    private const val JPEG_QUALITY = 85
    private const val STORAGE_PATH = "posts/images"

    /**
     * @return Firebase Storage download URL (HTTPS).
     * @throws IllegalArgumentException 이미지 디코딩 실패 또는 압축 후 [MAX_BYTES] 초과
     */
    suspend fun uploadPostImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = compressImage(context, uri)
        if (bytes.size > MAX_BYTES) {
            error("압축 후에도 이미지 사이즈 초과: ${bytes.size / 1024}KB > ${MAX_BYTES / 1024}KB")
        }
        val ref = Firebase.storage.reference
            .child("$STORAGE_PATH/${UUID.randomUUID()}.jpg")
        ref.putBytes(bytes).await()
        ref.downloadUrl.await().toString()
    }

    /** content URI → 리사이즈 + JPEG 압축된 바이트. */
    private fun compressImage(context: Context, uri: Uri): ByteArray {
        // 1. 사이즈만 먼저 조회 (메모리 안 올림)
        val sizeOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, sizeOpts)
        } ?: error("이미지 URI 열기 실패: $uri")

        val srcW = sizeOpts.outWidth
        val srcH = sizeOpts.outHeight
        if (srcW <= 0 || srcH <= 0) error("이미지 디코딩 실패: $uri")

        // 2. inSampleSize 로 메모리 절약하며 거의 MAX_DIM 크기로 디코드
        val longSide = maxOf(srcW, srcH)
        val sample = (longSide / MAX_DIM).coerceAtLeast(1)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val sampled = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: error("이미지 본 디코딩 실패: $uri")

        // 3. 정확히 MAX_DIM 으로 리사이즈 (sample 만으로는 약간 더 클 수 있음)
        val sampledLong = maxOf(sampled.width, sampled.height)
        val finalBitmap = if (sampledLong <= MAX_DIM) sampled else {
            val scale = MAX_DIM.toFloat() / sampledLong
            val w = (sampled.width * scale).toInt()
            val h = (sampled.height * scale).toInt()
            Bitmap.createScaledBitmap(sampled, w, h, true).also {
                if (it !== sampled) sampled.recycle()
            }
        }

        // 4. JPEG 압축
        val out = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        finalBitmap.recycle()
        return out.toByteArray()
    }
}
