package id.my.daniza.reelcraft.screen.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.my.daniza.reelcraft.model.AppliedEffect
import id.my.daniza.reelcraft.model.Clip
import id.my.daniza.reelcraft.model.MusicTrack
import id.my.daniza.reelcraft.model.Preset
import id.my.daniza.reelcraft.model.Presets
import id.my.daniza.reelcraft.model.TextOverlay
import id.my.daniza.reelcraft.model.Transition
import id.my.daniza.reelcraft.model.TransitionType

@Composable
fun PresetsPanel(
    onApplyPreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = Presets.all.groupBy { it.category }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        grouped.entries.forEach { entry ->
            val category = entry.key
            val presets = entry.value
            item(key = "header_${category.name}") {
                Text(
                    category.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            item(key = "grid_${category.name}") {
                val rows = presets.chunked(4)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                PresetItem(
                                    preset = preset,
                                    onClick = { onApplyPreset(preset.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(4 - rowPresets.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetItem(preset: Preset, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                preset.icon,
                contentDescription = preset.name,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            preset.name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (preset.tier >= 1) {
            Text(
                "AI",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun ClipPropertiesPanel(
    clip: Clip,
    onUpdateTrim: (Long, Long) -> Unit,
    onUpdateSpeed: (Float) -> Unit,
    onUpdateVolume: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Clip Properties",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        PropertySlider(
            label = "Speed",
            value = clip.speed,
            valueRange = 0.1f..4f,
            displayValue = "${"%.1f".format(clip.speed)}x",
            onValueChange = { onUpdateSpeed(it) }
        )

        PropertySlider(
            label = "Volume",
            value = clip.volume,
            valueRange = 0f..1f,
            displayValue = "${(clip.volume * 100).toInt()}%",
            onValueChange = { onUpdateVolume(it) }
        )
    }
}

@Composable
private fun PropertySlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(displayValue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
fun EffectsListPanel(
    clip: Clip,
    onAddEffect: () -> Unit,
    onRemoveEffect: (String) -> Unit,
    onSelectEffect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Effects",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(
                onClick = onAddEffect,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (clip.effects.isEmpty()) {
            Text(
                "No effects applied",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            clip.effects.forEach { effect ->
                EffectCard(
                    effect = effect,
                    onRemove = { onRemoveEffect(effect.id) },
                    onSelect = { onSelectEffect(effect.id) }
                )
            }
        }
    }
}

@Composable
private fun EffectCard(
    effect: AppliedEffect,
    onRemove: () -> Unit,
    onSelect: () -> Unit
) {
    val preset = Presets.byId(effect.presetId)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                preset?.icon ?: Icons.Default.Tune,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(preset?.name ?: effect.presetId, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${effect.maskType.label} - ${(effect.intensity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Remove", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun EffectDetailPanel(
    clip: Clip,
    effectId: String,
    onUpdateIntensity: (Float) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effect = clip.effects.find { it.id == effectId } ?: return
    val preset = Presets.byId(effect.presetId)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("< Back", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                preset?.name ?: "Effect",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        PropertySlider(
            label = "Intensity",
            value = effect.intensity,
            valueRange = 0f..1f,
            displayValue = "${(effect.intensity * 100).toInt()}%",
            onValueChange = { onUpdateIntensity(it) }
        )
    }
}

@Composable
fun TextEditorPanel(
    textOverlay: TextOverlay,
    clipId: String,
    onUpdate: (TextOverlay) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Text Overlay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Delete")
        }
    }
}

@Composable
fun MusicPickerPanel(
    currentTrack: MusicTrack?,
    onSetTrack: (MusicTrack?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Background Music", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        if (currentTrack != null) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(currentTrack.name, style = MaterialTheme.typography.titleSmall)
                        Text("${(currentTrack.volume * 100).toInt()}% volume", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onSetTrack(null) }) {
                        Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(18.dp))
                    }
                }
            }
        } else {
            Text(
                "No music track selected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
fun TransitionsPanel(
    currentTransition: Transition?,
    onUpdateTransition: (Transition) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Transition Out",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            "Type",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransitionType.entries.forEach { type ->
                val selected = currentTransition?.type == type
                Button(
                    onClick = {
                        onUpdateTransition(currentTransition?.copy(type = type) ?: Transition(type = type))
                    },
                    colors = if (selected) ButtonDefaults.buttonColors()
                    else ButtonDefaults.outlinedButtonColors(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(type.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        PropertySlider(
            label = "Duration",
            value = (currentTransition?.durationUs ?: 500_000L).toFloat() / 2_000_000f,
            valueRange = 0.1f..1f,
            displayValue = "${((currentTransition?.durationUs ?: 500_000L) / 1_000_000f)}s",
            onValueChange = { onUpdateTransition(currentTransition?.copy(durationUs = (it * 2_000_000L).toLong()) ?: Transition(durationUs = (it * 2_000_000L).toLong())) }
        )
    }
}
