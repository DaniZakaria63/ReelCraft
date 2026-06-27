package id.my.daniza.reelcraft.ui.navigation

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{projectId}"
    const val EXPORT = "export/{projectId}"

    fun editor(projectId: String) = "editor/$projectId"
    fun export(projectId: String) = "export/$projectId"
}
