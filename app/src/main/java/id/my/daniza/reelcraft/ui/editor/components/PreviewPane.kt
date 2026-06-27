package id.my.daniza.reelcraft.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import id.my.daniza.reelcraft.model.Project

@Composable
fun PreviewPane(
    project: Project,
    isDetailVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val previewHeight = if (isDetailVisible) 180.dp else 280.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(previewHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Movie,
            contentDescription = "Preview",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    }
}
