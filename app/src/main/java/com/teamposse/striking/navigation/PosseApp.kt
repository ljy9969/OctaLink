package com.teamposse.striking.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.teamposse.striking.data.session.SessionViewModel
import com.teamposse.striking.data.tournament.TournamentViewModel
import com.teamposse.striking.ui.screens.attendance.AttendanceScreen
import com.teamposse.striking.ui.screens.bracket.BracketDrawScreen
import com.teamposse.striking.ui.screens.bracket.BracketScreen
import com.teamposse.striking.ui.screens.community.CommunityScreen
import com.teamposse.striking.ui.screens.curriculum.CurriculumScreen
import com.teamposse.striking.ui.screens.home.HomeScreen
import com.teamposse.striking.ui.screens.info.InfoScreen
import com.teamposse.striking.ui.screens.profile.ProfileScreen

sealed class Route(val path: String, val label: String, val icon: ImageVector) {
    data object Home : Route("home", "홈", Icons.Outlined.Home)
    data object Curriculum : Route("curriculum", "커리큘럼", Icons.AutoMirrored.Outlined.Assignment)
    data object Attendance : Route("attendance", "출석", Icons.Outlined.CheckCircle)
    data object Community : Route("community", "커뮤니티", Icons.Outlined.Forum)
    data object Profile : Route("profile", "프로필", Icons.Outlined.Person)
    data object Bracket : Route("bracket", "대진표", Icons.Outlined.CheckCircle)
    data object BracketDraw : Route("bracket_draw", "추첨", Icons.Outlined.CheckCircle)
    data object Info : Route("info", "체육관 정보", Icons.Outlined.Info)
}

private val tabs = listOf(Route.Home, Route.Curriculum, Route.Attendance, Route.Community, Route.Profile)

@Composable
fun PosseApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val sessionVm: SessionViewModel = viewModel()
    val tournamentVm: TournamentViewModel = viewModel()

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
            composable(Route.Attendance.path) { AttendanceScreen(sessionVm = sessionVm) }
            composable(Route.Community.path) { CommunityScreen() }
            composable(Route.Profile.path) {
                ProfileScreen(sessionVm = sessionVm)
            }
            composable(Route.Info.path) {
                InfoScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.Bracket.path) {
                BracketScreen(
                    tournamentVm = tournamentVm,
                    onBack = { navController.popBackStack() },
                    onOpenDraw = { navController.navigate(Route.BracketDraw.path) }
                )
            }
            composable(Route.BracketDraw.path) {
                BracketDrawScreen(
                    sessionVm = sessionVm,
                    tournamentVm = tournamentVm,
                    onBack = { navController.popBackStack() },
                    onDrawComplete = {
                        navController.navigate(Route.Bracket.path) {
                            popUpTo(Route.BracketDraw.path) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
