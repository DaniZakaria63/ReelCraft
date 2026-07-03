package id.my.daniza.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import id.my.daniza.local.dao.ProjectDao
import id.my.daniza.local.db.entity.ProjectEntity

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
