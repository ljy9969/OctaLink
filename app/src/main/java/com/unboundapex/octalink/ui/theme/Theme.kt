package com.unboundapex.octalink.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** OctaLink 다크 — 도장 무드의 거의 검정 배경 + 빨강 액센트. 기본값. */
private val PosseDarkScheme = darkColorScheme(
    primary = Blood,
    onPrimary = Bone,
    secondary = Bone,
    onSecondary = Ink,
    background = Ink,
    onBackground = Bone,
    surface = Canvas,
    onSurface = Bone,
    surfaceVariant = Ash,
    onSurfaceVariant = Mist,
    outline = Ash,
)

/**
 * OctaLink 라이트 — Notion / Linear 풍 깔끔 화이트. 카드 = 순백, 배경 = 살짝 off-white 로 글래어 완화.
 * 브랜드 빨강(Blood) 은 양 테마 공통 — primary 액센트 / 우승 강조 / 칩 등에서 일관 사용.
 */
private val PosseLightScheme = lightColorScheme(
    primary = Blood,
    onPrimary = Paper,
    secondary = Slate,
    onSecondary = Paper,
    background = Cloud,
    onBackground = Slate,
    surface = Paper,
    onSurface = Slate,
    surfaceVariant = Frost,
    onSurfaceVariant = Pebble,
    outline = OutlineLight,
)

/**
 * 회원이 ProfileScreen 에서 선택한 [AppTheme] 에 따라 colorScheme 분기.
 * MainActivity 가 [AppThemeViewModel] 에서 collectAsState 후 전달.
 */
@Composable
fun OctaLinkTheme(
    theme: AppTheme = AppTheme.DARK,
    content: @Composable () -> Unit,
) {
    val colors = when (theme) {
        AppTheme.DARK -> PosseDarkScheme
        AppTheme.LIGHT -> PosseLightScheme
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
