package com.unboundapex.octalink

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.unboundapex.octalink.data.HolidayRepository
import com.unboundapex.octalink.data.repo.RepositoryProvider

/**
 * 앱 프로세스 진입점. 초기화는 [onCreate] 에서 1회만 수행.
 *
 * - 카카오 SDK: 네이티브 앱 키 주입. 이후 어디서든 [com.kakao.sdk.user.UserApiClient] 사용 가능
 * - HolidayRepository: 공휴일 API 캐시 컨텍스트
 * - RepositoryProvider: Auth / Member 등 데이터 레이어 싱글톤 컨테이너
 *
 * MainActivity 의 onCreate 보다 먼저 실행되므로 Compose 가 RepositoryProvider 를 읽을 때
 * 초기화 보장됨. 이전 MainActivity 내 init 호출은 제거됨.
 */
class OctaLinkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        HolidayRepository.init(applicationContext)
        RepositoryProvider.init(applicationContext)
    }
}
