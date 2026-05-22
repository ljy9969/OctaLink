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
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
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

    /**
     * URI → InputStream.
     * `file://` 스킴은 `ContentResolver.openInputStream` 가 일부 환경에서 null 을 돌려주는
     * 케이스가 있어 (특히 cacheDir 의 file://) `FileInputStream` 으로 직접 열어 우회.
     * content:// 등 다른 스킴은 기존대로 ContentResolver 경유.
     */
    private fun openSource(context: Context, uri: Uri): InputStream {
        if (uri.scheme == "file") {
            val path = uri.path ?: error("file URI 에 path 없음: $uri")
            val f = File(path)
            if (!f.exists()) error("이미지 파일 없음: $path")
            return FileInputStream(f)
        }
        return context.contentResolver.openInputStream(uri)
            ?: error("이미지 URI 열기 실패 (스트림 null): $uri")
    }

    /** content URI → EXIF 회전 적용 + 리사이즈 + JPEG 압축된 바이트. */
    private fun compressImage(context: Context, uri: Uri): ByteArray {
        // 0. EXIF orientation 읽기 — BitmapFactory.decodeStream 는 EXIF 무시하므로
        //    여기서 직접 읽어 회전 각도 산출. JPEG 출력은 EXIF 첨부 안 되므로 (Bitmap.compress
        //    가 자동 첨부 X) 회전을 픽셀에 baking 해야 뷰어가 올바른 방향으로 표시.
        val exifRotation = runCatching {
            openSource(context, uri).use { stream ->
                val exif = android.media.ExifInterface(stream)
                when (
                    exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrDefault(0f)

        // 1. 사이즈만 먼저 조회 (메모리 안 올림)
        val sizeOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openSource(context, uri).use {
            BitmapFactory.decodeStream(it, null, sizeOpts)
        }

        val srcW = sizeOpts.outWidth
        val srcH = sizeOpts.outHeight
        if (srcW <= 0 || srcH <= 0) error("이미지 디코딩 실패: $uri")

        // 2. inSampleSize 로 메모리 절약하며 거의 MAX_DIM 크기로 디코드
        val longSide = maxOf(srcW, srcH)
        val sample = (longSide / MAX_DIM).coerceAtLeast(1)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val sampled = openSource(context, uri).use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: error("이미지 본 디코딩 실패: $uri")

        // 3. 정확히 MAX_DIM 으로 리사이즈 (sample 만으로는 약간 더 클 수 있음)
        val sampledLong = maxOf(sampled.width, sampled.height)
        val resized = if (sampledLong <= MAX_DIM) sampled else {
            val scale = MAX_DIM.toFloat() / sampledLong
            val w = (sampled.width * scale).toInt()
            val h = (sampled.height * scale).toInt()
            Bitmap.createScaledBitmap(sampled, w, h, true).also {
                if (it !== sampled) sampled.recycle()
            }
        }

        // 4. EXIF 회전 적용 (0° 면 no-op).
        val finalBitmap = if (exifRotation == 0f) resized else {
            val matrix = android.graphics.Matrix().apply { postRotate(exifRotation) }
            android.util.Log.i(
                "OctaLink.ImageUploader",
                "EXIF rotation ${exifRotation.toInt()}° applied to pixels",
            )
            Bitmap.createBitmap(resized, 0, 0, resized.width, resized.height, matrix, true).also {
                if (it !== resized) resized.recycle()
            }
        }

        // 5. JPEG 압축
        val out = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        finalBitmap.recycle()
        return out.toByteArray()
    }
}
