package app.ailiaili.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AliContrastTest {
    @Test
    fun themeAndPlaybackAccentsHaveReadableForegrounds() {
        assertTrue(contrastRatio(AliColors.StageInk, AliColors.Ink) >= 4.5f)
        assertTrue(contrastRatio(AliColors.StageMuted, AliColors.StagePanel) >= 4.5f)
        for (scheme in listOf(LightScheme, DarkScheme)) {
            assertTrue(contrastRatio(scheme.playbackAccent(), AliColors.StagePanel) >= 4.5f)
            assertTrue(contrastRatio(scheme.playbackAccent(), AliColors.Ink) >= 4.5f)
            assertTrue(contrastRatio(scheme.onBackground, scheme.background) >= 4.5f)
            assertTrue(contrastRatio(scheme.onSurfaceVariant, scheme.surfaceContainerLow) >= 4.5f)
            assertTrue(contrastRatio(scheme.onSurfaceVariant, scheme.surface) >= 4.5f)
            assertTrue(contrastRatio(scheme.onSurfaceVariant, scheme.surfaceContainerHighest) >= 4.5f)
            assertTrue(contrastRatio(scheme.primary, scheme.surfaceContainerHigh) >= 4.5f)
            assertTrue(contrastRatio(scheme.onPrimary, scheme.primary) >= 4.5f)
            assertTrue(contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
        }
    }

    @Test
    fun bothThemesUseRestrainedBlueAccentsOnNeutralSurfaces() {
        val light = LightScheme
        val dark = DarkScheme
        for (scheme in listOf(light, dark)) {
            assertTrue(scheme.primary.blue > scheme.primary.green && scheme.primary.green > scheme.primary.red)
            assertTrue(contrastRatio(scheme.primaryContainer, scheme.surface) in 1.1f..1.65f)
        }
        assertTrue(light.primary.blue - light.primary.red < .22f)
        assertEquals(Color(0xFFA8C7FA), dark.primary)
        assertTrue(light.background.luminance() < light.surface.luminance())
        val darkLevels = listOf(
            dark.background,
            dark.surfaceContainerLow,
            dark.fillFor(AliMaterial.Panel),
            dark.fillFor(AliMaterial.Inset),
            dark.fillFor(AliMaterial.Floating),
            dark.surfaceContainerHighest,
        )
        for (color in darkLevels + listOf(light.background, light.surface, AliColors.StageMedia, AliColors.StagePanel)) {
            assertTrue(color.blue >= color.green && color.green >= color.red)
            assertTrue(color.blue - color.red < .04f)
        }
        assertTrue(darkLevels.zipWithNext().all { (lower, higher) -> lower.luminance() < higher.luminance() })
        assertTrue(contrastRatio(dark.background, dark.surfaceContainerHighest) < 2f)
        assertTrue(light.playbackAccent().blue > light.playbackAccent().red)
        assertEquals(dark.primary, dark.playbackAccent())
    }

    @Test
    fun adjustsCustomAccentsThatBlendIntoTheSurface() {
        val lightSurface = LightScheme.surfaceContainerHigh
        val darkSurface = DarkScheme.surfaceContainerHigh
        val requested = listOf(Color.White, Color.Black, AliColors.LightAccent, AliColors.DarkAccent)
        for (accent in requested) {
            assertTrue(contrastRatio(readableAccent(accent, lightSurface, dark = false), lightSurface) >= 4.5f)
            assertTrue(contrastRatio(readableAccent(accent, darkSurface, dark = true), darkSurface) >= 4.5f)
        }
        assertEquals(AliColors.Ink, readableAccent(AliColors.Ink, lightSurface, dark = false))
    }

    @Test
    fun customSeedsKeepSurfacesNeutralAndControlsReadable() {
        val seeds = listOf(Color.White, Color.Black, Color(0xFF10B981), Color(0xFFAA8C62))
        for ((base, dark) in listOf(LightScheme to false, DarkScheme to true)) {
            for (seed in seeds) {
                val scheme = themedWithAccent(base, seed, dark)
                val floating = scheme.fillFor(AliMaterial.Floating)
                assertEquals(base.background, scheme.background)
                assertEquals(base.surface, scheme.surface)
                assertEquals(base.fillFor(AliMaterial.Inset), scheme.fillFor(AliMaterial.Inset))
                assertEquals(base.fillFor(AliMaterial.Floating), floating)
                assertTrue(scheme.primaryContainer != base.primaryContainer)
                assertTrue(
                    contrastRatio(scheme.onSurfaceVariant, floating) >= 4.5f,
                    "Supporting text on $floating with seed $seed and dark=$dark",
                )
                assertTrue(contrastRatio(scheme.primary, floating) >= 4.5f, "Accent on $floating with seed $seed and dark=$dark")
                assertTrue(contrastRatio(scheme.playbackAccent(), AliColors.StagePanel) >= 4.5f)
                assertTrue(contrastRatio(scheme.onPrimary, scheme.primary) >= 4.5f)
                assertTrue(contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
            }
        }
    }
}
