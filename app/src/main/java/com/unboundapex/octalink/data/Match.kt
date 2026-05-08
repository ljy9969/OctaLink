package com.unboundapex.octalink.data

/**
 * 한 매치(2인 대전)의 in-memory 모델. 토너먼트 트리 화면 + ViewModel에서 사용.
 * 영속화 시점에는 [com.unboundapex.octalink.data.schema.MatchDoc] 으로 매핑.
 */
data class Match(
    val red: String,
    val blue: String,
    val winner: String? = null,
) {
    val isPending: Boolean get() = red == "?" || blue == "?"
    val isResolved: Boolean get() = winner != null
}
