package id.my.daniza.reelcraft.data.json

import id.my.daniza.reelcraft.model.AppliedEffect
import id.my.daniza.reelcraft.model.AspectRatio
import id.my.daniza.reelcraft.model.Clip
import id.my.daniza.reelcraft.model.Interpolation
import id.my.daniza.reelcraft.model.Keyframe
import id.my.daniza.reelcraft.model.MaskType
import id.my.daniza.reelcraft.model.MusicTrack
import id.my.daniza.reelcraft.model.Project
import id.my.daniza.reelcraft.model.TextOverlay
import id.my.daniza.reelcraft.model.TimelineState
import id.my.daniza.reelcraft.model.Transition
import id.my.daniza.reelcraft.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectJsonSerializerTest {

    private val serializer = ProjectJsonSerializer()

    @Test
    fun `round-trip minimal project`() {
        val project = Project(
            id = "p1",
            name = "Test",
            aspectRatio = AspectRatio.SixteenNine
        )
        val json = serializer.serialize(project, null)
        val result = serializer.deserialize(json)

        assertEquals("p1", result.project.id)
        assertEquals("Test", result.project.name)
        assertEquals(AspectRatio.SixteenNine, result.project.aspectRatio)
        assertNull(result.timelineState)
    }

    @Test
    fun `round-trip full project with everything`() {
        val keyframe = Keyframe(
            positionUs = 1_000_000L,
            intensity = 0.5f,
            interpolation = Interpolation.EaseInOut
        )
        val effect = AppliedEffect(
            id = "eff1",
            presetId = "preset_vignette",
            maskType = MaskType.Foreground,
            intensity = 0.8f,
            enabled = true,
            keyframes = listOf(keyframe),
            params = floatArrayOf(0.1f, 0.2f, 0.3f)
        )
        val textOverlay = TextOverlay(
            id = "txt1",
            text = "Hello World",
            fontName = "Roboto",
            fontSize = 48,
            colorArgb = 0xFFFF0000,
            positionX = 0.2f,
            positionY = 0.3f,
            rotationDeg = 15f,
            startOffsetUs = 500_000L,
            endOffsetUs = 2_000_000L
        )
        val transition = Transition(
            type = TransitionType.Crossfade,
            durationUs = 500_000L
        )
        val clip = Clip(
            id = "clip1",
            sourcePath = "/tmp/video.mp4",
            trimStartUs = 0L,
            trimEndUs = 5_000_000L,
            speed = 1.5f,
            volume = 0.7f,
            orderIndex = 0,
            effects = listOf(effect),
            textOverlays = listOf(textOverlay),
            transitionOut = transition
        )
        val musicTrack = MusicTrack(
            sourcePath = "/tmp/music.mp3",
            name = "Upbeat",
            volume = 0.4f,
            trimStartUs = 100_000L,
            trimEndUs = 10_000_000L
        )
        val timelineState = TimelineState(
            currentPositionUs = 2_500_000L,
            isPlaying = true,
            durationUs = 10_000_000L,
            zoomLevel = 1.5f,
            selectedClipId = "clip1",
            selectedEffectId = "eff1"
        )
        val project = Project(
            id = "p2",
            name = "Full Project",
            thumbnailPath = "/cache/thumb.png",
            durationUs = 10_000_000L,
            dateCreatedMs = 1000000L,
            dateModifiedMs = 2000000L,
            clips = listOf(clip),
            musicTrack = musicTrack,
            aspectRatio = AspectRatio.FourThree,
            volume = 0.8f
        )

        val json = serializer.serialize(project, timelineState)
        val result = serializer.deserialize(json)
        val deserialized = result.project

        assertEquals("p2", deserialized.id)
        assertEquals("Full Project", deserialized.name)
        assertEquals("/cache/thumb.png", deserialized.thumbnailPath)
        assertEquals(10_000_000L, deserialized.durationUs)
        assertEquals(1000000L, deserialized.dateCreatedMs)
        assertEquals(2000000L, deserialized.dateModifiedMs)
        assertEquals(AspectRatio.FourThree, deserialized.aspectRatio)
        assertEquals(0.8f, deserialized.volume)
        assertEquals(1, deserialized.clips.size)

        val deserializedClip = deserialized.clips.first()
        assertEquals("clip1", deserializedClip.id)
        assertEquals("/tmp/video.mp4", deserializedClip.sourcePath)
        assertEquals(0L, deserializedClip.trimStartUs)
        assertEquals(5_000_000L, deserializedClip.trimEndUs)
        assertEquals(1.5f, deserializedClip.speed)
        assertEquals(0.7f, deserializedClip.volume)
        assertEquals(0, deserializedClip.orderIndex)
        assertEquals(1, deserializedClip.effects.size)
        assertEquals(1, deserializedClip.textOverlays.size)

        val deserializedEffect = deserializedClip.effects.first()
        assertEquals("eff1", deserializedEffect.id)
        assertEquals("preset_vignette", deserializedEffect.presetId)
        assertEquals(MaskType.Foreground, deserializedEffect.maskType)
        assertEquals(0.8f, deserializedEffect.intensity)
        assertTrue(deserializedEffect.enabled)
        assertEquals(1, deserializedEffect.keyframes.size)
        assertNotNull(deserializedEffect.params)
        assertEquals(3, deserializedEffect.params!!.size)
        assertEquals(0.1f, deserializedEffect.params!![0])
        assertEquals(0.2f, deserializedEffect.params!![1])
        assertEquals(0.3f, deserializedEffect.params!![2])

        val deserializedKeyframe = deserializedEffect.keyframes.first()
        assertEquals(1_000_000L, deserializedKeyframe.positionUs)
        assertEquals(0.5f, deserializedKeyframe.intensity)
        assertEquals(Interpolation.EaseInOut, deserializedKeyframe.interpolation)

        val deserializedText = deserializedClip.textOverlays.first()
        assertEquals("txt1", deserializedText.id)
        assertEquals("Hello World", deserializedText.text)
        assertEquals("Roboto", deserializedText.fontName)
        assertEquals(48, deserializedText.fontSize)
        assertEquals(0xFFFF0000, deserializedText.colorArgb)
        assertEquals(0.2f, deserializedText.positionX)
        assertEquals(0.3f, deserializedText.positionY)
        assertEquals(15f, deserializedText.rotationDeg)
        assertEquals(500_000L, deserializedText.startOffsetUs)
        assertEquals(2_000_000L, deserializedText.endOffsetUs)

        val deserializedTransition = requireNotNull(deserializedClip.transitionOut)
        assertEquals(TransitionType.Crossfade, deserializedTransition.type)
        assertEquals(500_000L, deserializedTransition.durationUs)

        val deserializedMusic = requireNotNull(deserialized.musicTrack)
        assertEquals("/tmp/music.mp3", deserializedMusic.sourcePath)
        assertEquals("Upbeat", deserializedMusic.name)
        assertEquals(0.4f, deserializedMusic.volume)
        assertEquals(100_000L, deserializedMusic.trimStartUs)
        assertEquals(10_000_000L, deserializedMusic.trimEndUs)

        assertNotNull(result.timelineState)
        val deserializedTimeline = result.timelineState!!
        assertEquals(2_500_000L, deserializedTimeline.currentPositionUs)
        assertTrue(deserializedTimeline.isPlaying)
        assertEquals(10_000_000L, deserializedTimeline.durationUs)
        assertEquals(1.5f, deserializedTimeline.zoomLevel)
        assertEquals("clip1", deserializedTimeline.selectedClipId)
        assertEquals("eff1", deserializedTimeline.selectedEffectId)
    }

    @Test
    fun `deserialize handles null fields gracefully`() {
        val json = """
        {
            "schemaVersion": 1,
            "id": "p3",
            "name": "Empty",
            "aspectRatio": "SixteenNine",
            "dateCreatedMs": 1000,
            "dateModifiedMs": 2000,
            "clips": [],
            "volume": 1.0
        }
        """.trimIndent()

        val result = serializer.deserialize(json)
        assertEquals("p3", result.project.id)
        assertEquals(0, result.project.clips.size)
        assertNull(result.project.musicTrack)
        assertNull(result.project.thumbnailPath)
        assertNull(result.timelineState)
    }

    @Test
    fun `deserialize handles unknown enum values`() {
        val json = """
        {
            "schemaVersion": 1,
            "id": "p4",
            "name": "Test",
            "aspectRatio": "UnknownValue",
            "dateCreatedMs": 1000,
            "dateModifiedMs": 2000,
            "clips": [],
            "volume": 1.0
        }
        """.trimIndent()

        val result = serializer.deserialize(json)
        assertEquals("p4", result.project.id)
        assertEquals(AspectRatio.SixteenNine, result.project.aspectRatio)
    }

    @Test
    fun `serialize includes version field`() {
        val project = Project(
            id = "p5",
            name = "Test",
            aspectRatio = AspectRatio.OneOne
        )
        val json = serializer.serialize(project, null)
        assertTrue(json.contains("\"schemaVersion\":1"))
    }
}
