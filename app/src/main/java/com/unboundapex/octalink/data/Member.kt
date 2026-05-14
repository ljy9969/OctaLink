package com.unboundapex.octalink.data

enum class WeightClass(val displayName: String, val maxKg: Int?) {
    FEATHER("페더급", 66),
    LIGHT("라이트급", 70),
    WELTER("웰터급", 77),
    MIDDLE("미들급", 84),
    HEAVY("헤비급", null);

    val shortLabel: String get() = displayName.removeSuffix("급")
}

data class Member(
    val id: String,
    val name: String,
    val belt: Belt,
    val weightClass: WeightClass,
    val avatarId: String,
)

val memberPool: List<Member> = listOf(
    // ===== 페더급 (8명, 여) =====
    Member("f01", "이지연", Belt.WHITE,  WeightClass.FEATHER, "f_feather"),
    Member("f02", "정유진", Belt.WHITE,  WeightClass.FEATHER, "f_feather"),
    Member("f03", "신예린", Belt.WHITE,  WeightClass.FEATHER, "f_feather"),
    Member("f04", "조하진", Belt.BLUE,   WeightClass.FEATHER, "f_feather"),
    Member("f05", "한소희", Belt.BLUE,   WeightClass.FEATHER, "f_feather"),
    Member("f06", "송미라", Belt.PURPLE, WeightClass.FEATHER, "f_feather"),
    Member("f07", "차예린", Belt.PURPLE, WeightClass.FEATHER, "f_feather"),
    Member("f08", "윤보라", Belt.BROWN,  WeightClass.FEATHER, "f_feather"),

    // ===== 라이트급 (8명, 남) =====
    Member("l01", "박정호", Belt.BLUE,   WeightClass.LIGHT, "m_light"),
    Member("l02", "한도윤", Belt.BLUE,   WeightClass.LIGHT, "m_light"),
    Member("l03", "최민서", Belt.BLUE,   WeightClass.LIGHT, "m_light"),
    Member("l04", "서지호", Belt.PURPLE, WeightClass.LIGHT, "m_light"),
    Member("l05", "이도현", Belt.WHITE,  WeightClass.LIGHT, "m_light"),
    Member("l06", "김형준", Belt.PURPLE, WeightClass.LIGHT, "m_light"),
    Member("l07", "안재성", Belt.BROWN,  WeightClass.LIGHT, "m_light"),
    Member("l08", "구본혁", Belt.BLACK,  WeightClass.LIGHT, "m_light"),

    // ===== 웰터급 (8명, 남) =====
    Member("w01", "오태양", Belt.BROWN,  WeightClass.WELTER, "m_welter"),
    Member("w02", "장민호", Belt.BLUE,   WeightClass.WELTER, "m_welter"),
    Member("w03", "차강민", Belt.PURPLE, WeightClass.WELTER, "m_welter"),
    Member("w04", "노승우", Belt.BLACK,  WeightClass.WELTER, "m_welter"),
    Member("w05", "임재원", Belt.WHITE,  WeightClass.WELTER, "m_welter"),
    Member("w06", "송지환", Belt.BLUE,   WeightClass.WELTER, "m_welter"),
    Member("w07", "배준영", Belt.BROWN,  WeightClass.WELTER, "m_welter"),
    Member("w08", "한승우", Belt.BLACK,  WeightClass.WELTER, "m_welter"),

    // ===== 미들급 (8명, 남) =====
    Member("m01", "김상혁", Belt.PURPLE, WeightClass.MIDDLE, "m_middle"),
    Member("m02", "윤성준", Belt.BLUE,   WeightClass.MIDDLE, "m_middle"),
    Member("m03", "임도현", Belt.PURPLE, WeightClass.MIDDLE, "m_middle"),
    Member("m04", "강하늘", Belt.BROWN,  WeightClass.MIDDLE, "m_middle"),
    Member("m05", "정현우", Belt.WHITE,  WeightClass.MIDDLE, "m_middle"),
    Member("m06", "백두산", Belt.BLUE,   WeightClass.MIDDLE, "m_middle"),
    Member("m07", "조형식", Belt.BROWN,  WeightClass.MIDDLE, "m_middle"),
    Member("m08", "권혁수", Belt.BLACK,  WeightClass.MIDDLE, "m_middle"),
    Member("m09", "장태준", Belt.BLACK,  WeightClass.MIDDLE, "m_middle"),

    // ===== 헤비급 (8명, 남) =====
    Member("h01", "백승현", Belt.BROWN,  WeightClass.HEAVY, "m_heavy"),
    Member("h02", "권태일", Belt.BLACK,  WeightClass.HEAVY, "m_heavy"),
    Member("h03", "민재훈", Belt.BLUE,   WeightClass.HEAVY, "m_heavy"),
    Member("h04", "유지석", Belt.PURPLE, WeightClass.HEAVY, "m_heavy"),
    Member("h05", "남도현", Belt.WHITE,  WeightClass.HEAVY, "m_heavy"),
    Member("h06", "주영민", Belt.BLUE,   WeightClass.HEAVY, "m_heavy"),
    Member("h07", "한경수", Belt.BROWN,  WeightClass.HEAVY, "m_heavy"),
    Member("h08", "오대성", Belt.BLACK,  WeightClass.HEAVY, "m_heavy"),
)
