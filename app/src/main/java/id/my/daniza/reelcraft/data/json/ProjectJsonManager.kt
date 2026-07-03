package id.my.daniza.reelcraft.data.json

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import id.my.daniza.reelcraft.model.Project
import id.my.daniza.reelcraft.model.TimelineState
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectJsonManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val projectsDir: File
        get() = File(context.filesDir, "projects").also { it.mkdirs() }

    fun projectFilePath(projectId: String): String =
        File(projectsDir, "$projectId.json").absolutePath

    fun saveProject(
        project: Project,
        timelineState: TimelineState? = null,
        serializer: ProjectJsonSerializer
    ): String {
        val path = projectFilePath(project.id)
        val tmpFile = File("$path.tmp")
        val json = serializer.serialize(project, timelineState)
        tmpFile.writeText(json)
        tmpFile.renameTo(File(path))
        return path
    }

    fun loadProjectWithTimeline(
        jsonPath: String,
        serializer: ProjectJsonSerializer
    ): ProjectJsonSerializer.DeserializeResult? {
        val file = File(jsonPath)
        if (!file.exists()) return null
        val jsonString = file.readText()
        return serializer.deserialize(jsonString)
    }

    fun loadProject(jsonPath: String, serializer: ProjectJsonSerializer): Project? {
        val file = File(jsonPath)
        if (!file.exists()) return null
        return serializer.deserialize(file.readText()).project
    }

    fun deleteProjectFile(projectId: String): Boolean {
        val file = File(projectFilePath(projectId))
        return if (file.exists()) file.delete() else true
    }

    fun projectFileExists(projectId: String): Boolean =
        File(projectFilePath(projectId)).exists()
}
