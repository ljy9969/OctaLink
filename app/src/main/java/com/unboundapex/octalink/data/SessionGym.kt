package com.unboundapex.octalink.data

/**
 * 현재 로그인 사용자의 소속 체육관 id (멀티테넌트 쿼리 스코핑용 **앰비언트 세션 상태**).
 *
 * - [com.unboundapex.octalink.data.session.SessionViewModel] 이 회원 doc 로드 시 [gymId] 설정,
 *   로그아웃/세션 종료 시 null 로 clear.
 * - Firestore repository 들이 members/posts/attendance/tournaments 쿼리에 `gymId` 필터를 걸 때 참조.
 *
 * 앱 흐름상 6개 화면은 항상 회원 doc 로드(=gymId 확정) 이후 렌더되므로, 리스너 부착 시점엔 값이 채워짐.
 * 값이 비어 있으면(로그인 전/마이그레이션 전) 쿼리는 `gymId == ""` 로 fail-closed 되어 타 체육관 데이터가 새지 않음.
 */
object SessionGym {
    @Volatile
    var gymId: String? = null
}
