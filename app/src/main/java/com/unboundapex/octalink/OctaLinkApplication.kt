package com.unboundapex.octalink

import android.app.Application
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.util.Utility
import com.unboundapex.octalink.data.HolidayRepository
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.messaging.ClassReminderScheduler
import com.unboundapex.octalink.messaging.NotificationChannels

/**
 * 앱 프로세스 진입점. 초기화는 [onCreate] 에서 1회만 수행.
 *
 * - 카카오 SDK: 네이티브 앱 키 주입. 이후 어디서든 [com.kakao.sdk.user.UserApiClient] 사용 가능
 * - HolidayRepository: 공휴일 API 캐시 컨텍스트
 * - RepositoryProvider: Auth / Member 등 데이터 레이어 싱글톤 컨테이너
 *
 * MainActivity 의 onCreate 보다 먼저 실행되므로 Compose 가 RepositoryProvider 를 읽을 때
 * 초기화 보장됨. 이전 MainActivity 내 init 호출은 제거됨.
 *
 * **startup 시 KeyHash 출력** — 카카오 콘솔에 등록해야 하는 SHA-1 base64 키해시를 logcat 에
 * 항상 찍어둠. debug/release 빌드별로 다른 값이 나오니 각각 등록 필요.
 */
class OctaLinkApplication : Application(), ImageLoaderFactory {
    /**
     * Coil 의 기본 ImageLoader 에 GIF 디코더 등록.
     * - Android P 이상: 네이티브 [ImageDecoderDecoder] (애니메이션 PNG/WebP 도 함께 지원)
     * - 이하: GIF 전용 [GifDecoder]
     * AI 보강 루틴의 ExerciseDB GIF (`https://static.exercisedb.dev/media/{id}.gif`) 재생용.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
        }
        .build()

    override fun onCreate() {
        super.onCreate()
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        Log.d("OctaLink.KeyHash", "Kakao KeyHash to register: ${Utility.getKeyHash(this)}")
        HolidayRepository.init(applicationContext)
        RepositoryProvider.init(applicationContext)
        // FCM 알림 채널 등록 — 알림 표시 자체엔 필수. 권한(POST_NOTIFICATIONS) 은 별도.
        NotificationChannels.ensureRegistered(applicationContext)
        // 매일 04:00 KST 에 그날 CLASS_REMINDER 재스케줄 — KEEP 정책이라 멱등.
        ClassReminderScheduler.scheduleDailyRollover(applicationContext)
    }
}
