package id.my.daniza.reelcraft.ui.export

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: ExportViewModel = viewModel()
) {
    viewModel.loadProject(projectId)
    val project = viewModel.project

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (project != null) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${formatDuration(project.durationUs)} - ${project.clips.size} clip(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                if (!viewModel.isExporting && !viewModel.isComplete) {
                    ExportSettingsSection(settings = viewModel.settings, viewModel = viewModel)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.startExport() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Export Video", style = MaterialTheme.typography.titleMedium)
                    }
                }

                if (viewModel.isExporting) {
                    ExportProgressSection(
                        progress = viewModel.progress,
                        onCancel = { viewModel.cancelExport() }
                    )
                }

                if (viewModel.isComplete) {
                    ExportCompleteSection(onDone = onNavigateHome)
                }
            }
        }
    }
}

@Composable
private fun ExportSettingsSection(settings: ExportSettings, viewModel: ExportViewModel) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("Resolution", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportResolution.entries.forEach { res ->
                    FilterChip(
                        selected = settings.resolution == res,
                        onClick = { viewModel.updateResolution(res) },
                        label = { Text(res.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
            Text("Frame Rate", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(24, 30, 60).forEach { fps ->
                    FilterChip(
                        selected = settings.frameRate == fps,
                        onClick = { viewModel.updateFrameRate(fps) },
                        label = { Text("$fps fps", style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
            Text("Bitrate", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 40).forEach { b ->
                    FilterChip(
                        selected = settings.bitrateMbps == b,
                        onClick = { viewModel.updateBitrate(b) },
                        label = { Text("${b} Mbps", style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportProgressSection(progress: Float, onCancel: () -> Unit) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "exportProgress")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Exporting...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ExportCompleteSection(onDone: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.height(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text("Export Complete!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDone) {
                Text("Back to Projects")
            }
        }
    }
}

private fun formatDuration(us: Long): String {
    val totalSeconds = us / 1_000_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
