package com.unboundapex.octalink.ui.map

import android.content.Context
import android.os.Build
import android.util.Log
import com.kakao.vectormap.KakaoMapSdk
import com.unboundapex.octalink.BuildConfig

/**
 * 카카오맵 SDK v2 초기화·사용 가능 여부. [init] 은 Application.onCreate 에서 1회.
 *
 * 지도를 못 띄우는 경우(키 미설정 · x86 에뮬레이터처럼 SDK 가 없는 ABI · 초기화 실패)에도 앱은 그대로 돌고,
 * 화면은 [unavailableReason] 문구로 지도 자리만 대신한다.
 */
object KakaoMapSupport {
    /** SDK 네이티브 라이브러리는 arm64-v8a · armeabi-v7a 만 제공. */
    private val abiSupported: Boolean =
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "armeabi-v7a" }

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (BuildConfig.KAKAO_MAP_APP_KEY.isBlank() || !abiSupported) return
        runCatching { KakaoMapSdk.init(context, BuildConfig.KAKAO_MAP_APP_KEY) }
            .onSuccess { initialized = true }
            .onFailure { Log.w("OctaLink.KakaoMap", "KakaoMapSdk.init failed", it) }
    }

    /** 지도를 띄울 수 없는 이유(사용자 문구). 띄울 수 있으면 null. */
    fun unavailableReason(): String? = when {
        BuildConfig.KAKAO_MAP_APP_KEY.isBlank() -> "지도 키가 아직 설정되지 않았어요."
        !abiSupported -> "이 기기에서는 지도를 표시할 수 없어요."
        !initialized -> "지도를 준비하지 못했어요."
        else -> null
    }
}
