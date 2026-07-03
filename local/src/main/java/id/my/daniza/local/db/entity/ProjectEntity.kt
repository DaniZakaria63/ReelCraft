package id.my.daniza.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?,
    @ColumnInfo(name = "duration_us")
    val durationUs: Long,
    @ColumnInfo(name = "aspect_ratio")
    val aspectRatio: String,
    val dateCreatedMs: Long,
    @ColumnInfo(name = "date_modified_ms")
    val dateModifiedMs: Long,
    val projectJsonPath: String
)
