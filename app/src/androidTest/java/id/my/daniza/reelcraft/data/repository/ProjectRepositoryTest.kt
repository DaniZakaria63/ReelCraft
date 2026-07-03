package id.my.daniza.reelcraft.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.my.daniza.local.db.AppDatabase
import id.my.daniza.reelcraft.data.json.ProjectJsonManager
import id.my.daniza.reelcraft.data.json.ProjectJsonSerializer
import id.my.daniza.reelcraft.model.AspectRatio
import id.my.daniza.reelcraft.model.Clip
import id.my.daniza.reelcraft.model.Project
import id.my.daniza.reelcraft.model.TimelineState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var jsonManager: ProjectJsonManager
    private lateinit var jsonSerializer: ProjectJsonSerializer
    private lateinit var repository: ProjectRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        jsonManager = ProjectJsonManager(context)
        jsonSerializer = ProjectJsonSerializer()
        repository = ProjectRepository(db.projectDao(), jsonSerializer, jsonManager)
    }

    @After
    fun tearDown() {
        db.close()
        jsonManager.projectsDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun createAndLoadProject() = runBlocking {
        val projectId = repository.createProject("Test Project", AspectRatio.SixteenNine)
        assertNotNull(projectId)

        val entities = repository.observeProjects().first()
        assertEquals(1, entities.size)
        assertEquals("Test Project", entities[0].name)

        val result = repository.loadFullProject(projectId)
        assertNotNull(result)
        assertEquals("Test Project", result!!.project.name)
        assertEquals(AspectRatio.SixteenNine, result.project.aspectRatio)
    }

    @Test
    fun saveAndReloadFullProjectWithClips() = runBlocking {
        val projectId = repository.createProject("Full Project", AspectRatio.FourThree)

        val clip = Clip(
            id = "clip1",
            sourcePath = "/tmp/video.mp4",
            trimStartUs = 1000L,
            trimEndUs = 5000000L,
            speed = 2.0f,
            orderIndex = 0
        )
        val project = Project(
            id = projectId,
            name = "Full Project Updated",
            durationUs = 10_000_000L,
            clips = listOf(clip),
            aspectRatio = AspectRatio.FourThree,
            volume = 0.7f
        )
        val timelineState = TimelineState(
            currentPositionUs = 5_000_000L,
            isPlaying = false,
            zoomLevel = 2.0f
        )

        repository.saveFullProject(project, timelineState)

        val result = repository.loadFullProject(projectId)
        assertNotNull(result)
        assertEquals("Full Project Updated", result!!.project.name)
        assertEquals(1, result.project.clips.size)
        assertEquals("clip1", result.project.clips[0].id)
        assertEquals(2.0f, result.project.clips[0].speed)
        assertEquals(0.7f, result.project.volume)
        assertNotNull(result.timelineState)
        assertEquals(5_000_000L, result.timelineState!!.currentPositionUs)
        assertEquals(2.0f, result.timelineState.zoomLevel)
    }

    @Test
    fun deleteProject() = runBlocking {
        val projectId = repository.createProject("To Delete", AspectRatio.OneOne)
        assertTrue(repository.observeProjects().first().isNotEmpty())

        repository.deleteProject(projectId)

        val entities = repository.observeProjects().first()
        assertEquals(0, entities.size)
        assertNull(repository.loadFullProject(projectId))
    }

    @Test
    fun renameProject() = runBlocking {
        val projectId = repository.createProject("Original", AspectRatio.SixteenNine)
        repository.renameProject(projectId, "Renamed Project")

        val result = repository.loadFullProject(projectId)
        assertNotNull(result)
        assertEquals("Renamed Project", result!!.project.name)

        val entity = repository.observeProjects().first().first()
        assertEquals("Renamed Project", entity.name)
    }

    @Test
    fun observeProjectsReturnsOrderedByDateModified() = runBlocking {
        repository.createProject("Older", AspectRatio.SixteenNine)
        Thread.sleep(10)
        repository.createProject("Newer", AspectRatio.FourThree)

        val entities = repository.observeProjects().first()
        assertEquals(2, entities.size)
        assertTrue(entities[0].dateModifiedMs >= entities[1].dateModifiedMs)
    }

    @Test
    fun createProjectUniquely() = runBlocking {
        val id1 = repository.createProject("P1", AspectRatio.SixteenNine)
        val id2 = repository.createProject("P2", AspectRatio.FourThree)
        assertTrue(id1 != id2)

        val entities = repository.observeProjects().first()
        assertEquals(2, entities.size)
    }

    @Test
    fun loadFullProjectReturnsNullForNonexistent() = runBlocking {
        assertNull(repository.loadFullProject("nonexistent-id"))
    }
}
