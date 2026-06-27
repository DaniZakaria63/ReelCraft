package id.my.daniza.reelcraft.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TransitEnterexit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.my.daniza.reelcraft.ui.editor.EditorTool

data class ToolItem(
    val tool: EditorTool,
    val icon: ImageVector,
    val label: String
)

private val tools = listOf(
    ToolItem(EditorTool.SELECT, Icons.Default.TouchApp, "Select"),
    ToolItem(EditorTool.TRIM, Icons.Default.ContentCut, "Trim"),
    ToolItem(EditorTool.SPLIT, Icons.Default.Layers, "Split"),
    ToolItem(EditorTool.EFFECTS, Icons.Default.Palette, "Effects"),
    ToolItem(EditorTool.TEXT, Icons.Default.TextFields, "Text"),
    ToolItem(EditorTool.AUDIO, Icons.Default.MusicNote, "Audio"),
    ToolItem(EditorTool.SPEED, Icons.Default.Speed, "Speed"),
    ToolItem(EditorTool.TRANSITIONS, Icons.Default.TransitEnterexit, "Trans"),
)

@Composable
fun EditorToolbar(
    currentTool: EditorTool,
    onToolSelected: (EditorTool) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .height(64.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(tools) { item ->
            val isSelected = currentTool == item.tool
            val contentColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant

            Column(
                modifier = Modifier
                    .size(width = 52.dp, height = 60.dp)
                    .clickable { onToolSelected(item.tool) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor
                )
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}
