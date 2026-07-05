package id.my.daniza.reelcraft.engine

import android.content.Context
import id.my.daniza.ffmpeg.NativeFFmpeg
import id.my.daniza.reelcraft.model.Clip
import id.my.daniza.reelcraft.model.Project
import id.my.daniza.reelcraft.model.TransitionType
import id.my.daniza.reelcraft.viewmodel.ExportSettings
import id.my.daniza.segment.NativeSegment
import id.my.daniza.segment.SegmentEngine
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ExportProcessor {

    data class ExportResult(
        val success: Boolean,
        val outputPath: String? = null,
        val errorMessage: String? = null
    )

    fun export(
        context: Context,
        project: Project,
        settings: ExportSettings,
        outputFileName: String = "ReelCraft_${project.name.replace(" ", "_")}.mp4",
        onProgress: (Float) -> Unit = {}
    ): ExportResult {
        val outputFile = File(context.cacheDir, outputFileName)
        val w = settings.resolution.width
        val h = settings.resolution.height
        val fps = settings.frameRate
        val bitrate = settings.bitrateMbps * 1_000_000

        val frameSize = w * h * 4
        val frameBytes = ByteBuffer.allocateDirect(frameSize).order(ByteOrder.nativeOrder())
        val frameBytes2 = ByteBuffer.allocateDirect(frameSize).order(ByteOrder.nativeOrder())
        val maskBytes = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())

        val segEngine = SegmentEngine.getInstance()

        // Calculate total frames
        var totalFrames = 0L
        for (clip in project.clips) {
            val frameCount = (clip.effectiveDurationUs * fps / 1_000_000L).coerceAtLeast(1)
            totalFrames += frameCount
        }
        var processedFrames = 0L

        // Open encoder
        val encHandle = NativeFFmpeg.encoderOpen(
            outputFile.absolutePath, w, h, fps, 1, bitrate
        )
        if (encHandle == 0L) {
            return ExportResult(false, errorMessage = "Failed to open encoder")
        }

        try {
            for (clipIdx in project.clips.indices) {
                val clip = project.clips[clipIdx]
                val frameCount = (clip.effectiveDurationUs * fps / 1_000_000L).coerceAtLeast(1)

                // Open decoder
                val decHandle = NativeFFmpeg.decoderOpen(clip.sourcePath)
                if (decHandle == 0L) {
                    continue
                }

                try {
                    // Seek to trim start
                    NativeFFmpeg.decoderSeek(decHandle, clip.trimStartUs)

                    // Build filter chain
                    val filterDesc = PresetEngine.buildFilterString(clip.effects)
                    var filterHandle = 0L
                    if (filterDesc != null) {
                        filterHandle = NativeFFmpeg.filterGraphCreate(w, h, fps, filterDesc)
                    }

                    val textFilterDesc = PresetEngine.buildTextOverlayFilter(
                        clip.textOverlays, clip.effectiveDurationUs
                    )
                    var textFilterHandle = 0L
                    if (textFilterDesc != null) {
                        textFilterHandle = NativeFFmpeg.filterGraphCreate(w, h, fps, textFilterDesc)
                    }

                    try {
                        var frameRead = 0
                        // Process based on speed (frame sampling)
                        val speedMs = (clip.speed * 1_000_000f).toLong()
                        var positionUs = clip.trimStartUs

                        while (frameRead < frameCount && positionUs < clip.trimEndUs) {
                            frameBytes.clear()
                            if (!NativeFFmpeg.decoderReadFrame(decHandle, frameBytes)) {
                                positionUs += 33_333L // skip ~1 frame worth
                                continue
                            }

                            // Apply FFmpeg filter if present
                            if (filterHandle != 0L) {
                                frameBytes2.clear()
                                NativeFFmpeg.filterGraphProcess(
                                    filterHandle, frameBytes, w, h, frameBytes2, w, h
                                )
                                frameBytes2.rewind()
                                frameBytes.rewind()
                                frameBytes.put(frameBytes2)
                            }

                            // Apply segment effect if present
                            val segEffect = clip.effects.firstOrNull {
                                PresetEngine.isSegmentEffect(it.presetId)
                            }
                            if (segEffect != null && segEngine.segmentReady) {
                                frameBytes.rewind()
                                maskBytes.clear()
                                segEngine.segmentFrame(frameBytes, w, h, maskBytes)

                                frameBytes.rewind()
                                frameBytes2.clear()
                                maskBytes.rewind()

                                segEngine.applyEffect(
                                    PresetEngine.getSegmentEffectType(segEffect.presetId),
                                    frameBytes, w, h, maskBytes, frameBytes2,
                                )
                                frameBytes2.rewind()
                                frameBytes.rewind()
                                frameBytes.put(frameBytes2)
                            }

                            // Apply text overlay filter if present
                            if (textFilterHandle != 0L) {
                                frameBytes2.clear()
                                NativeFFmpeg.filterGraphProcess(
                                    textFilterHandle, frameBytes, w, h, frameBytes2, w, h
                                )
                                frameBytes2.rewind()
                                frameBytes.rewind()
                                frameBytes.put(frameBytes2)
                            }

                            // Encode frame
                            frameBytes.rewind()
                            NativeFFmpeg.encoderEncodeFrameRgba(encHandle, frameBytes)

                            frameRead++
                            processedFrames++
                            positionUs += speedMs
                            onProgress(processedFrames.toFloat() / totalFrames)
                        }

                        // Apply transition to next clip
                        if (clipIdx < project.clips.size - 1) {
                            val transition = clip.transitionOut
                            if (transition != null && transition.type != TransitionType.None) {
                                val nextClip = project.clips[clipIdx + 1]
                                val transitionFrames =
                                    (transition.durationUs * fps / 1_000_000L).coerceAtLeast(1)

                                val nextDecHandle = NativeFFmpeg.decoderOpen(nextClip.sourcePath)
                                if (nextDecHandle != 0L) {
                                    NativeFFmpeg.decoderSeek(nextDecHandle, nextClip.trimStartUs)
                                    val frameA = ByteBuffer.allocateDirect(frameSize).order(ByteOrder.nativeOrder())
                                    val frameB = ByteBuffer.allocateDirect(frameSize).order(ByteOrder.nativeOrder())
                                    val frameOut = ByteBuffer.allocateDirect(frameSize).order(ByteOrder.nativeOrder())

                                    for (t in 0 until transitionFrames) {
                                        val progress = t.toFloat() / transitionFrames

                                        frameA.clear(); frameB.clear()
                                        NativeFFmpeg.decoderReadFrame(decHandle, frameA)
                                        NativeFFmpeg.decoderReadFrame(nextDecHandle, frameB)
                                        frameOut.clear()

                                        when (transition.type) {
                                            TransitionType.Crossfade -> {
                                                NativeFFmpeg.compositeCrossfade(
                                                    frameA, frameB, frameOut, w, h, progress
                                                )
                                            }
                                            else -> {
                                                val dir = when (transition.type) {
                                                    TransitionType.WipeLeft -> 0
                                                    TransitionType.WipeRight -> 1
                                                    TransitionType.WipeUp -> 2
                                                    TransitionType.WipeDown -> 3
                                                    else -> 0
                                                }
                                                NativeFFmpeg.compositeWipe(
                                                    frameA, frameB, frameOut, w, h, progress, dir
                                                )
                                            }
                                        }

                                        frameOut.rewind()
                                        NativeFFmpeg.encoderEncodeFrameRgba(encHandle, frameOut)
                                        processedFrames++
                                        onProgress(processedFrames.toFloat() / totalFrames)
                                    }
                                    NativeFFmpeg.decoderClose(nextDecHandle)
                                }
                            }
                        }
                    } finally {
                        NativeFFmpeg.filterGraphClose(filterHandle)
                        NativeFFmpeg.filterGraphClose(textFilterHandle)
                    }
                } finally {
                    NativeFFmpeg.decoderClose(decHandle)
                }
            }

            NativeFFmpeg.encoderFinalize(encHandle)
            onProgress(1f)
            return ExportResult(true, outputPath = outputFile.absolutePath)

        } catch (e: Exception) {
            return ExportResult(false, errorMessage = e.message ?: "Unknown error")
        } finally {
            NativeFFmpeg.encoderClose(encHandle)
        }
    }
}
