package id.my.daniza.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.my.daniza.local.db.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY date_modified_ms DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("UPDATE projects SET date_modified_ms = :timestamp WHERE id = :projectId")
    suspend fun touchModifiedDate(projectId: String, timestamp: Long)

    @Query("UPDATE projects SET name = :name, date_modified_ms = :now WHERE id = :projectId")
    suspend fun renameProject(projectId: String, name: String, now: Long)

    @Query("UPDATE projects SET thumbnail_path = :path, date_modified_ms = :now WHERE id = :projectId")
    suspend fun updateThumbnailPath(projectId: String, path: String?, now: Long)

    @Query("UPDATE projects SET duration_us = :durationUs, aspect_ratio = :aspectRatio, date_modified_ms = :now WHERE id = :projectId")
    suspend fun updateCatalogFields(projectId: String, durationUs: Long, aspectRatio: String, now: Long)
}
