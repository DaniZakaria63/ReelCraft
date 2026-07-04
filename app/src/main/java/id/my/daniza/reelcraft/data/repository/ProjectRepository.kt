package id.my.daniza.reelcraft.data.repository

import id.my.daniza.local.dao.ProjectDao
import id.my.daniza.local.db.entity.ProjectEntity
import id.my.daniza.reelcraft.data.json.ProjectJsonManager
import id.my.daniza.reelcraft.data.json.ProjectJsonSerializer
import id.my.daniza.reelcraft.model.AspectRatio
import id.my.daniza.reelcraft.model.Project
import id.my.daniza.reelcraft.model.TimelineState
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val jsonSerializer: ProjectJsonSerializer,
    private val jsonManager: ProjectJsonManager
) {

    fun observeProjects(): Flow<List<ProjectEntity>> =
        projectDao.getAllProjects()

    // only used by ProjectRepositoryTest
    suspend fun createProject(name: String, aspectRatio: AspectRatio): String {
        val projectId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val jsonPath = jsonManager.projectFilePath(projectId)

        val project = Project(
            id = projectId,
            name = name,
            aspectRatio = aspectRatio,
            dateCreatedMs = now,
            dateModifiedMs = now
        )
        jsonManager.saveProject(project, null, jsonSerializer)

        val entity = projectToEntity(project, jsonPath, now)
        projectDao.insertOrUpdate(entity)
        return projectId
    }

    suspend fun deleteProject(projectId: String) {
        val entity = projectDao.getProjectById(projectId)
        projectDao.deleteProjectById(projectId)
        jsonManager.deleteProjectFile(projectId)
        entity?.thumbnailPath?.let { File(it).delete() }
    }

    suspend fun renameProject(projectId: String, newName: String) {
        val now = System.currentTimeMillis()
        projectDao.renameProject(projectId, newName, now)
        val entity = projectDao.getProjectById(projectId) ?: return
        val project = jsonManager.loadProject(entity.projectJsonPath, jsonSerializer) ?: return
        jsonManager.saveProject(project.copy(name = newName, dateModifiedMs = now), null, jsonSerializer)
    }

    suspend fun loadFullProject(projectId: String): LoadResult? {
        val entity = projectDao.getProjectById(projectId) ?: return null
        val result = jsonManager.loadProjectWithTimeline(entity.projectJsonPath, jsonSerializer) ?: return null
        return LoadResult(result.project, result.timelineState)
    }

    suspend fun saveFullProject(project: Project, timelineState: TimelineState? = null) {
        val now = System.currentTimeMillis()
        val jsonPath = jsonManager.projectFilePath(project.id)

        val saved = project.copy(dateModifiedMs = now)
        jsonManager.saveProject(saved, timelineState, jsonSerializer)

        val entity = projectToEntity(saved, jsonPath, now)
        projectDao.insertOrUpdate(entity)
    }

    data class LoadResult(
        val project: Project,
        val timelineState: TimelineState?
    )

    private fun projectToEntity(project: Project, jsonPath: String, now: Long) = ProjectEntity(
        id = project.id,
        name = project.name,
        thumbnailPath = project.thumbnailPath,
        durationUs = project.effectiveDurationUs,
        aspectRatio = project.aspectRatio.name,
        dateCreatedMs = project.dateCreatedMs,
        dateModifiedMs = now,
        projectJsonPath = jsonPath
    )
}
