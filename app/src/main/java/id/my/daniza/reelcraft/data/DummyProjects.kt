package id.my.daniza.reelcraft.data

import id.my.daniza.reelcraft.model.AspectRatio
import id.my.daniza.reelcraft.model.Clip
import id.my.daniza.reelcraft.model.MusicTrack
import id.my.daniza.reelcraft.model.Project

object DummyProjects {

    val projects: List<Project> = listOf(
        Project(
            id = "proj_1",
            name = "Summer Trip",
            durationUs = 45_000_000L,
            dateCreatedMs = 1718000000000L,
            dateModifiedMs = 1718100000000L,
            thumbnailPath = null,
            clips = listOf(
                Clip(id = "clip_1", sourcePath = "/dummy/beach.mp4", trimStartUs = 0L, trimEndUs = 15_000_000L, orderIndex = 0),
                Clip(id = "clip_2", sourcePath = "/dummy/sunset.mp4", trimStartUs = 0L, trimEndUs = 20_000_000L, orderIndex = 1),
                Clip(id = "clip_3", sourcePath = "/dummy/mountains.mp4", trimStartUs = 0L, trimEndUs = 10_000_000L, orderIndex = 2)
            ),
            aspectRatio = AspectRatio.SixteenNine,
            musicTrack = MusicTrack(
                sourcePath = "/dummy/summer_song.mp3",
                name = "Summer Vibes"
            )
        ),
        Project(
            id = "proj_2",
            name = "Birthday Edit",
            durationUs = 30_000_000L,
            dateCreatedMs = 1717500000000L,
            dateModifiedMs = 1717600000000L,
            thumbnailPath = null,
            clips = listOf(
                Clip(id = "clip_4", sourcePath = "/dummy/party.mp4", trimStartUs = 0L, trimEndUs = 30_000_000L, orderIndex = 0)
            ),
            aspectRatio = AspectRatio.NineSixteen
        ),
        Project(
            id = "proj_3",
            name = "Nature Reel",
            durationUs = 60_000_000L,
            dateCreatedMs = 1717000000000L,
            dateModifiedMs = 1717200000000L,
            thumbnailPath = null,
            clips = listOf(
                Clip(id = "clip_5", sourcePath = "/dummy/forest.mp4", trimStartUs = 0L, trimEndUs = 25_000_000L, orderIndex = 0),
                Clip(id = "clip_6", sourcePath = "/dummy/river.mp4", trimStartUs = 0L, trimEndUs = 20_000_000L, orderIndex = 1),
                Clip(id = "clip_7", sourcePath = "/dummy/waterfall.mp4", trimStartUs = 0L, trimEndUs = 15_000_000L, orderIndex = 2)
            ),
            aspectRatio = AspectRatio.SixteenNine
        ),
        Project(
            id = "proj_4",
            name = "TikTok Compilation",
            durationUs = 15_000_000L,
            dateCreatedMs = 1716500000000L,
            dateModifiedMs = 1716600000000L,
            thumbnailPath = null,
            clips = listOf(
                Clip(id = "clip_8", sourcePath = "/dummy/dance1.mp4", trimStartUs = 0L, trimEndUs = 5_000_000L, orderIndex = 0),
                Clip(id = "clip_9", sourcePath = "/dummy/dance2.mp4", trimStartUs = 0L, trimEndUs = 5_000_000L, orderIndex = 1),
                Clip(id = "clip_10", sourcePath = "/dummy/dance3.mp4", trimStartUs = 0L, trimEndUs = 5_000_000L, orderIndex = 2)
            ),
            aspectRatio = AspectRatio.NineSixteen
        ),
        Project(
            id = "proj_5",
            name = "Slow Mo Test",
            durationUs = 10_000_000L,
            dateCreatedMs = 1716000000000L,
            dateModifiedMs = 1716100000000L,
            thumbnailPath = null,
            clips = listOf(
                Clip(id = "clip_11", sourcePath = "/dummy/splash.mp4", trimStartUs = 0L, trimEndUs = 10_000_000L, speed = 0.25f, orderIndex = 0)
            ),
            aspectRatio = AspectRatio.SixteenNine
        ),
        Project(
            id = "proj_6",
            name = "Urban Exploration",
            durationUs = 35_000_000L,
            dateCreatedMs = 1715500000000L,
            dateModifiedMs = 1715700000000L,
            thumbnailPath = null,
            clips = listOf(
                Clip(id = "clip_12", sourcePath = "/dummy/cityscape.mp4", trimStartUs = 0L, trimEndUs = 20_000_000L, orderIndex = 0),
                Clip(id = "clip_13", sourcePath = "/dummy/street.mp4", trimStartUs = 0L, trimEndUs = 15_000_000L, orderIndex = 1)
            ),
            aspectRatio = AspectRatio.SixteenNine
        )
    )

    fun projectById(id: String): Project? = projects.find { it.id == id }
}
