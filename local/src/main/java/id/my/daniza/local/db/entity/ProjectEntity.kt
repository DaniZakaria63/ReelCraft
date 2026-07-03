package id.my.daniza.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val thumbnailPath: String?,
    val durationUs: Long,
    val aspectRatio: String,
    val dateCreatedMs: Long,
    val dateModifiedMs: Long,
    val projectJsonPath: String
)
