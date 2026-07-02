package id.my.daniza.reelcraft.screen.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlaybackBar(
    currentPositionUs: Long,
    durationUs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val positionSec = currentPositionUs / 1_000_000f
    val durationSec = if (durationUs > 0) durationUs / 1_000_000f else 1f

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Slider(
            value = positionSec,
            onValueChange = { onSeek((it * 1_000_000f).toLong().coerceAtMost(durationUs)) },
            valueRange = 0f..durationSec,
            modifier = Modifier.fillMaxWidth().height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline,
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(currentPositionUs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.width(52.dp)
            )

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onSeek((currentPositionUs - 5_000_000L).coerceAtLeast(0L)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.FastRewind, "Back 5s", modifier = Modifier.height(18.dp))
                }

                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.height(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { onSeek((currentPositionUs + 5_000_000L).coerceAtMost(durationUs)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.FastForward, "Forward 5s", modifier = Modifier.height(18.dp))
                }
            }

            Text(
                text = formatTime(durationUs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(52.dp)
            )
        }
    }
}

private fun formatTime(us: Long): String {
    val totalSeconds = us / 1_000_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (us / 10_000) % 100
    return "%d:%02d.%02d".format(minutes, seconds, millis)
}
