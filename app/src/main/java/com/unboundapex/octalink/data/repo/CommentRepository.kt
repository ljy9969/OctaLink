package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.schema.CommentDoc
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 운영진 한 줄 코멘트 ([CommentDoc]) 영속화 추상화.
 *
 * Firestore 경로: `members/{toMemberId}/comments/{commentId}` — 수신 회원 서브컬렉션.
 * 권한 (Security Rules):
 *  - read: 본인 (toMemberId) + 운영진(`isStaff`)
 *  - create: 운영진만, byMasterId == request.auth.uid (위조 차단)
 *  - update/delete: 본인이 작성한 코멘트만 (운영진 중에서도 작성자만)
 *
 * 정렬: classDate DESC (최근 수업 코멘트 먼저).
 */
interface CommentRepository {
    /** 특정 회원이 받은 코멘트 시계열 (classDate DESC). 본인 / 운영진 모두 read 가능. */
    fun observeByMember(toMemberId: String): Flow<List<CommentDoc>>

    /**
     * 코멘트 작성. id 는 Firestore 자동 생성. createdAt 은 서버 timestamp.
     * @param classDate 코멘트 대상 수업/관찰일 (기본 today).
     */
    suspend fun create(
        toMemberId: String,
        byMasterId: String,
        byMasterName: String,
        text: String,
        classDate: LocalDate = LocalDate.now(),
    ): CommentDoc

    /** 코멘트 삭제. 작성한 운영진 본인만 (Rules 강제). */
    suspend fun delete(toMemberId: String, commentId: String)
}
