package id.my.daniza.reelcraft.ui.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.my.daniza.reelcraft.model.Clip
import id.my.daniza.reelcraft.ui.theme.DarkClipBar
import id.my.daniza.reelcraft.ui.theme.DarkPlayhead
import id.my.daniza.reelcraft.ui.theme.DarkTimelineBg
import id.my.daniza.reelcraft.ui.theme.DarkTimelineRuler

private val RULER_HEIGHT_DP = 24.dp
private val CLIP_HEIGHT_DP = 36.dp
private val TRACK_PADDING_DP = 4.dp
private val MIN_CLIP_WIDTH_DP = 80.dp
private const val TIME_MARKER_INTERVAL = 1_000_000L

@Composable
fun TimelineView(
    clips: List<Clip>,
    currentPositionUs: Long,
    zoomLevel: Float,
    selectedClipId: String?,
    durationUs: Long,
    onSeek: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeMeasurer = rememberTextMeasurer()
    val trackCount = clips.size

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onZoomOut, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ZoomOut, "Zoom Out", modifier = Modifier.size(16.dp))
            }
            Text(
                "${"%.1f".format(zoomLevel)}x",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onZoomIn, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ZoomIn, "Zoom In", modifier = Modifier.size(16.dp))
            }
        }

        val totalHeightDp = RULER_HEIGHT_DP + (CLIP_HEIGHT_DP + TRACK_PADDING_DP) * trackCount + 8.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeightDp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkTimelineBg)
                .pointerInput(durationUs, zoomLevel) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val pixelsPerSecond = zoomLevel * 10f
                        val newPos = ((change.position.x - 8) / pixelsPerSecond * 1_000_000f).toLong()
                            .coerceIn(0L, maxOf(durationUs, 1L))
                        onSeek(newPos)
                    }
                }
                .clickable { }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeightDp)
            ) {
                val rulerH = RULER_HEIGHT_DP.toPx()
                val clipH = CLIP_HEIGHT_DP.toPx()
                val pad = TRACK_PADDING_DP.toPx()
                val pixelsPerSecond = zoomLevel * 10f
                val startX = 8f
                val totalW = size.width

                drawRuler(timeMeasurer, durationUs, pixelsPerSecond, startX, totalW, rulerH)
                drawClips(clips, pixelsPerSecond, startX, rulerH, clipH, pad, selectedClipId)
                drawPlayhead(currentPositionUs, pixelsPerSecond, startX, size.height)
            }
        }
    }
}

private fun DrawScope.drawRuler(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    durationUs: Long,
    pixelsPerSecond: Float,
    startX: Float,
    totalWidth: Float,
    rulerH: Float
) {
    drawRect(DarkTimelineRuler, Offset(startX - 4, 0f), Size(totalWidth, rulerH))
    var t = 0L
    while (t <= durationUs) {
        val x = startX + (t / 1_000_000f) * pixelsPerSecond
        if (x in startX..(startX + totalWidth)) {
            val seconds = t / 1_000_000
            val label = "%d:%02d".format(seconds / 60, seconds % 60)
            drawLine(
                Color.White.copy(alpha = 0.3f),
                Offset(x, rulerH - 8f),
                Offset(x, rulerH),
                strokeWidth = 1f
            )
            val result = textMeasurer.measure(
                label,
                TextStyle(color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
            )
            drawText(result, topLeft = Offset(x + 2f, 2f))
        }
        t += TIME_MARKER_INTERVAL
    }
    drawLine(Color.White.copy(alpha = 0.1f), Offset(startX - 4, rulerH), Offset(totalWidth, rulerH))
}

private fun DrawScope.drawClips(
    clips: List<Clip>,
    pixelsPerSecond: Float,
    startX: Float,
    rulerH: Float,
    clipH: Float,
    pad: Float,
    selectedClipId: String?
) {
    clips.forEachIndexed { index, clip ->
        val y = rulerH + pad + index * (clipH + pad)
        val clipStartX = startX + (clip.trimStartUs / 1_000_000f) * pixelsPerSecond
        val clipWidth = maxOf(
            (clip.durationUs / 1_000_000f) * pixelsPerSecond,
            MIN_CLIP_WIDTH_DP.toPx()
        )
        val isSelected = clip.id == selectedClipId
        val clipColor = if (isSelected) DarkClipBar.copy(alpha = 1f) else DarkClipBar
        val borderColor = if (isSelected) Color.White else Color.Transparent

        drawRoundRect(clipColor, Offset(clipStartX, y), Size(clipWidth, clipH), CornerRadius(4f, 4f))
        if (isSelected) {
            drawRoundRect(borderColor, Offset(clipStartX, y), Size(clipWidth, clipH), CornerRadius(4f, 4f), style = Stroke(1.5f))
        }
    }
}

private fun DrawScope.drawPlayhead(
    currentPositionUs: Long,
    pixelsPerSecond: Float,
    startX: Float,
    totalHeight: Float
) {
    val playheadX = startX + (currentPositionUs / 1_000_000f) * pixelsPerSecond
    drawLine(DarkPlayhead, Offset(playheadX, 0f), Offset(playheadX, totalHeight), strokeWidth = 2f)
    val triPath = Path().apply {
        moveTo(playheadX, 0f)
        lineTo(playheadX - 5f, 8f)
        lineTo(playheadX + 5f, 8f)
        close()
    }
    drawPath(triPath, DarkPlayhead)
}
