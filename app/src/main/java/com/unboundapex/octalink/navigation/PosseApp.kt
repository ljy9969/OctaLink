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
import com.unboundapex.octalink.ui.screens.admin.AdminScreen
import com.unboundapex.octalink.ui.screens.admin.CoachCommentScreen
import com.unboundapex.octalink.ui.screens.admin.SkillScoreProposeScreen
import com.unboundapex.octalink.ui.screens.attendance.AttendanceReviewScreen
import com.unboundapex.octalink.ui.screens.attendance.AttendanceScreen
import com.unboundapex.octalink.ui.screens.bracket.BracketDrawScreen
import com.unboundapex.octalink.ui.screens.bracket.BracketScreen
import com.unboundapex.octalink.ui.screens.community.CommunityScreen
import com.unboundapex.octalink.ui.screens.creator.CreatorScreen
import com.unboundapex.octalink.ui.screens.curriculum.CurriculumScreen
import com.unboundapex.octalink.ui.screens.home.HomeScreen
import com.unboundapex.octalink.ui.screens.info.InfoScreen
import com.unboundapex.octalink.ui.screens.onboarding.LeftScreen
import com.unboundapex.octalink.ui.screens.onboarding.LoginScreen
import com.unboundapex.octalink.ui.screens.onboarding.PendingApprovalScreen
import com.unboundapex.octalink.ui.screens.onboarding.RejectedScreen
import com.unboundapex.octalink.ui.screens.onboarding.SignupScreen
import com.unboundapex.octalink.ui.screens.profile.ProfileScreen
import com.unboundapex.octalink.ui.screens.splash.SplashScreen

sealed class Route(val path: String, val label: String, val icon: ImageVector) {
    data object Home : Route("home", "홈", Icons.Outlined.Home)
    data object Curriculum : Route("curriculum", "커리큘럼", Icons.AutoMirrored.Outlined.Assignment)
    data object Attendance : Route("attendance", "출석", Icons.Outlined.CheckCircle)
    data object Community : Route("community", "커뮤니티", Icons.Outlined.Forum)
    data object Profile : Route("profile", "프로필", Icons.Outlined.Person)
    data object Bracket : Route("bracket", "대진표", Icons.Outlined.CheckCircle)
    data object BracketAdmin : Route("bracket_admin", "대진표 관리", Icons.Outlined.CheckCircle)
    data object BracketDraw : Route("bracket_draw", "추첨", Icons.Outlined.CheckCircle)
    data object Info : Route("info", "체육관 정보", Icons.Outlined.Info)
    data object Admin : Route("admin", "운영", Icons.Outlined.ManageAccounts)
    data object Creator : Route("creator", "창조자", Icons.Outlined.Person)
    data object AttendanceReview : Route("attendance_review", "출결 검토", Icons.Outlined.CheckCircle)
    data object CoachComment : Route("coach_comment", "코멘트", Icons.Outlined.ManageAccounts)
    data object SkillScorePropose : Route("skill_score_propose", "스킬 제안", Icons.Outlined.ManageAccounts)
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
                    onOpenBracket = { navController.navigate(Route.Bracket.path) },
                    onOpenInfo = { navController.navigate(Route.Info.path) },
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
                ProfileScreen(sessionVm = sessionVm)
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
                BracketScreen(
                    tournamentVm = tournamentVm,
                    onBack = { navController.popBackStack() },
                    onOpenDraw = { /* member 모드에서는 호출되지 않음 */ },
                    canManage = false,
                )
            }
            composable(Route.BracketAdmin.path) {
                // 운영진 진입(Admin → 토너먼트 추첨/대진 관리): 새 추첨/초기화 칩 + BracketDrawScreen 진입 가능.
                BracketScreen(
                    tournamentVm = tournamentVm,
                    onBack = { navController.popBackStack() },
                    onOpenDraw = { navController.navigate(Route.BracketDraw.path) },
                    canManage = true,
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
