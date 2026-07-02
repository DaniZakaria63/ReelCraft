package id.my.daniza.reelcraft.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

enum class PresetCategory(val label: String) {
    ColorGrade("Color Grade"),
    Film("Film & Vintage"),
    Artistic("Artistic"),
    Glitch("Glitch"),
    Portrait("Portrait"),
    Nature("Nature"),
    Aesthetic("Aesthetic"),
    Filters("Filters")
}

data class Preset(
    val id: String,
    val name: String,
    val description: String,
    val category: PresetCategory,
    val icon: ImageVector,
    val tier: Int,
    val filterDesc: String = ""
)

object Presets {
    val all: List<Preset> = listOf(
        // ─── Color Grade (Instagram-style, Tier 0) ─────────────────────
        Preset("clarendon", "Clarendon", "Cool whites, bright midtones, increased contrast",
            PresetCategory.ColorGrade, Icons.Default.PhotoFilter, 0,
            "eq=contrast=1.15:brightness=0.05:saturation=1.1,colorbalance=rs=-0.08:gs=-0.03:bs=0.04"),
        Preset("gingham", "Gingham", "Muted colors with soft vintage haze",
            PresetCategory.ColorGrade, Icons.Default.PhotoFilter, 0,
            "curves=m='0/0 0.1/0.06 0.5/0.46 0.9/0.86 1/1',eq=saturation=0.65"),
        Preset("juno", "Juno", "Warm tones with intensified reds and yellows",
            PresetCategory.ColorGrade, Icons.Default.PhotoFilter, 0,
            "eq=contrast=1.25:brightness=0.02:saturation=1.25,colorbalance=rh=0.05:yh=0.04"),
        Preset("lark", "Lark", "Bright washed-out look with cool greens and blues",
            PresetCategory.ColorGrade, Icons.Default.PhotoFilter, 0,
            "eq=brightness=0.07:saturation=1.15,colorbalance=gs=0.08:bs=0.07"),
        Preset("valencia", "Valencia", "Warm faded tones, soft and dreamy",
            PresetCategory.ColorGrade, Icons.Default.PhotoFilter, 0,
            "curves=m='0/0.04 0.5/0.48 1/0.96',colorbalance=rh=0.04:gh=-0.03"),
        Preset("ludwig", "Ludwig", "High contrast, desaturated, crisp details",
            PresetCategory.ColorGrade, Icons.Default.PhotoFilter, 0,
            "eq=contrast=1.4:saturation=0.5:brightness=-0.02"),
        Preset("aden", "Aden", "Soft pink-warm tint with low contrast",
            PresetCategory.ColorGrade, Icons.Default.PhotoFilter, 0,
            "colorbalance=rh=0.07:gh=-0.02:bh=-0.02,eq=contrast=0.85:saturation=0.8"),
        Preset("perpetua", "Perpetua", "Cool bright tones with soft finish",
            PresetCategory.ColorGrade, Icons.Default.PhotoFilter, 0,
            "eq=brightness=0.05:contrast=0.9,colorbalance=rs=-0.04:gs=0.02:bs=0.07"),
        Preset("lofi", "Lo-fi", "High saturation and contrast with warm cast",
            PresetCategory.ColorGrade, Icons.Default.PhotoFilter, 0,
            "eq=contrast=1.35:saturation=1.4:brightness=0.02,colorbalance=rh=0.05"),
        Preset("xpro2", "X-Pro II", "High contrast with subtle vignette, warm tones",
            PresetCategory.ColorGrade, Icons.Default.PhotoFilter, 0,
            "eq=contrast=1.25:saturation=1.1,vignette=PI/4:eval=frame"),
        Preset("slumber", "Slumber", "Desaturated cool moody tones",
            PresetCategory.ColorGrade, Icons.Default.PhotoFilter, 0,
            "eq=saturation=0.4:brightness=-0.04:contrast=0.9,colorbalance=bs=0.08"),
        Preset("moon", "Moon", "Deep shadows with desaturated monochrome feel",
            PresetCategory.ColorGrade, Icons.Default.PhotoFilter, 0,
            "eq=contrast=1.5:brightness=-0.07:saturation=0.3"),
        Preset("teal_orange", "Blockbuster", "Teal shadows, orange highlights — Hollywood look",
            PresetCategory.ColorGrade, Icons.Default.Palette, 0,
            "eq=contrast=1.2:saturation=1.1,colorbalance=rs=-0.1:gs=-0.05:bs=0.2:rh=0.1:gh=-0.05:bh=-0.15"),
        Preset("pastel", "Pastel", "Soft pastel tones, gentle and airy",
            PresetCategory.ColorGrade, Icons.Default.Palette, 0,
            "eq=contrast=0.85:brightness=0.08:saturation=0.6,curves=m='0/0.05 1/0.95'"),

        // ─── Film & Vintage (Tier 0) ────────────────────────────────────
        Preset("vintage", "Vintage", "Warm retro film look with faded colors",
            PresetCategory.Film, Icons.Default.Palette, 0,
            "curves=r='0/0 0.1/0.07 1/1':g='0/0 0.1/0.05 1/1':b='0/0 0.1/0.03 1/1',eq=saturation=0.6"),
        Preset("sepia", "Sepia", "Classic brown-toned vintage photograph look",
            PresetCategory.Film, Icons.Default.Palette, 0,
            "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131"),
        Preset("noir", "Noir", "High-contrast black & white film noir",
            PresetCategory.Film, Icons.Default.ColorLens, 0,
            "colorchannelmixer=.3:.4:.3:0:.3:.4:.3:0:.3:.4:.3,eq=contrast=1.6"),
        Preset("film_grain", "Film Grain", "16mm film look with organic grain",
            PresetCategory.Film, Icons.Default.Photo, 0,
            "noise=alls=6:allf=t+u,eq=contrast=1.1:saturation=0.85"),
        Preset("eight_mm", "8mm", "Old home movie look with vignette and grain",
            PresetCategory.Film, Icons.Default.Photo, 0,
            "vignette=PI/3:eval=frame,hue=s=0.5,noise=alls=10:allf=t+u,eq=contrast=1.2"),
        Preset("bleach_bypass", "Bleach Bypass", "Silver retention look — high contrast, desaturated",
            PresetCategory.Film, Icons.Default.Photo, 0,
            "eq=contrast=1.3:saturation=0.4"),
        Preset("cross_process", "Cross Process", "C-41 cross-processing color shift",
            PresetCategory.Film, Icons.Default.Photo, 0,
            "curves=r='0/0 0.5/0.6 1/1':g='0/0 0.5/0.4 1/1':b='0/0 0.5/0.7 1/1'"),

        // ─── Artistic (Tier 0) ──────────────────────────────────────────
        Preset("pencil_sketch", "Pencil Sketch", "Hand-drawn pencil outline effect",
            PresetCategory.Artistic, Icons.Default.Brush, 0,
            "edgedetect=low=0.1:high=0.3,colorchannelmixer=.3:.4:.3"),
        Preset("pixel_art", "Pixel Art", "Retro 8-bit pixelation effect",
            PresetCategory.Artistic, Icons.Default.GridOn, 0,
            "pixelize=width=16:height=16"),
        Preset("watercolor", "Watercolor", "Soft watercolor paint-like appearance",
            PresetCategory.Artistic, Icons.Default.WaterDrop, 0,
            "gblur=sigma=2,eq=contrast=1.2:saturation=0.7"),
        Preset("oil_paint", "Oil Paint", "Thick oil brush stroke effect",
            PresetCategory.Artistic, Icons.Default.Brush, 0,
            "gblur=sigma=3,eq=saturation=1.3:contrast=1.1"),
        Preset("cartoon", "Cartoon", "Bold outlines with flat simplified colors",
            PresetCategory.Artistic, Icons.Default.Brush, 0,
            "edgedetect=low=0.05:high=0.15,curves=m='0/0 0.2/0.2 1/1'"),
        Preset("posterize", "Posterize", "Reduced color levels for graphic look",
            PresetCategory.Artistic, Icons.Default.ColorLens, 0,
            "posterize=levels=6"),
        Preset("dreamy", "Dreamy", "Soft focus with warm glow",
            PresetCategory.Artistic, Icons.Default.Star, 0,
            "gblur=sigma=1.5,eq=brightness=0.08:contrast=0.85,colorbalance=rh=0.03"),

        // ─── Glitch & Distortion (Tier 0) ──────────────────────────────
        Preset("glitch", "Glitch", "Digital distortion with RGB offset",
            PresetCategory.Glitch, Icons.Default.Bolt, 0,
            "chromashift=crh=3:crv=2:cbh=-3:cbv=-2,hue=H=40*sin(2*PI*t/1.5)"),
        Preset("chromatic", "Chromatic", "RGB chromatic aberration effect",
            PresetCategory.Glitch, Icons.Default.Bolt, 0,
            "chromashift=crh=4:crv=2:cbh=-4:cbv=-2"),
        Preset("vhs", "VHS", "Old VHS tape degradation with noise and color bleed",
            PresetCategory.Glitch, Icons.Default.FlashOn, 0,
            "hue=s=0.3,curves=m='0/0 0.05/0.1 0.5/0.5 0.95/0.9 1/1',noise=alls=8:allf=t+u"),
        Preset("invert", "Invert", "Full color negative inversion",
            PresetCategory.Glitch, Icons.Default.FlashOn, 0,
            "negate"),
        Preset("scanlines", "Scanlines", "CRT monitor scan line overlay",
            PresetCategory.Glitch, Icons.Default.GridOn, 0,
            "drawbox=y=ih*mod(t*10,1):w=iw:h=2:c=white@0.15:t=fill"),
        Preset("datamosh", "Datamosh", "Compression artifact glitch effect",
            PresetCategory.Glitch, Icons.Default.Bolt, 0,
            "crop=iw-16:ih:8:0,overlay=8:0,crop=iw-16:ih:0:0,overlay=0:0,hue=H=30*sin(2*PI*t)"),

        // ─── Nature & Travel (Tier 0) ───────────────────────────────────
        Preset("nature_enhance", "Nature Boost", "Vibrant greens and blues for landscape",
            PresetCategory.Nature, Icons.Default.Nature, 0,
            "eq=contrast=1.1:saturation=1.3,colorbalance=gs=0.08:bs=0.05"),
        Preset("golden_hour", "Golden Hour", "Warm golden sunlight tones",
            PresetCategory.Nature, Icons.Default.Landscape, 0,
            "colorbalance=rh=0.1:yh=0.08,eq=saturation=1.15"),
        Preset("cool_tones", "Cool Tones", "Crisp cool blue tones",
            PresetCategory.Nature, Icons.Default.Landscape, 0,
            "colorbalance=rs=-0.05:bs=0.1,eq=contrast=1.05:saturation=0.9"),
        Preset("vibrant", "Vibrant", "Maximum pop with punchy colors",
            PresetCategory.Nature, Icons.Default.Nature, 0,
            "eq=contrast=1.15:saturation=1.5:brightness=0.03"),
        Preset("fade_spring", "Fade Spring", "Soft faded look with gentle warm tint",
            PresetCategory.Nature, Icons.Default.Nature, 0,
            "eq=contrast=0.9:brightness=0.06:saturation=0.7,colorbalance=rh=0.03"),

        // ─── Aesthetic (Tier 0) ─────────────────────────────────────────
        Preset("retro_wave", "Retro Wave", "Neon pink and cyan synthwave aesthetic",
            PresetCategory.Aesthetic, Icons.Default.Palette, 0,
            "eq=saturation=1.4:contrast=1.2,colorbalance=rh=0.1:bs=0.15"),
        Preset("soft_glam", "Soft Glam", "Smooth beauty look with soft skin",
            PresetCategory.Aesthetic, Icons.Default.Star, 0,
            "gblur=sigma=0.5,eq=contrast=0.9:brightness=0.05:saturation=0.85"),
        Preset("warm_glow", "Warm Glow", "Subtle warm misty glow",
            PresetCategory.Aesthetic, Icons.Default.Star, 0,
            "colorbalance=rh=0.05:yh=0.05,eq=brightness=0.05:contrast=0.9"),
        Preset("bw_highkey", "B&W High Key", "Bright black & white with soft shadows",
            PresetCategory.Aesthetic, Icons.Default.ColorLens, 0,
            "colorchannelmixer=.3:.4:.3:0:.3:.4:.3:0:.3:.4:.3,eq=contrast=0.85:brightness=0.1"),
        Preset("duotone", "Duotone", "Two-color gradient map effect",
            PresetCategory.Aesthetic, Icons.Default.ColorLens, 0,
            "curves=r='0/0.1 1/0.9':g='0/0.05 1/0.95':b='0/0.3 1/0.7'"),

        // ══════════════════════════════════════════════════════════════════
        //  PHASE 1 — Background Effects (SINet mask → effect on bg only)
        // ══════════════════════════════════════════════════════════════════
        // Each generates random colors/params at apply time for unique results.
        Preset("bg_replace", "Background Replace", "Replace background with random gradient",
            PresetCategory.Portrait, Icons.Default.Person, 1, "bg_replace"),
        Preset("ink_splash", "Ink Splash", "Ink splatter effect on background",
            PresetCategory.Portrait, Icons.Default.WaterDrop, 1, "ink_splash"),
        Preset("cyberpunk_grid", "Cyberpunk Grid", "Neon grid lines on background",
            PresetCategory.Portrait, Icons.Default.Person, 1, "cyberpunk_grid"),
        Preset("glitch_bg", "Glitch Background", "Digital glitch distortion on background only",
            PresetCategory.Portrait, Icons.Default.Bolt, 1, "glitch_bg"),
        Preset("pixelate_bg", "Pixelate Background", "Mosaic pixelation on background",
            PresetCategory.Portrait, Icons.Default.GridOn, 1, "pixelate_bg"),

        // ══════════════════════════════════════════════════════════════════
        //  PHASE 2 — Person Effects (SINet mask → effect on person only)
        // ══════════════════════════════════════════════════════════════════
        Preset("glow_silhouette", "Glow Silhouette", "Glowing edge around person silhouette",
            PresetCategory.Portrait, Icons.Default.Star, 1, "glow_silhouette"),
        Preset("neon_outline", "Neon Outline", "Vibrant neon outline tracing the person",
            PresetCategory.Portrait, Icons.Default.FlashOn, 1, "neon_outline"),
        Preset("van_gogh", "Van Gogh Portrait", "Impressionist brush strokes on person",
            PresetCategory.Portrait, Icons.Default.Brush, 1, "van_gogh"),

        // ══════════════════════════════════════════════════════════════════
        //  PHASE 3 — Combined Effects (different treatment per area)
        // ══════════════════════════════════════════════════════════════════
        Preset("dramatic", "Dramatic", "High contrast person + moody background",
            PresetCategory.Portrait, Icons.Default.ColorLens, 1, "dramatic"),
        Preset("color_pop", "Color Pop", "Vibrant person over black & white background",
            PresetCategory.Portrait, Icons.Default.Palette, 1, "color_pop"),
        Preset("dreamy", "Dreamy", "Soft glow on person with cool tones in background",
            PresetCategory.Portrait, Icons.Default.Star, 1, "dreamy"),
        Preset("golden_hour", "Golden Hour", "Warm golden person against cool background",
            PresetCategory.Portrait, Icons.Default.Landscape, 1, "golden_hour"),
        Preset("double_exposure", "Double Exposure", "Overlay pattern blended with person mask",
            PresetCategory.Portrait, Icons.Default.PhotoFilter, 1, "double_exposure"),

        // ─── Neural (Tier 2) ────────────────────────────────────────────
        Preset("neural_style", "Neural Style", "Deep learning style transfer",
            PresetCategory.Artistic, Icons.Default.Star, 2, "")
    )

    fun byId(id: String): Preset? = all.find { it.id == id }

    val tier0: List<Preset> = all.filter { it.tier == 0 }
    val tier1: List<Preset> = all.filter { it.tier == 1 }
    val tier2: List<Preset> = all.filter { it.tier == 2 }
}
