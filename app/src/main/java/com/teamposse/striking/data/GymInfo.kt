package com.teamposse.striking.data

/**
 * Team Posse Striking 강남점 운영 정보.
 * 호스팅된 정적 페이지(개인정보처리방침)와 외부 링크는 여기서만 관리.
 */
object GymInfo {
    const val NAME: String = "Team Posse Striking"
    const val BRANCH: String = "강남점"
    const val ADDRESS: String = "서울 강남구 테헤란로44길 5 대아빌딩 지하 2층"
    const val PHONE: String = "0507-1408-6634"

    /** 네이버 플레이스 단축 URL */
    const val NAVER_PLACE_URL: String = "https://naver.me/FcmAMJao"

    /** 인스타그램 공식 계정 */
    const val INSTAGRAM_URL: String = "https://www.instagram.com/teamposse_gangnam/"

    /** 네이버 블로그 */
    const val BLOG_URL: String = "https://blog.naver.com/teamposse2007"

    /**
     * 개인정보처리방침 호스팅 URL.
     * GitHub Pages: Settings → Pages → Source: main branch / /docs folder
     * 활성화 후 약 1분 내에 URL 발행. Play Store 등록용으로도 안정적.
     */
    const val PRIVACY_POLICY_URL: String =
        "https://ljy9969.github.io/OctaLink/privacy-policy.html"

    /** Play Store 출시 후 업데이트 */
    const val PLAY_STORE_URL: String = "https://play.google.com/store/apps/details?id=com.teamposse.striking"
}
