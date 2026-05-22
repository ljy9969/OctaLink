package com.unboundapex.octalink.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.isStaff
import com.unboundapex.octalink.data.session.SessionState
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.data.tournament.TournamentViewModel
import com.unboundapex.octalink.messaging.ClassReminderScheduler
import com.unboundapex.octalink.ui.screens.admin.AdminScreen
import com.unboundapex.octalink.ui.screens.admin.CoachCommentScreen
import com.unboundapex.octalink.ui.screens.admin.SkillScoreProposeScreen
import com.unboundapex.octalink.ui.screens.attendance.AttendanceReviewScreen
import com.unboundapex.octalink.ui.screens.attendance.AttendanceScreen
import com.unboundapex.octalink.ui.screens.bracket.BracketDrawScreen
import com.unboundapex.octalink.ui.screens.bracket.BracketScreen
import com.unboundapex.octalink.ui.screens.bracket.TournamentHistoryScreen
import com.unboundapex.octalink.ui.screens.community.CommunityScreen
import com.unboundapex.octalink.ui.screens.creator.CreatorScreen
import com.unboundapex.octalink.ui.screens.curriculum.CurriculumScreen
import com.unboundapex.octalink.ui.screens.home.HomeScreen
import com.unboundapex.octalink.ui.screens.home.MyAttendanceHistoryScreen
import com.unboundapex.octalink.ui.screens.info.InfoScreen
import com.unboundapex.octalink.ui.screens.onboarding.LeftScreen
import com.unboundapex.octalink.ui.screens.onboarding.LoginScreen
import com.unboundapex.octalink.ui.screens.onboarding.PendingApprovalScreen
import com.unboundapex.octalink.ui.screens.onboarding.RejectedScreen
import com.unboundapex.octalink.ui.screens.onboarding.SignupScreen
import com.unboundapex.octalink.ui.screens.profile.ProfileScreen
import com.unboundapex.octalink.ui.screens.profile.ProfileSettingsScreen
import com.unboundapex.octalink.ui.screens.splash.SplashScreen

sealed class Route(val path: String, val label: String, val icon: ImageVector) {
    data object Home : Route("home", "홈", Icons.Outlined.Home)
    data object Curriculum : Route("curriculum", "커리큘럼", Icons.AutoMirrored.Outlined.Assignment)
    data object Attendance : Route("attendance", "출석", Icons.Outlined.CheckCircle)
    data object Community : Route("community", "커뮤니티", Icons.Outlined.Forum)
    data object Profile : Route("profile", "프로필", Icons.Outlined.Person)
    data object ProfileSettings : Route("profile_settings", "프로필 설정", Icons.Outlined.Settings)
    data object Bracket : Route("bracket", "대진표", Icons.Outlined.CheckCircle)
    data object BracketAdmin : Route("bracket_admin", "대진표 관리", Icons.Outlined.CheckCircle)
    data object BracketDraw : Route("bracket_draw", "추첨", Icons.Outlined.CheckCircle)
    data object Info : Route("info", "체육관 정보", Icons.Outlined.Info)
    data object Admin : Route("admin", "운영", Icons.Outlined.ManageAccounts)
    data object Creator : Route("creator", "창조자", Icons.Outlined.Person)
    data object AttendanceReview : Route("attendance_review", "출결 검토", Icons.Outlined.CheckCircle)
    data object CoachComment : Route("coach_comment", "코멘트", Icons.Outlined.ManageAccounts)
    data object SkillScorePropose : Route("skill_score_propose", "스킬 제안", Icons.Outlined.ManageAccounts)
    data object MyAttendance : Route("my_attendance", "내 출석", Icons.Outlined.CheckCircle)
    data object TournamentHistory : Route("tournament_history", "토너먼트 히스토리", Icons.Outlined.CheckCircle)
    /** 히스토리에서 특정 토너먼트 read-only 진입 — path arg 로 tournamentId 전달. */
    data object BracketView : Route("bracket_view/{tournamentId}", "대진표 보기", Icons.Outlined.CheckCircle) {
        fun pathFor(tournamentId: String) = "bracket_view/$tournamentId"
    }
}

private val baseTabs = listOf(Route.Home, Route.Curriculum, Route.Attendance, Route.Community, Route.Profile)

@Composable
fun PosseApp() {
    val sessionVm: SessionViewModel = viewModel()
    val session by sessionVm.state.collectAsState()

    // 스플래시 최소 노출 시간 — 캐시된 Firebase Auth 면 LOADING 이 ~100ms 만에 끝나서
    // 심장박동 애니메이션 1주기(1200ms) 도 못 보고 사라짐. 1.5초 동안은 SplashScreen 유지.
    var minSplashDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        minSplashDone = true
    }
    if (!minSplashDone) {
        SplashScreen()
        return
    }

    // 세션 단계별 진입 분기 — APPROVED 가 아닌 모든 단계는 메인 앱 진입 차단
    when (session.phase) {
        SessionState.Phase.LOADING -> {
            // 초기 uid resolve 가 1.5초 넘게 걸리면 계속 splash 노출.
            SplashScreen()
            return
        }
        SessionState.Phase.UNAUTHENTICATED -> {
            LoginScreen(sessionVm = sessionVm)
            return
        }
        SessionState.Phase.PENDING_SIGNUP -> {
            SignupScreen(sessionVm = sessionVm)
            return
        }
        SessionState.Phase.AUTHENTICATED -> {
            when (session.status) {
                MembershipStatus.APPROVED -> Unit // 메인 앱 렌더 (아래)
                MembershipStatus.PENDING -> {
                    PendingApprovalScreen(sessionVm = sessionVm)
                    return
                }
                MembershipStatus.LEFT -> {
                    // 자진 탈퇴 — 재가입 CTA 제공
                    LeftScreen(sessionVm = sessionVm)
                    return
                }
                MembershipStatus.REJECTED,
                MembershipStatus.SUSPENDED -> {
                    RejectedScreen(sessionVm = sessionVm)
                    return
                }
            }
        }
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val tournamentVm: TournamentViewModel = viewModel()

    // 토너먼트 매치 갱신 시 resolvedByMasterId 로 들어갈 작성자 uid.
    // session.member?.id 변화 시 (로그인/탈퇴/회원 doc 갱신) 자동 동기화.
    LaunchedEffect(session.member?.id) {
        tournamentVm.currentUserId = session.member?.id
    }

    // 로그인 직후 1회 — 오늘 남은 수업의 CLASS_REMINDER 스케줄. prefs OFF 면 내부에서 skip.
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(session.member?.id) {
        val mid = session.member?.id ?: return@LaunchedEffect
        ClassReminderScheduler.scheduleAll(appContext)
        // FCM 토큰 보장 영속화 — onNewToken 콜백은 토큰 회전 시에만 fire 이라 신규 로그인
        // (혹은 첫 설치 후 회전 누락) 케이스에서 members.{uid}.fcmToken 누락 가능. 매 세션
        // 시작마다 한 번 fetch → write. 같은 토큰이면 멱등.
        runCatching {
            val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
            if (token.isNotBlank()) {
                com.unboundapex.octalink.data.repo.RepositoryProvider.members
                    .updateFcmToken(mid, token)
                android.util.Log.i(
                    "OctaLink.Fcm",
                    "token ensured for member=$mid (${token.take(12)}…)",
                )
            }
        }.onFailure {
            android.util.Log.w("OctaLink.Fcm", "ensure token FAILED for $mid", it)
        }
    }

    // 운영진(COACH+) 이상은 "운영" 탭이 Profile 뒤에 추가됨. 회원은 안 보임.
    val tabs = if (session.role.isStaff) baseTabs + Route.Admin else baseTabs

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = backStack?.destination?.hierarchy?.any { it.route == tab.path } == true
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                if (currentRoute != tab.path) {
                                    navController.navigate(tab.path) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            inclusive = false
                                            saveState = false
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Home.path,
            modifier = Modifier.padding(padding)
        ) {
            composable(Route.Home.path) {
                HomeScreen(
                    sessionVm = sessionVm,
                    onOpenBracket = { navController.navigate(Route.Bracket.path) },
                    onOpenInfo = { navController.navigate(Route.Info.path) },
                    onOpenMyAttendance = { navController.navigate(Route.MyAttendance.path) },
                )
            }
            composable(Route.MyAttendance.path) {
                MyAttendanceHistoryScreen(
                    onBack = { navController.popBackStack() },
                    sessionVm = sessionVm,
                )
            }
            composable(Route.Curriculum.path) { CurriculumScreen() }
            composable(Route.Attendance.path) {
                AttendanceScreen(
                    sessionVm = sessionVm,
                    onOpenReview = { navController.navigate(Route.AttendanceReview.path) },
                )
            }
            composable(Route.AttendanceReview.path) {
                AttendanceReviewScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.Community.path) { CommunityScreen(sessionVm = sessionVm) }
            composable(Route.Profile.path) {
                ProfileScreen(
                    sessionVm = sessionVm,
                    onOpenTournamentHistory = { navController.navigate(Route.TournamentHistory.path) },
                    onOpenSettings = { navController.navigate(Route.ProfileSettings.path) },
                )
            }
            composable(Route.ProfileSettings.path) {
                ProfileSettingsScreen(
                    sessionVm = sessionVm,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Route.Info.path) {
                InfoScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.Admin.path) {
                AdminScreen(
                    sessionVm = sessionVm,
                    onOpenCreator = { navController.navigate(Route.Creator.path) },
                    onOpenCoachComment = { navController.navigate(Route.CoachComment.path) },
                    onOpenBracket = { navController.navigate(Route.BracketAdmin.path) },
                    onOpenSkillScorePropose = { navController.navigate(Route.SkillScorePropose.path) },
                )
            }
            composable(Route.CoachComment.path) {
                CoachCommentScreen(
                    onBack = { navController.popBackStack() },
                    sessionVm = sessionVm,
                )
            }
            composable(Route.SkillScorePropose.path) {
                SkillScoreProposeScreen(
                    onBack = { navController.popBackStack() },
                    sessionVm = sessionVm,
                )
            }
            composable(Route.Creator.path) {
                CreatorScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.Bracket.path) {
                // 회원 진입(Home → 이번 주 대진표): 결과 조회만, 새 추첨/초기화 칩 없음.
                // active 토너먼트 자동 선택 — 명시 override 해제.
                LaunchedEffect(Unit) { tournamentVm.selectTournament(null) }
                BracketScreen(
                    tournamentVm = tournamentVm,
                    onBack = { navController.popBackStack() },
                    onOpenDraw = { /* member 모드에서는 호출되지 않음 */ },
                    onOpenHistory = { navController.navigate(Route.TournamentHistory.path) },
                    canManage = false,
                )
            }
            composable(Route.BracketAdmin.path) {
                // 운영진 진입(Admin → 토너먼트 추첨/대진 관리): 새 추첨/초기화 칩 + BracketDrawScreen 진입 가능.
                LaunchedEffect(Unit) { tournamentVm.selectTournament(null) }
                BracketScreen(
                    tournamentVm = tournamentVm,
                    onBack = { navController.popBackStack() },
                    onOpenDraw = { navController.navigate(Route.BracketDraw.path) },
                    onOpenHistory = { navController.navigate(Route.TournamentHistory.path) },
                    canManage = true,
                )
            }
            composable(Route.TournamentHistory.path) {
                TournamentHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTournament = { id ->
                        navController.navigate(Route.BracketView.pathFor(id))
                    },
                    sessionVm = sessionVm,
                )
            }
            composable(
                route = Route.BracketView.path,
                arguments = listOf(
                    androidx.navigation.navArgument("tournamentId") {
                        type = androidx.navigation.NavType.StringType
                    }
                ),
            ) { entry ->
                val tournamentId = entry.arguments?.getString("tournamentId").orEmpty()
                LaunchedEffect(tournamentId) {
                    tournamentVm.selectTournament(tournamentId.takeIf { it.isNotBlank() })
                }
                BracketScreen(
                    tournamentVm = tournamentVm,
                    onBack = { navController.popBackStack() },
                    onOpenDraw = { /* read-only 모드 — 호출 안 됨 */ },
                    onOpenHistory = { /* 이미 히스토리 경유 진입 — 노출 안 함 */ },
                    canManage = false,
                    showHistoryButton = false,
                )
            }
            composable(Route.BracketDraw.path) {
                BracketDrawScreen(
                    sessionVm = sessionVm,
                    tournamentVm = tournamentVm,
                    onBack = { navController.popBackStack() },
                    onDrawComplete = {
                        // 추첨 완료 → 운영진 대진표 화면으로 (관리 권한 유지).
                        navController.navigate(Route.BracketAdmin.path) {
                            popUpTo(Route.BracketDraw.path) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
