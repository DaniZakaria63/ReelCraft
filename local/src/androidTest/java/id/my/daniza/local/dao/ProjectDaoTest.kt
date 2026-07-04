package id.my.daniza.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.my.daniza.local.db.AppDatabase
import id.my.daniza.local.db.entity.ProjectEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ProjectDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.projectDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndRetrieve() = runBlocking {
        val entity = ProjectEntity(
            id = "project1",
            name = "My Project",
            thumbnailPath = null,
            durationUs = 0L,
            aspectRatio = "SixteenNine",
            dateCreatedMs = 1000L,
            dateModifiedMs = 2000L,
            projectJsonPath = "/data/projects/project1.json"
        )
        dao.insertOrUpdate(entity)

        val retrieved = dao.getProjectById("project1")
        assertNotNull(retrieved)
        assertEquals("project1", retrieved!!.id)
        assertEquals("My Project", retrieved.name)
        assertEquals("SixteenNine", retrieved.aspectRatio)
    }

    @Test
    fun upsertReplacesExisting() = runBlocking {
        val entity = ProjectEntity(
            id = "project2", name = "Original",
            thumbnailPath = null, durationUs = 0L, aspectRatio = "SixteenNine",
            dateCreatedMs = 1000L, dateModifiedMs = 2000L,
            projectJsonPath = "/data/projects/project2.json"
        )
        dao.insertOrUpdate(entity)

        val updated = entity.copy(name = "Updated", dateModifiedMs = 3000L)
        dao.insertOrUpdate(updated)

        val retrieved = dao.getProjectById("project2")
        assertEquals("Updated", retrieved!!.name)
        assertEquals(3000L, retrieved.dateModifiedMs)
    }

    @Test
    fun deleteById() = runBlocking {
        val entity = ProjectEntity(
            id = "project3", name = "To Delete",
            thumbnailPath = null, durationUs = 0L, aspectRatio = "FourThree",
            dateCreatedMs = 1000L, dateModifiedMs = 2000L,
            projectJsonPath = "/data/projects/project3.json"
        )
        dao.insertOrUpdate(entity)
        dao.deleteProjectById("project3")

        assertNull(dao.getProjectById("project3"))
    }

    @Test
    fun getAllProjectsOrderedByDateModified() = runBlocking {
        val older = ProjectEntity(
            id = "older", name = "Older",
            thumbnailPath = null, durationUs = 0L, aspectRatio = "SixteenNine",
            dateCreatedMs = 1000L, dateModifiedMs = 1000L,
            projectJsonPath = "/data/projects/older.json"
        )
        val newer = ProjectEntity(
            id = "newer", name = "Newer",
            thumbnailPath = null, durationUs = 0L, aspectRatio = "SixteenNine",
            dateCreatedMs = 2000L, dateModifiedMs = 2000L,
            projectJsonPath = "/data/projects/newer.json"
        )
        dao.insertOrUpdate(older)
        dao.insertOrUpdate(newer)

        val projects = dao.getAllProjects().first()
        assertEquals(2, projects.size)
        assertEquals("newer", projects[0].id)
        assertEquals("older", projects[1].id)
    }

    @Test
    fun renameProject() = runBlocking {
        val entity = ProjectEntity(
            id = "project5", name = "Original Name",
            thumbnailPath = null, durationUs = 0L, aspectRatio = "SixteenNine",
            dateCreatedMs = 1000L, dateModifiedMs = 2000L,
            projectJsonPath = "/data/projects/project5.json"
        )
        dao.insertOrUpdate(entity)
        dao.renameProject("project5", "Renamed", 3000L)

        val retrieved = dao.getProjectById("project5")
        assertEquals("Renamed", retrieved!!.name)
        assertEquals(3000L, retrieved.dateModifiedMs)
    }

    @Test
    fun touchModifiedDate() = runBlocking {
        val entity = ProjectEntity(
            id = "project6", name = "Touch",
            thumbnailPath = null, durationUs = 0L, aspectRatio = "SixteenNine",
            dateCreatedMs = 1000L, dateModifiedMs = 2000L,
            projectJsonPath = "/data/projects/project6.json"
        )
        dao.insertOrUpdate(entity)
        dao.touchModifiedDate("project6", 9999L)

        val retrieved = dao.getProjectById("project6")
        assertEquals(9999L, retrieved!!.dateModifiedMs)
    }

    @Test
    fun updateThumbnailPath() = runBlocking {
        val entity = ProjectEntity(
            id = "project7", name = "Thumb",
            thumbnailPath = null, durationUs = 0L, aspectRatio = "SixteenNine",
            dateCreatedMs = 1000L, dateModifiedMs = 2000L,
            projectJsonPath = "/data/projects/project7.json"
        )
        dao.insertOrUpdate(entity)
        dao.updateThumbnailPath("project7", "/cache/thumb.png", 3000L)

        val retrieved = dao.getProjectById("project7")
        assertEquals("/cache/thumb.png", retrieved!!.thumbnailPath)
    }

    @Test
    fun updateCatalogFields() = runBlocking {
        val entity = ProjectEntity(
            id = "project8", name = "Catalog",
            thumbnailPath = null, durationUs = 0L, aspectRatio = "SixteenNine",
            dateCreatedMs = 1000L, dateModifiedMs = 2000L,
            projectJsonPath = "/data/projects/project8.json"
        )
        dao.insertOrUpdate(entity)
        dao.updateCatalogFields("project8", 5_000_000L, "OneOne", 3000L)

        val retrieved = dao.getProjectById("project8")
        assertEquals(5_000_000L, retrieved!!.durationUs)
        assertEquals("OneOne", retrieved.aspectRatio)
        assertEquals(3000L, retrieved.dateModifiedMs)
    }

    @Test
    fun deleteProjectEntity() = runBlocking {
        val entity = ProjectEntity(
            id = "project9", name = "Entity Delete",
            thumbnailPath = null, durationUs = 0L, aspectRatio = "SixteenNine",
            dateCreatedMs = 1000L, dateModifiedMs = 2000L,
            projectJsonPath = "/data/projects/project9.json"
        )
        dao.insertOrUpdate(entity)
        val inserted = dao.getProjectById("project9")
        assertNotNull(inserted)

        dao.deleteProject(inserted!!)
        assertNull(dao.getProjectById("project9"))
    }
}
