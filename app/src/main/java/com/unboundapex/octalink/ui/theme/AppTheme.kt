package com.unboundapex.octalink.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/** 신규 설치 / SharedPreferences 미존재 / 파싱 실패 시 폴백. 라이트가 기본 — 일반 사용자 친숙도 ↑. */
private val DEFAULT_THEME = AppTheme.LIGHT

private fun loadAppTheme(context: Context): AppTheme {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val name = prefs.getString(KEY_THEME, DEFAULT_THEME.name) ?: DEFAULT_THEME.name
    return runCatching { AppTheme.valueOf(name) }.getOrDefault(DEFAULT_THEME)
}

private fun saveAppTheme(context: Context, theme: AppTheme) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_THEME, theme.name)
        .apply()
}

/**
 * 앱 전역 테마 상태 — 싱글톤 [StateFlow]. ViewModel 로 만들지 않는 이유:
 *
 * Compose 의 `viewModel()` 은 가장 가까운 `LocalViewModelStoreOwner` 를 잡는데,
 * MainActivity.setContent 는 Activity scope, NavHost composable 내부의 ProfileScreen 은
 * 자기 NavBackStackEntry scope 라 *서로 다른 store* 인스턴스를 갖는다. 한쪽에서 setter 호출해도
 * 다른 쪽의 collectAsState 가 갱신되지 않음 → 테마 즉시 적용 안 되는 버그.
 *
 * 테마는 화면 라이프사이클과 무관한 앱 전역 상태이므로 object 싱글톤이 적합.
 * [init] 은 [MainActivity.onCreate] 에서 1회 호출 (멱등).
 */
object AppThemeStore {
    private var appContext: Context? = null
    // init() 호출 전 잠깐 노출되는 임시 기본값 — DEFAULT_THEME 과 일치시켜 첫 프레임 깜빡임 회피.
    private val _theme = MutableStateFlow(DEFAULT_THEME)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    /** Application 또는 MainActivity 시작 시 1회 호출. 멱등 — 두 번째 호출은 no-op. */
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        _theme.value = loadAppTheme(app)
    }

    fun set(theme: AppTheme) {
        if (_theme.value == theme) return
        _theme.value = theme
        appContext?.let { saveAppTheme(it, theme) }
    }
}
