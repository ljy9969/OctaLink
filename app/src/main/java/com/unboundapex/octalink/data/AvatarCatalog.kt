package com.unboundapex.octalink.data

import androidx.compose.ui.graphics.Color

data class Avatar(
    val id: String,
    val displayName: String,
    val initial: String,
    val accent: Color,
    /** res/drawable-nodpi/{id}.png — 캐릭터 본체. 정적 자산이며 런타임 색 변형 없음. */
    val bodyResourceName: String,
)

private val _catalog: List<Avatar> = buildList {
    data class WcMeta(val wc: WeightClass, val key: String, val accent: Color)

    val wcList = listOf(
        WcMeta(WeightClass.FEATHER, "feather", Color(0xFF7B1FA2)),
        WcMeta(WeightClass.LIGHT,   "light",   Color(0xFF1565C0)),
        WcMeta(WeightClass.WELTER,  "welter",  Color(0xFF2E7D32)),
        WcMeta(WeightClass.MIDDLE,  "middle",  Color(0xFFBF360C)),
        WcMeta(WeightClass.HEAVY,   "heavy",   Color(0xFF37474F)),
    )
    val genders = listOf("m" to "남", "f" to "여")

    for ((gPrefix, gLabel) in genders) {
        for ((wc, wKey, accent) in wcList) {
            val id = "${gPrefix}_${wKey}"
            add(Avatar(
                id = id,
                displayName = "$gLabel ${wc.displayName}",
                initial = gLabel,
                accent = accent,
                bodyResourceName = "${gPrefix}_${wKey}",
            ))
        }
    }
}

val AvatarCatalog: List<Avatar> = _catalog

fun avatarById(id: String?): Avatar =
    _catalog.firstOrNull { it.id == id } ?: _catalog.first()

/**
 * Auto-resolve avatar from Kakao gender string ("MALE"/"FEMALE") and weight class.
 * Falls back to male feather when gender is unknown.
 */
fun avatarFor(gender: String?, weightClass: WeightClass): Avatar {
    val g = if (gender?.uppercase() == "FEMALE") "f" else "m"
    val w = weightClass.name.lowercase()
    return _catalog.firstOrNull { it.id == "${g}_${w}" } ?: _catalog.first()
}
