package com.unboundapex.octalink.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.unboundapex.octalink.BuildConfig

/**
 * AdMob 배너 (320×50 표준 사이즈) Compose 래퍼.
 *
 *  - [BuildConfig.SHOW_ADS] 가 false 면 아무것도 렌더 안 함 — 베타 50명 미만 차단용 토글.
 *  - 광고 ID 는 [BuildConfig.BANNER_AD_UNIT_ID]. 현재 Google 공식 테스트 ID 사용 (실제 광고
 *    노출 없음, 정책 위반 없음). 실제 배포 시 admob.google.com 에서 발급한 ID 로 swap.
 *  - 호스트 화면이 LazyColumn item 일 때도 안전하게 동작 (AndroidView 가 재구성 시 재사용).
 *
 * 적용 위치 (UX 저해 최소화 — README 참고):
 *  - Home / Curriculum / Info 하단
 *  - 회피: 출석 체크인, 프로필, 설정, 글 작성, 알림 다이얼로그, 미디어 전체화면, 토너먼트, 로그인
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (!BuildConfig.SHOW_ADS) return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                adUnitId = BuildConfig.BANNER_AD_UNIT_ID
                setAdSize(AdSize.BANNER) // 320×50 dp
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
