package com.teamposse.striking.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.teamposse.striking.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * 공공데이터 특일정보 API(getRestDeInfo) 기반 한국 공휴일 조회.
 * - 메모리 캐시 + SharedPreferences 영속 캐시
 * - API 실패 시 하드코딩 폴백(2026년) 사용
 * - MainActivity.onCreate에서 [init] 호출
 */
object HolidayRepository {
    private const val TAG = "HolidayRepository"
    private const val PREFS_NAME = "holiday_cache"
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val cache = ConcurrentHashMap<Int, Set<LocalDate>>()
    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val currentYear = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).year
        // 1) 영속 캐시 → 메모리 캐시
        for (year in (currentYear - 1)..(currentYear + 1)) {
            loadFromPrefs(year)?.let { cache[year] = it }
        }
        // 2) 폴백 등록 (API 못 닿을 때 대비)
        for ((year, set) in fallbackHolidays) {
            cache.putIfAbsent(year, set)
        }
        // 3) 백그라운드에서 API 갱신
        scope.launch { refresh(currentYear) }
        scope.launch { refresh(currentYear + 1) }
    }

    fun isHoliday(date: LocalDate): Boolean {
        return cache[date.year]?.contains(date) == true
    }

    private suspend fun refresh(year: Int) = withContext(Dispatchers.IO) {
        val fetched = fetchHolidaysFromApi(year)
        if (fetched.isNotEmpty()) {
            cache[year] = fetched
            saveToPrefs(year, fetched)
            Log.d(TAG, "Holidays for $year refreshed: ${fetched.size} entries")
        } else {
            Log.w(TAG, "Holiday API returned empty for $year, keeping existing cache/fallback")
        }
    }

    private fun fetchHolidaysFromApi(year: Int): Set<LocalDate> {
        val key = BuildConfig.HOLIDAY_API_KEY
        if (key.isBlank()) {
            Log.w(TAG, "HOLIDAY_API_KEY is empty, skipping API call")
            return emptySet()
        }
        return try {
            val urlStr = "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo" +
                "?serviceKey=$key&solYear=$year&numOfRows=50&_type=json"
            val response = URL(urlStr).readText()
            // JSON: {"response":{"body":{"items":{"item":[{"locdate":20260101,...}, ...]}}}}
            // XML도 동일하게 <locdate>YYYYMMDD</locdate> 패턴이라 안전한 정규식으로 추출.
            val regex = """"?locdate"?\s*[:>]\s*"?(\d{8})"?""".toRegex()
            regex.findAll(response)
                .mapNotNull { runCatching { LocalDate.parse(it.groupValues[1], DATE_FORMAT) }.getOrNull() }
                .toSet()
        } catch (e: Exception) {
            Log.e(TAG, "fetchHolidaysFromApi($year) failed", e)
            emptySet()
        }
    }

    private fun loadFromPrefs(year: Int): Set<LocalDate>? {
        val raw = prefs?.getString(keyFor(year), null) ?: return null
        return raw.split(',')
            .mapNotNull { runCatching { LocalDate.parse(it, DATE_FORMAT) }.getOrNull() }
            .toSet()
            .takeIf { it.isNotEmpty() }
    }

    private fun saveToPrefs(year: Int, dates: Set<LocalDate>) {
        val raw = dates.joinToString(",") { it.format(DATE_FORMAT) }
        prefs?.edit()?.putString(keyFor(year), raw)?.apply()
    }

    private fun keyFor(year: Int) = "holidays_$year"
}

// API 실패/네트워크 없을 때 표시될 최소 폴백
private val fallbackHolidays: Map<Int, Set<LocalDate>> = mapOf(
    2026 to setOf(
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 2, 16),
        LocalDate.of(2026, 2, 17),
        LocalDate.of(2026, 2, 18),
        LocalDate.of(2026, 3, 1),
        LocalDate.of(2026, 5, 5),
        LocalDate.of(2026, 5, 24),
        LocalDate.of(2026, 5, 25),
        LocalDate.of(2026, 6, 6),
        LocalDate.of(2026, 8, 15),
        LocalDate.of(2026, 8, 17),
        LocalDate.of(2026, 9, 24),
        LocalDate.of(2026, 9, 25),
        LocalDate.of(2026, 9, 26),
        LocalDate.of(2026, 10, 3),
        LocalDate.of(2026, 10, 5),
        LocalDate.of(2026, 10, 9),
        LocalDate.of(2026, 12, 25),
    )
)
