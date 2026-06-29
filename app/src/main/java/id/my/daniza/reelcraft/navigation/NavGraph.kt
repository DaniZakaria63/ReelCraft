package id.my.daniza.reelcraft.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import id.my.daniza.reelcraft.screen.editor.EditorScreen
import id.my.daniza.reelcraft.ui.export.ExportScreen
import id.my.daniza.reelcraft.screen.home.HomeScreen

@Composable
fun ReelCraftNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenProject = { projectId ->
                    navController.navigate(Screen.Editor.createRoute(projectId))
                }
            )
        }

        composable(
            route = Screen.Editor.ROUTE_WITH_ARGS,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            EditorScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExport = {
                    navController.navigate(Screen.Export.createRoute(projectId))
                }
            )
        }

        composable(
            route = Screen.Export.ROUTE_WITH_ARGS,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            ExportScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
