package id.my.daniza.reelcraft.screen.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import id.my.daniza.reelcraft.screen.editor.components.BottomPanelContentSwitch
import id.my.daniza.reelcraft.screen.editor.components.EditorToolbar
import id.my.daniza.reelcraft.screen.editor.components.PlaybackBar
import id.my.daniza.reelcraft.screen.editor.components.PreviewPane
import id.my.daniza.reelcraft.screen.editor.components.TimelineView
import id.my.daniza.reelcraft.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToExport: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    val project = viewModel.project ?: return
    val timelineState = viewModel.timelineState

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        androidx.compose.material3.Text(
                            project.name,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (timelineState.isPlaying) {
                            androidx.compose.material3.Text(
                                "Playing",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = viewModel.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.padding(4.dp))
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = viewModel.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.padding(4.dp))
                    }
                    IconButton(onClick = onNavigateToExport) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            EditorToolbar(
                currentTool = viewModel.currentTool,
                onToolSelected = { viewModel.selectTool(it) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PreviewPane(
                previewBitmap = viewModel.previewBitmap,
                isDetailVisible = viewModel.bottomSheetContent != null,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            PlaybackBar(
                currentPositionUs = timelineState.currentPositionUs,
                durationUs = timelineState.durationUs,
                isPlaying = timelineState.isPlaying,
                onSeek = { viewModel.seekTo(it) },
                onTogglePlay = { viewModel.togglePlayback() },
                modifier = Modifier.padding(vertical = 2.dp)
            )

            AnimatedVisibility(
                visible = viewModel.bottomSheetContent != null,
                enter = expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                BottomPanelContentSwitch(
                    bottomSheetContent = viewModel.bottomSheetContent,
                    project = project,
                    onAddEffect = { presetId -> viewModel.addEffect(presetId) },
                    onRemoveEffect = { effectId -> viewModel.removeEffect(effectId) },
                    onUpdateEffectIntensity = { effectId, intensity -> viewModel.updateEffectIntensity(effectId, intensity) },
                    onUpdateClipTrim = { clipId, start, end -> viewModel.updateClipTrim(clipId, start, end) },
                    onUpdateClipSpeed = { clipId, speed -> viewModel.updateClipSpeed(clipId, speed) },
                    onUpdateClipVolume = { clipId, volume -> viewModel.updateClipVolume(clipId, volume) },
                    onUpdateTextOverlay = { clipId, overlay -> viewModel.updateTextOverlay(clipId, overlay) },
                    onDeleteTextOverlay = { clipId, overlayId -> viewModel.removeTextOverlay(clipId, overlayId) },
                    onSetMusicTrack = { viewModel.setMusicTrack(it) },
                    onSelectEffect = { clipId, effectId -> viewModel.showEffectDetail(clipId, effectId) },
                    onBackToEffects = { clipId -> viewModel.showEffectList(clipId) },
                    onDismiss = { viewModel.hideBottomSheet() },
                    onShowPresets = { viewModel.showPresets() },
                    onUpdateTransition = { clipId, transition -> viewModel.updateClipTransition(clipId, transition) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                TimelineView(
                    clips = project.clips,
                    currentPositionUs = timelineState.currentPositionUs,
                    zoomLevel = timelineState.zoomLevel,
                    selectedClipId = timelineState.selectedClipId,
                    durationUs = timelineState.durationUs,
                    onSeek = { viewModel.seekTo(it) },
                    onSelectClip = { viewModel.selectClip(it) },
                    onZoomIn = { viewModel.setZoom(timelineState.zoomLevel * 1.5f) },
                    onZoomOut = { viewModel.setZoom(timelineState.zoomLevel / 1.5f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}
