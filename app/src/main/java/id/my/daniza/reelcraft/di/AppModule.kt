package id.my.daniza.reelcraft.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import id.my.daniza.segment.SegmentEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSegmentEngine(): SegmentEngine {
        return SegmentEngine.getInstance()
    }
}
