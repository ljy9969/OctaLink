package com.unboundapex.octalink.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.CommentDoc
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * AdminScreen 의 "회원 한 줄 코멘트 작성" 진입점.
 *
 * 운영진 풀(staff: COACH/MASTER/CREATOR)이 회원/코치/창조자에게 코멘트 작성.
 * AttendanceReview 와 유사 패턴 — 회원 list → 회원 선택 → 코멘트 시계열 + 작성 dialog.
 *
 * 대상 풀: APPROVED 상태의 MEMBER + COACH + CREATOR. 관장(MASTER)은 제외.
 */
class CoachCommentViewModel : ViewModel() {
    private val membersRepo = RepositoryProvider.members
    private val commentsRepo = RepositoryProvider.comments

    /** APPROVED 회원/코치/창조자 풀 (이름순). 관장(MASTER)만 제외. */
    val members: StateFlow<List<MemberDoc>> =
        membersRepo.observeByStatus(MembershipStatus.APPROVED)
            .map { list ->
                list.filter { it.role != Role.MASTER }.sortedBy { it.name }
            }
            .catch { e ->
                android.util.Log.e("OctaLink.Comments", "members flow error", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedMemberId = MutableStateFlow<String?>(null)
    val selectedMemberId: StateFlow<String?> = _selectedMemberId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedComments: StateFlow<List<CommentDoc>> =
        _selectedMemberId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else commentsRepo.observeByMember(id)
            }
            .catch { e ->
                android.util.Log.e("OctaLink.Comments", "selectedComments flow error", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectMember(memberId: String) {
        _selectedMemberId.value = memberId
    }

    fun clearSelection() {
        _selectedMemberId.value = null
    }

    fun submit(
        toMemberId: String,
        byMasterId: String,
        byMasterName: String,
        text: String,
        classDate: LocalDate,
    ) {
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching {
                commentsRepo.create(
                    toMemberId = toMemberId,
                    byMasterId = byMasterId,
                    byMasterName = byMasterName,
                    text = text.trim().take(80),
                    classDate = classDate,
                )
            }.onFailure {
                android.util.Log.e("OctaLink.Comments", "create FAILED", it)
            }
        }
    }

    fun delete(toMemberId: String, commentId: String) {
        viewModelScope.launch {
            runCatching { commentsRepo.delete(toMemberId, commentId) }
                .onFailure { android.util.Log.e("OctaLink.Comments", "delete FAILED", it) }
        }
    }
}
