package dk.azp.cadence.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dk.azp.cadence.ui.groups.GroupListScreen
import dk.azp.cadence.ui.groups.JoinGroupScreen
import dk.azp.cadence.ui.overview.GroupOverviewScreen
import dk.azp.cadence.ui.tasks.GroupTasksScreen
import dk.azp.cadence.ui.tasks.TaskDetailScreen
import kotlinx.serialization.Serializable

@Serializable
object GroupListRoute

@Serializable
object JoinGroupRoute

@Serializable
data class GroupTasksRoute(val groupId: String)

@Serializable
data class TaskDetailRoute(val groupId: String, val taskId: String)

@Serializable
data class GroupOverviewRoute(val groupId: String)

@Composable
fun CadenceNavHost(requestedTask: TaskDetailRoute? = null, onRequestedTaskShown: () -> Unit = {}) {
    val navController = rememberNavController()
    LaunchedEffect(requestedTask) {
        if (requestedTask != null) {
            navController.navigate(GroupTasksRoute(requestedTask.groupId)) { popUpTo(GroupListRoute) }
            navController.navigate(requestedTask)
            onRequestedTaskShown()
        }
    }
    NavHost(navController = navController, startDestination = GroupListRoute) {
        composable<GroupListRoute> {
            GroupListScreen(
                onOpenGroup = { groupId -> navController.navigate(GroupTasksRoute(groupId)) },
                onJoinGroup = { navController.navigate(JoinGroupRoute) },
            )
        }
        composable<JoinGroupRoute> {
            JoinGroupScreen(
                onBack = { navController.popBackStack() },
                onJoined = { groupId -> navController.navigate(GroupTasksRoute(groupId)) { popUpTo(GroupListRoute) } },
            )
        }
        composable<GroupTasksRoute> { entry ->
            val route = entry.toRoute<GroupTasksRoute>()
            GroupTasksScreen(
                groupId = route.groupId,
                onBack = { navController.popBackStack() },
                onOpenTask = { taskId -> navController.navigate(TaskDetailRoute(route.groupId, taskId)) },
                onOpenOverview = { navController.navigate(GroupOverviewRoute(route.groupId)) },
            )
        }
        composable<TaskDetailRoute> { entry ->
            val route = entry.toRoute<TaskDetailRoute>()
            TaskDetailScreen(groupId = route.groupId, taskId = route.taskId, onBack = { navController.popBackStack() })
        }
        composable<GroupOverviewRoute> { entry ->
            val route = entry.toRoute<GroupOverviewRoute>()
            GroupOverviewScreen(
                groupId = route.groupId,
                onBack = { navController.popBackStack() },
                onLeft = { navController.popBackStack(GroupListRoute, inclusive = false) },
            )
        }
    }
}
