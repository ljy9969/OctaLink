package com.unboundapex.octalink.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 회원 본인이 ProfileScreen 에서 선택하는 앱 UI 테마.
 *
 * - [DARK]: 기존 OctaLink 다크 테마 (Ink/Canvas/Bone/Blood). 도장 무드.
 * - [LIGHT]: 깔끔 미니멀 라이트 (Paper/Cloud/Slate/Blood). Notion 풍 화이트.
 *
 * 기기 단위 설정 — Firestore 동기화 ❌. 사용자가 디바이스 별로 별개 선호 가질 수 있고, 알림 채널 등
 * 다른 device-local 설정과 일관성. [SharedPreferences] 1키로 영속.
 */
enum class AppTheme(val displayName: String) {
    DARK("다크"),
    LIGHT("라이트"),
}

/** SharedPreferences 단일 키 — 앱 시작 시 1회 read, 변경 시 즉시 write. */
private const val PREFS_NAME = "octalink_ui_theme"
private const val KEY_THEME = "selected_theme"

internal fun loadAppTheme(context: Context): AppTheme {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val name = prefs.getString(KEY_THEME, AppTheme.DARK.name) ?: AppTheme.DARK.name
    return runCatching { AppTheme.valueOf(name) }.getOrDefault(AppTheme.DARK)
}

internal fun saveAppTheme(context: Context, theme: AppTheme) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_THEME, theme.name)
        .apply()
}

/**
 * 테마 선택 상태 + 영속화. [MainActivity] 에서 1회 hoist → `OctaLinkTheme` 에 전달 → ProfileScreen 에서 토글.
 * Application context 보유라 Configuration 변경 후에도 동일 인스턴스.
 */
class AppThemeViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val _theme = MutableStateFlow(loadAppTheme(application))
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun set(theme: AppTheme) {
        if (_theme.value == theme) return
        viewModelScope.launch {
            saveAppTheme(getApplication(), theme)
        }
        _theme.value = theme
    }
}
