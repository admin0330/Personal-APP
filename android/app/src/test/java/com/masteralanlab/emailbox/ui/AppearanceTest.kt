package com.masteralanlab.emailbox.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.masteralanlab.emailbox.data.normalizeNavOrder
import com.masteralanlab.emailbox.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class AppearanceTest {
    @Test fun dockOrderSurvivesNormalizationAndLegacyMigration() {
        val chosen = listOf("notes", "ledger", "overview", "mail")
        assertEquals(chosen, normalizeNavOrder(chosen))
        assertEquals(listOf("notes", "mail", "overview", "ledger"), normalizeNavOrder(listOf("tokens", "unknown", "mail", "mail")))
    }

    @Test fun palettesKeepReadableTextAndShareMaterialColors() {
        val palettes = listOf(Ym1rClearLightColors, Ym1rClearDarkColors, Ym1rClassicLightColors, Ym1rClassicDarkColors)
        fun contrast(a: Color, b: Color): Float {
            val first = a.luminance(); val second = b.luminance()
            return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
        }
        for (colors in palettes) {
            val scheme = materialColors(colors)
            assertEquals(colors.background, scheme.background)
            assertEquals(colors.accent, scheme.primary)
            assertEquals(colors.surface, scheme.surface)
            for (surface in listOf(colors.background, colors.surface, colors.surfaceRaised)) {
                assertTrue("Primary text contrast", contrast(colors.textPrimary, surface) >= 4.5f)
                assertTrue("Secondary text contrast", contrast(colors.textSecondary, surface) >= 4.5f)
            }
            assertTrue("Action label contrast", contrast(scheme.primary, scheme.onPrimary) >= 4.5f)
            assertTrue("Error label contrast", contrast(scheme.error, scheme.onError) >= 4.5f)
        }
    }
}
