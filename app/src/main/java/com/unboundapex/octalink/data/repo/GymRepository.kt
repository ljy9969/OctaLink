package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.schema.GymDoc
import kotlinx.coroutines.flow.Flow

/**
 * 체육관(테넌트) 저장소. `gyms/{gymId}`.
 *
 * 쓰기(생성/운영진 지정/가입코드)는 CREATOR/서버 전용이라 이 인터페이스는 **읽기 위주**.
 * 신규 회원의 gymId 확정(가입코드 검증)은 서버 `completeSignup` 이 담당.
 */
interface GymRepository {
    /** 단일 체육관 관찰 — 홈/프로필의 소속 체육관 표시용. */
    fun observeById(gymId: String): Flow<GymDoc?>

    /** 전체 체육관 목록 — CREATOR 관리 화면·교류전 대상 선택용. */
    fun observeAll(): Flow<List<GymDoc>>
}
