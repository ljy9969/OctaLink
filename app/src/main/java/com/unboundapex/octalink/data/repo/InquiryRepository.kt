package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.schema.InquiryCategory
import com.unboundapex.octalink.data.schema.InquiryDoc
import kotlinx.coroutines.flow.Flow

/**
 * 1:1 문의([InquiryDoc]) 영속화. `inquiries/{id}` — 비공개(작성자 본인 + 운영진만 read).
 *
 * 흐름: 사용자 작성(PENDING) → support 에이전트 답변 초안(ops) → 운영자 승인·게시(ANSWERED).
 * 답변 게시는 운영자(MASTER/CREATOR) 권한 — Firestore rules 에서 검증.
 */
interface InquiryRepository {
    /** 내가 쓴 문의 — 작성자 본인 것만. createdAt DESC. */
    fun observeMine(authorId: String): Flow<List<InquiryDoc>>

    /** 운영진용 — 우리 체육관 전체 문의. createdAt DESC. */
    fun observeAllForGym(): Flow<List<InquiryDoc>>

    /** 문의 작성. id 는 Firestore 자동 생성. 작성 시점이 createdAt, status=PENDING. */
    suspend fun create(
        authorId: String,
        authorName: String,
        category: InquiryCategory,
        text: String,
    ): InquiryDoc

    /** 운영자 답변 게시 — answer 저장 + status=ANSWERED. 권한은 rules 검증. */
    suspend fun postAnswer(inquiryId: String, answer: String, answeredBy: String)

    /** 작성자 본인 문의 삭제. */
    suspend fun delete(inquiryId: String)
}
