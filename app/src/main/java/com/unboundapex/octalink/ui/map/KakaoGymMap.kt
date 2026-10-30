package com.unboundapex.octalink.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.shape.DotPoints
import com.kakao.vectormap.shape.PolygonOptions
import com.kakao.vectormap.shape.PolygonStyles
import kotlin.math.cos
import kotlin.math.roundToInt

/** 지도 위 점 종류 — 색은 한 가지 뜻: 빨강 우리 체육관 · 파랑 신청 가능(가입) · 회색 아직 OctaLink 전. */
enum class PinKind { MY_GYM, MEMBER_GYM, OTHER_GYM }

/** 지도에 그릴 점 하나. [key] 는 클릭 시 그대로 돌려준다. */
data class MapPin(val key: String, val lat: Double, val lng: Double, val kind: PinKind)

object GymMapColors {
    val MemberBlue = Color(0xFF3987E5)
    val OtherGray = Color(0xFF7D818C)
    /** 밝은 지도 바탕 위 회색 점 — 목록 점보다 한 단계 진하게. */
    val OtherGrayOnMap = Color(0xFF5D616C)
}

/**
 * 우리 체육관([centerLat],[centerLng]) 반경 [radiusKm] 원 + 체육관 점을 그린 카카오맵.
 *
 * - 스크롤 화면 안에 들어가므로 지도를 만지는 동안 부모 스크롤이 터치를 가로채지 않게 막는다.
 * - MapView 라이프사이클(resume/pause/finish)은 공식 가이드대로 화면 생명주기에 맞춘다.
 * - SDK 를 쓸 수 없거나 인증·로딩이 실패하면 지도 자리에 이유 문구만 보여준다(목록은 화면이 따로 보여줌).
 */
@Composable
fun KakaoGymMap(
    centerLat: Double,
    centerLng: Double,
    radiusKm: Int,
    pins: List<MapPin>,
    onPinClick: (String) -> Unit,
    primary: Color,
    modifier: Modifier = Modifier,
) {
    val unavailable = KakaoMapSupport.unavailableReason()
    if (unavailable != null) {
        MapNotice(unavailable, modifier)
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current.density
    val paddingPx = with(LocalDensity.current) { 28.dp.roundToPx() }
    val latestOnPinClick by rememberUpdatedState(onPinClick)
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var failed by remember { mutableStateOf(false) }
    val mapView = remember { MapView(context) }
    val container = remember {
        TouchHoldingFrame(context).apply {
            addView(mapView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    DisposableEffect(lifecycle) {
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {
                kakaoMap = null
            }

            override fun onMapError(error: Exception) {
                // 인증 실패(MapAuthException: 401 키/키해시, 403 권한, 429 쿼터) 등 — 원인은 logcat(k3f 필터).
                Log.w("OctaLink.KakaoMap", "map error", error)
                failed = true
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                // 2.15.x 는 Boolean 반환(처리했으면 true) — 공개 레퍼런스의 void 시그니처와 다름.
                map.setOnLabelClickListener { _, _, label ->
                    val key = label.tag as? String ?: return@setOnLabelClickListener false
                    latestOnPinClick(key)
                    true
                }
                kakaoMap = map
            }

            override fun getPosition(): LatLng = LatLng.from(centerLat, centerLng)
            override fun getZoomLevel(): Int = 15
        })
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            kakaoMap = null
            mapView.finish()
        }
    }

    // 데이터·반경이 바뀔 때마다 다시 그림(점 수십 개 수준이라 전체 교체).
    LaunchedEffect(kakaoMap, pins, radiusKm, centerLat, centerLng) {
        val map = kakaoMap ?: return@LaunchedEffect
        drawGyms(map, centerLat, centerLng, radiusKm, pins, primary, paddingPx, density)
    }

    if (failed) {
        MapNotice("지도를 불러오지 못했어요.", modifier)
    } else {
        AndroidView(factory = { container }, modifier = modifier)
    }
}

private fun drawGyms(
    map: KakaoMap,
    lat: Double,
    lng: Double,
    radiusKm: Int,
    pins: List<MapPin>,
    primary: Color,
    paddingPx: Int,
    density: Float,
) {
    val center = LatLng.from(lat, lng)
    val radiusM = radiusKm * 1000.0

    map.shapeManager?.layer?.let { shapes ->
        shapes.removeAll()
        shapes.addPolygon(
            PolygonOptions.from(
                DotPoints.fromCircle(center, radiusM.toFloat()),
                PolygonStyles.from(primary.copy(alpha = 0.08f).toArgb(), 2f, primary.copy(alpha = 0.85f).toArgb()),
            ),
        )
    }

    val labels = map.labelManager ?: return
    val styles = mapOf(
        PinKind.MY_GYM to dotStyle(18, primary, density),
        PinKind.MEMBER_GYM to dotStyle(15, GymMapColors.MemberBlue, density),
        PinKind.OTHER_GYM to dotStyle(11, GymMapColors.OtherGrayOnMap, density),
    ).mapValues { (_, s) -> labels.addLabelStyles(LabelStyles.from(s)) }
    val rank = mapOf(PinKind.MY_GYM to 3L, PinKind.MEMBER_GYM to 2L, PinKind.OTHER_GYM to 1L)
    val layer = labels.layer ?: return
    layer.removeAll()
    for (p in pins) {
        layer.addLabel(
            LabelOptions.from(LatLng.from(p.lat, p.lng))
                .setStyles(styles.getValue(p.kind))
                .setRank(rank.getValue(p.kind))
                .setClickable(p.kind != PinKind.MY_GYM)
                .setTag(p.key),
        )
    }

    // 반경 원이 화면에 꽉 차도록 동서남북 끝점에 맞춤.
    val dLat = radiusM / 111_320.0
    val dLng = radiusM / (111_320.0 * cos(Math.toRadians(lat)))
    map.moveCamera(
        CameraUpdateFactory.fitMapPoints(
            arrayOf(
                LatLng.from(lat + dLat, lng), LatLng.from(lat, lng + dLng),
                LatLng.from(lat - dLat, lng), LatLng.from(lat, lng - dLng),
            ),
            paddingPx,
        ),
    )
}

/**
 * 흰 테두리 원 점. 실기기(Galaxy A34)에서 비트맵이 dp 배율 없이 픽셀 그대로 그려져
 * 화면 밀도([density])를 곱한 픽셀로 만든다.
 */
private fun dotStyle(sizeDp: Int, fill: Color, density: Float): LabelStyle {
    val px = (sizeDp * density).roundToInt().coerceAtLeast(4)
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val r = px / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(r, r, r, paint)
    paint.color = fill.toArgb()
    canvas.drawCircle(r, r, r - 2f * density, paint)
    return LabelStyle.from(bmp).setAnchorPoint(0.5f, 0.5f)
}

@Composable
private fun MapNotice(text: String, modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** 지도를 만지는 동안 부모(세로 스크롤)가 터치를 가로채지 않게 — 그래야 지도 이동·확대가 된다. */
private class TouchHoldingFrame(context: Context) : FrameLayout(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        return super.dispatchTouchEvent(ev)
    }
}
