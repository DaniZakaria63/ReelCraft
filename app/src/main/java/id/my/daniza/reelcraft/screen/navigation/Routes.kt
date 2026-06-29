package id.my.daniza.reelcraft.screen.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")

    data class Editor(val projectId: String = "{projectId}") : Screen("editor/$projectId") {
        companion object {
            const val ROUTE_WITH_ARGS = "editor/{projectId}"
            fun createRoute(projectId: String) = "editor/$projectId"
        }
    }

    data class Export(val projectId: String = "{projectId}") : Screen("export/$projectId") {
        companion object {
            const val ROUTE_WITH_ARGS = "export/{projectId}"
            fun createRoute(projectId: String) = "export/$projectId"
        }
    }
}
