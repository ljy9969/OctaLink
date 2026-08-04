package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.schema.PublicProfileDoc
import kotlinx.coroutines.flow.Flow

/**
 * 크로스짐 제한 공개 프로필 저장소 (`publicProfiles/{uid}`). 읽기 전용 — 쓰기는 서버 트리거.
 * 교류전 명단에서 **다른 체육관** 관원(성별/체급/벨트/경력/전적)을 조회.
 */
interface PublicProfileRepository {
    /** 특정 체육관의 공개 프로필 목록 (교류전 대상 명단). */
    fun observeByGym(gymId: String): Flow<List<PublicProfileDoc>>

    /** 단일 공개 프로필 (교류전 상세/상대 표시용). */
    fun observeById(uid: String): Flow<PublicProfileDoc?>
}
