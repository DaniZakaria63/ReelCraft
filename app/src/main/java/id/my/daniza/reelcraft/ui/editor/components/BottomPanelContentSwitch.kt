package id.my.daniza.reelcraft.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.my.daniza.reelcraft.model.Project
import id.my.daniza.reelcraft.ui.editor.BottomSheetContent

@Composable
fun BottomPanelContentSwitch(
    bottomSheetContent: BottomSheetContent?,
    project: Project,
    onAddEffect: (String) -> Unit,
    onRemoveEffect: (String) -> Unit,
    onUpdateEffectIntensity: (String, Float) -> Unit,
    onUpdateClipTrim: (String, Long, Long) -> Unit,
    onUpdateClipSpeed: (String, Float) -> Unit,
    onUpdateClipVolume: (String, Float) -> Unit,
    onUpdateTextOverlay: (String, id.my.daniza.reelcraft.model.TextOverlay) -> Unit,
    onDeleteTextOverlay: (String, String) -> Unit,
    onSetMusicTrack: (id.my.daniza.reelcraft.model.MusicTrack?) -> Unit,
    onSelectEffect: (String, String) -> Unit,
    onBackToEffects: (String) -> Unit,
    onDismiss: () -> Unit,
    onShowPresets: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = 400.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close panel", modifier = Modifier.padding(4.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when (val content = bottomSheetContent) {
                is BottomSheetContent.Presets -> {
                    PresetsPanel(onApplyPreset = onAddEffect)
                }
                is BottomSheetContent.ClipProperties -> {
                    val clips = project.clips.filter { it.id == content.clipId }
                    if (clips.isNotEmpty()) {
                        ClipPropertiesPanel(
                            clip = clips.first(),
                            onUpdateTrim = { start, end -> onUpdateClipTrim(content.clipId, start, end) },
                            onUpdateSpeed = { onUpdateClipSpeed(content.clipId, it) },
                            onUpdateVolume = { onUpdateClipVolume(content.clipId, it) }
                        )
                    }
                }
                is BottomSheetContent.Effects -> {
                    val clips = project.clips.filter { it.id == content.clipId }
                    if (clips.isNotEmpty()) {
                        if (content.effectId != null) {
                            EffectDetailPanel(
                                clip = clips.first(),
                                effectId = content.effectId,
                                onUpdateIntensity = { onUpdateEffectIntensity(content.effectId, it) },
                                onBack = { onBackToEffects(content.clipId) }
                            )
                        } else {
                            EffectsListPanel(
                                clip = clips.first(),
                                onAddEffect = onShowPresets,
                                onRemoveEffect = onRemoveEffect,
                                onSelectEffect = { onSelectEffect(content.clipId, it) }
                            )
                        }
                    }
                }
                is BottomSheetContent.TextEditor -> {
                    TextEditorPanel(
                        textOverlay = content.textOverlay,
                        clipId = content.clipId,
                        onUpdate = { onUpdateTextOverlay(content.clipId, it) },
                        onDelete = { onDeleteTextOverlay(content.clipId, content.textOverlay.id) }
                    )
                }
                is BottomSheetContent.Music -> {
                    MusicPickerPanel(
                        currentTrack = project.musicTrack,
                        onSetTrack = onSetMusicTrack
                    )
                }
                null -> {}
            }
        }
    }
}
