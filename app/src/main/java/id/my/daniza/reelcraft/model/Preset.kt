package id.my.daniza.reelcraft.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

data class Preset(
    val id: String,
    val name: String,
    val description: String,
    val category: PresetCategory,
    val icon: ImageVector,
    val tier: Int
)

enum class PresetCategory(val label: String) {
    ColorGrade("Color Grade"),
    Artistic("Artistic"),
    Glitch("Glitch"),
    Portrait("Portrait"),
    Nature("Nature"),
    Filters("Filters")
}

object Presets {
    val all: List<Preset> = listOf(
        Preset("vintage", "Vintage", "Warm retro film look", PresetCategory.ColorGrade, Icons.Default.Palette, 0),
        Preset("noir", "Noir", "High-contrast black & white", PresetCategory.ColorGrade, Icons.Default.ColorLens, 0),
        Preset("teal_orange", "Blockbuster", "Teal & orange Hollywood look", PresetCategory.ColorGrade, Icons.Default.Palette, 0),
        Preset("pastel", "Pastel", "Soft pastel tones", PresetCategory.ColorGrade, Icons.Default.Palette, 0),
        Preset("pencil_sketch", "Pencil Sketch", "Hand-drawn pencil effect", PresetCategory.Artistic, Icons.Default.Brush, 0),
        Preset("pixel_art", "Pixel Art", "Retro pixelation effect", PresetCategory.Artistic, Icons.Default.GridOn, 0),
        Preset("watercolor", "Watercolor", "Soft watercolor paint look", PresetCategory.Artistic, Icons.Default.WaterDrop, 0),
        Preset("glitch", "Glitch", "Digital distortion glitch", PresetCategory.Glitch, Icons.Default.Bolt, 0),
        Preset("vhs", "VHS", "Old VHS tape degradation", PresetCategory.Glitch, Icons.Default.FlashOn, 0),
        Preset("van_gogh", "Van Gogh Portrait", "Impressionist brush strokes on person", PresetCategory.Portrait, Icons.Default.Person, 1),
        Preset("cyberpunk", "Cyberpunk", "Neon city colors on background", PresetCategory.Portrait, Icons.Default.Person, 1),
        Preset("silhouette", "Silhouette Glow", "Person as silhouette with glowing edge", PresetCategory.Portrait, Icons.Default.Brush, 1),
        Preset("ghost_trail", "Ghost Trail", "Motion trail effect on person", PresetCategory.Portrait, Icons.Default.FlashOn, 1),
        Preset("ink_splash", "Ink Splash", "Ink bleed effect on background", PresetCategory.Artistic, Icons.Default.WaterDrop, 1),
        Preset("nature_enhance", "Nature Boost", "Vibrant greens and blues", PresetCategory.Nature, Icons.Default.Nature, 0),
        Preset("neural_style", "Neural Style", "Deep learning style transfer", PresetCategory.Artistic, Icons.Default.Star, 2)
    )

    fun byId(id: String): Preset? = all.find { it.id == id }
}
