package com.unboundapex.octalink.ui.screens.splash

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.unboundapex.octalink.R
import com.unboundapex.octalink.ui.theme.Ink

/**
 * 앱 LOADING phase 동안 표시되는 스플래시. 다크 ink 배경 + 흰색으로 tint 된 OctaLink 마크 +
 * 심장박동 펄스 애니메이션.
 *
 * 시스템 splash (themes.xml `Theme.OctaLink.Starting`) 는 짧게 노출된 후 활동 onCreate
 * 완료 시 사라지고, Compose [PosseApp] 의 LOADING phase 가 이 Composable 로 이어받음.
 * (시스템 splash 의 아이콘 색상은 mipmap 자산 색에 묶여 있어 별도 흰색 변형이 필요해 추후 정리.)
 *
 * 펄스 패턴 — 실제 심장박동(lub-dub) 모사:
 *   0%   scale 1.00 (rest)
 *   12%  scale 1.10 (lub — 강한 첫 박동)
 *   24%  scale 1.00
 *   32%  scale 1.06 (dub — 짧고 약한 두 번째 박동)
 *   40%  scale 1.00
 *   100% scale 1.00 (긴 휴식)
 * 총 주기 ~1200ms.
 */
@Composable
fun SplashScreen() {
    val transition = rememberInfiniteTransition(label = "heartbeat")
    // initialValue != targetValue 강제 — 같으면 Compose 가 애니메이션 skip 가능. peak 값을 target.
    val scale by transition.animateFloat(
        initialValue = 1.00f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                1.00f at 0 using LinearOutSlowInEasing
                1.10f at 145          // lub — 강한 첫 박동
                1.00f at 290
                1.06f at 385          // dub — 짧고 약한 두 번째 박동
                1.00f at 480
                1.00f at 1200         // 긴 휴식
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "heartbeat-scale",
    )

    // 스플래시는 테마 무관 항상 다크 — 시스템 splash(themes.xml: Theme.OctaLink.Starting) 가
    // 항상 ink 배경이라, Compose splash 도 다크로 통일해 시스템→Compose 핸드오프 시 색 점멸 회피.
    // 메인 컨텐츠 진입 시점에 사용자의 AppTheme 가 적용되므로 라이트 사용자도 자연스러운 페이드 인.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.mark_octalink),
            contentDescription = "OctaLink",
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier
                .size(160.dp)
                .scale(scale),
        )
    }
}
