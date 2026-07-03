package id.my.daniza.reelcraft.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.my.daniza.local.dao.ProjectDao
import id.my.daniza.local.db.AppDatabase
import id.my.daniza.reelcraft.data.json.ProjectJsonManager
import id.my.daniza.reelcraft.data.json.ProjectJsonSerializer
import id.my.daniza.reelcraft.data.repository.ProjectRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "reelcraft.db"
    ).build()

    @Provides
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    @Singleton
    fun provideProjectRepository(
        projectDao: ProjectDao,
        jsonSerializer: ProjectJsonSerializer,
        jsonManager: ProjectJsonManager
    ): ProjectRepository = ProjectRepository(projectDao, jsonSerializer, jsonManager)
}
