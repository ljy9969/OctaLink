package com.teamposse.striking.data

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
    // ===== 페더급 (8명) =====
    Member("f01", "이지연", Belt.WHITE,  WeightClass.FEATHER, "rose"),
    Member("f02", "정유진", Belt.WHITE,  WeightClass.FEATHER, "chun_li"),
    Member("f03", "신예린", Belt.WHITE,  WeightClass.FEATHER, "cammy"),
    Member("f04", "조하진", Belt.BLUE,   WeightClass.FEATHER, "juri"),
    Member("f05", "한소희", Belt.BLUE,   WeightClass.FEATHER, "ibuki"),
    Member("f06", "송미라", Belt.PURPLE, WeightClass.FEATHER, "makoto"),
    Member("f07", "차예린", Belt.PURPLE, WeightClass.FEATHER, "elena"),
    Member("f08", "윤보라", Belt.BROWN,  WeightClass.FEATHER, "decapre"),

    // ===== 라이트급 (8명) =====
    Member("l01", "박정호", Belt.BLUE,   WeightClass.LIGHT, "ken"),
    Member("l02", "한도윤", Belt.BLUE,   WeightClass.LIGHT, "ryu"),
    Member("l03", "최민서", Belt.BLUE,   WeightClass.LIGHT, "guile"),
    Member("l04", "서지호", Belt.PURPLE, WeightClass.LIGHT, "fei_long"),
    Member("l05", "이도현", Belt.WHITE,  WeightClass.LIGHT, "dan"),
    Member("l06", "김형준", Belt.PURPLE, WeightClass.LIGHT, "adon"),
    Member("l07", "안재성", Belt.BROWN,  WeightClass.LIGHT, "yang"),
    Member("l08", "구본혁", Belt.BLACK,  WeightClass.LIGHT, "yun"),

    // ===== 웰터급 (8명) =====
    Member("w01", "오태양", Belt.BROWN,  WeightClass.WELTER, "balrog"),
    Member("w02", "장민호", Belt.BLUE,   WeightClass.WELTER, "dudley"),
    Member("w03", "차강민", Belt.PURPLE, WeightClass.WELTER, "evil_ryu"),
    Member("w04", "노승우", Belt.BLACK,  WeightClass.WELTER, "sagat"),
    Member("w05", "임재원", Belt.WHITE,  WeightClass.WELTER, "cody"),
    Member("w06", "송지환", Belt.BLUE,   WeightClass.WELTER, "guy"),
    Member("w07", "배준영", Belt.BROWN,  WeightClass.WELTER, "rolento"),
    Member("w08", "한승우", Belt.BLACK,  WeightClass.WELTER, "akuma"),

    // ===== 미들급 (8명) =====
    Member("m01", "김상혁", Belt.PURPLE, WeightClass.MIDDLE, "oni"),
    Member("m02", "윤성준", Belt.BLUE,   WeightClass.MIDDLE, "rufus"),
    Member("m03", "임도현", Belt.PURPLE, WeightClass.MIDDLE, "seth"),
    Member("m04", "강하늘", Belt.BROWN,  WeightClass.MIDDLE, "gouken"),
    Member("m05", "정현우", Belt.WHITE,  WeightClass.MIDDLE, "abel"),
    Member("m06", "백두산", Belt.BLUE,   WeightClass.MIDDLE, "c_viper"),
    Member("m07", "조형식", Belt.BROWN,  WeightClass.MIDDLE, "rose"),
    Member("m08", "권혁수", Belt.BLACK,  WeightClass.MIDDLE, "el_fuerte"),

    // ===== 헤비급 (8명) =====
    Member("h01", "백승현", Belt.BROWN,  WeightClass.HEAVY, "zangief"),
    Member("h02", "권태일", Belt.BLACK,  WeightClass.HEAVY, "hugo"),
    Member("h03", "민재훈", Belt.BLUE,   WeightClass.HEAVY, "e_honda"),
    Member("h04", "유지석", Belt.PURPLE, WeightClass.HEAVY, "t_hawk"),
    Member("h05", "남도현", Belt.WHITE,  WeightClass.HEAVY, "hakan"),
    Member("h06", "주영민", Belt.BLUE,   WeightClass.HEAVY, "blanka"),
    Member("h07", "한경수", Belt.BROWN,  WeightClass.HEAVY, "dhalsim"),
    Member("h08", "오대성", Belt.BLACK,  WeightClass.HEAVY, "vega"),
)
